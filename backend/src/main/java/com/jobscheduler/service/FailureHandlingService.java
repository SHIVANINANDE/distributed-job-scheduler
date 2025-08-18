package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker;
import com.jobscheduler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Optional;

/**
 * Service for handling job and worker failures with comprehensive retry mechanisms
 * and exponential backoff strategies.
 */
@Service
@Transactional
public class FailureHandlingService {
    
    private static final Logger logger = LoggerFactory.getLogger(FailureHandlingService.class);
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private WorkerService workerService;
    
    @Autowired
    private JobAssignmentService jobAssignmentService;
    
    @Autowired
    private DeadLetterQueueService deadLetterQueueService;
    
    @Autowired
    private JobExecutionHistoryService executionHistoryService;
    
    @Autowired
    private RedisCacheService cacheService;
    
    @Value("${job.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${job.retry.base-delay-seconds:5}")
    private int baseDelaySeconds;
    
    @Value("${job.retry.max-delay-seconds:300}")
    private int maxDelaySeconds;
    
    @Value("${job.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;
    
    @Value("${worker.heartbeat.timeout-minutes:5}")
    private int workerHeartbeatTimeoutMinutes;
    
    /**
     * Handle job failure with exponential backoff retry mechanism
     */
    public void handleJobFailure(Long jobId, String errorMessage, Exception exception) {
        logger.warn("Handling job failure for job ID: {}, Error: {}", jobId, errorMessage);
        
        try {
            Optional<Job> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                logger.error("Job not found with ID: {}", jobId);
                return;
            }
            
            Job job = jobOpt.get();
            
            // Update job status and error information
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setUpdatedAt(LocalDateTime.now());
            
            // Record execution history
            executionHistoryService.recordJobFailure(job, errorMessage, exception);
            
            // Check if job can be retried
            if (canJobBeRetried(job)) {
                scheduleJobRetry(job);
            } else {
                // Send to dead letter queue
                moveJobToDeadLetterQueue(job, "Maximum retry attempts exceeded");
                executionHistoryService.recordJobMovedToDeadLetter(job, 
                    "Maximum retry attempts exceeded");
            }
            
            jobRepository.save(job);
            
            // Clear cache entries related to this job
            clearJobCacheEntries(jobId);
            
            logger.info("Job failure handled for job ID: {}, Retry count: {}", 
                       jobId, job.getRetryCount());
            
        } catch (Exception e) {
            logger.error("Error handling job failure for job ID: {}", jobId, e);
        }
    }
    
    /**
     * Check if a job can be retried based on retry count and configuration
     */
    private boolean canJobBeRetried(Job job) {
        int currentRetryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int maxRetries = job.getMaxRetries() != null ? job.getMaxRetries() : maxRetryAttempts;
        
        return currentRetryCount < maxRetries;
    }
    
    /**
     * Schedule job retry with exponential backoff
     */
    private void scheduleJobRetry(Job job) {
        int currentRetryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int newRetryCount = currentRetryCount + 1;
        
        // Calculate delay with exponential backoff
        long delaySeconds = calculateExponentialBackoffDelay(newRetryCount);
        LocalDateTime retryTime = LocalDateTime.now().plusSeconds(delaySeconds);
        
        // Update job for retry
        job.setRetryCount(newRetryCount);
        job.setStatus(JobStatus.PENDING);
        job.setScheduledAt(retryTime);
        job.setAssignedWorkerId(null);
        job.setAssignedWorkerName(null);
        job.setWorkerHost(null);
        job.setWorkerPort(null);
        job.setWorkerAssignedAt(null);
        job.setStartedAt(null);
        
        // Record retry in execution history
        executionHistoryService.recordJobRetry(job, newRetryCount, delaySeconds);
        job.setUpdatedAt(LocalDateTime.now());
        
        logger.info("Scheduled job retry for job ID: {}, Retry count: {}, Delay: {} seconds", 
                   job.getId(), newRetryCount, delaySeconds);
        
        // Cache retry information
        cacheService.put("job:retry:" + job.getId(), 
                        String.valueOf(retryTime.toString()), 
                        (int) delaySeconds + 60); // Cache slightly longer than delay
    }
    
    /**
     * Calculate exponential backoff delay in seconds
     */
    private long calculateExponentialBackoffDelay(int retryCount) {
        double delay = baseDelaySeconds * Math.pow(backoffMultiplier, retryCount - 1);
        
        // Add jitter to prevent thundering herd
        double jitter = Math.random() * 0.3; // 30% jitter
        delay = delay * (1 + jitter);
        
        // Cap at maximum delay
        return Math.min((long) delay, maxDelaySeconds);
    }
    
    /**
     * Move job to dead letter queue when all retries are exhausted
     */
    private void moveJobToDeadLetterQueue(Job job, String reason) {
        logger.warn("Moving job to dead letter queue: Job ID: {}, Reason: {}", 
                   job.getId(), reason);
        
        job.setStatus(JobStatus.FAILED);
        deadLetterQueueService.addToDeadLetterQueue(job, reason);
    }
    
    /**
     * Detect and handle worker failures based on missed heartbeats
     */
    @Transactional
    public void detectAndHandleWorkerFailures() {
        logger.debug("Checking for worker failures...");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now()
                    .minus(workerHeartbeatTimeoutMinutes, ChronoUnit.MINUTES);
            
            List<Worker> failedWorkers = workerService.findWorkersWithMissedHeartbeats(cutoffTime);
            
            for (Worker worker : failedWorkers) {
                handleWorkerFailure(worker);
            }
            
            if (!failedWorkers.isEmpty()) {
                logger.info("Processed {} failed workers", failedWorkers.size());
            }
            
        } catch (Exception e) {
            logger.error("Error detecting worker failures", e);
        }
    }
    
    /**
     * Handle individual worker failure
     */
    private void handleWorkerFailure(Worker worker) {
        logger.warn("Handling worker failure: Worker ID: {}, Name: {}", 
                   worker.getWorkerId(), worker.getName());
        
        try {
            // Mark worker as failed
            workerService.markWorkerAsFailed(worker.getWorkerId(), 
                                           "Missed heartbeat timeout");
            
            // Find and reassign jobs from failed worker
            List<Job> assignedJobs = jobRepository.findByAssignedWorkerIdAndStatusIn(
                    worker.getWorkerId(), 
                    List.of(JobStatus.RUNNING, JobStatus.QUEUED));
            
            for (Job job : assignedJobs) {
                reassignJobFromFailedWorker(job, worker);
            }
            
            // Record worker failure in execution history
            executionHistoryService.recordWorkerFailure(worker, 
                                                       "Heartbeat timeout", 
                                                       assignedJobs.size());
            
            // Clear worker cache entries
            clearWorkerCacheEntries(worker.getWorkerId());
            
            logger.info("Worker failure handled: Worker ID: {}, Reassigned {} jobs", 
                       worker.getWorkerId(), assignedJobs.size());
            
        } catch (Exception e) {
            logger.error("Error handling worker failure for worker ID: {}", 
                        worker.getWorkerId(), e);
        }
    }
    
    /**
     * Reassign job from failed worker to another available worker
     */
    private void reassignJobFromFailedWorker(Job job, Worker failedWorker) {
        logger.info("Reassigning job {} from failed worker {}", 
                   job.getId(), failedWorker.getWorkerId());
        
        try {
            // Reset job assignment
            job.setAssignedWorkerId(null);
            job.setAssignedWorkerName(null);
            job.setWorkerHost(null);
            job.setWorkerPort(null);
            job.setWorkerAssignedAt(null);
            job.setStatus(JobStatus.PENDING);
            job.setUpdatedAt(LocalDateTime.now());
            
            // Record reassignment in execution history
            executionHistoryService.recordJobReassignment(job, failedWorker, 
                                                        "Worker failure");
            
            // Try to assign to new worker
            boolean assigned = jobAssignmentService.assignJobToWorker(job);
            
            if (!assigned) {
                logger.warn("Could not immediately reassign job {} - will retry later", 
                           job.getId());
            }
            
            jobRepository.save(job);
            
        } catch (Exception e) {
            logger.error("Error reassigning job {} from failed worker {}", 
                        job.getId(), failedWorker.getWorkerId(), e);
        }
    }
    
    /**
     * Handle job timeout scenarios
     */
    public void handleJobTimeout(Long jobId) {
        logger.warn("Handling job timeout for job ID: {}", jobId);
        
        try {
            Optional<Job> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return;
            }
            
            Job job = jobOpt.get();
            
            // Record timeout in execution history
            executionHistoryService.recordJobTimeout(job);
            
            // Handle as job failure with timeout reason
            handleJobFailure(jobId, "Job execution timeout", 
                           new RuntimeException("Job execution timeout"));
            
        } catch (Exception e) {
            logger.error("Error handling job timeout for job ID: {}", jobId, e);
        }
    }
    
    /**
     * Handle jobs stuck in RUNNING state
     */
    public void handleStuckJobs() {
        logger.debug("Checking for stuck jobs...");
        
        try {
            // Find jobs running for too long
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(2); // 2 hours timeout
            
            List<Job> stuckJobs = jobRepository.findByStatusAndStartedAtBefore(
                    JobStatus.RUNNING, cutoffTime);
            
            for (Job job : stuckJobs) {
                logger.warn("Found stuck job: ID: {}, Started at: {}", 
                           job.getId(), job.getStartedAt());
                
                handleJobTimeout(job.getId());
            }
            
            if (!stuckJobs.isEmpty()) {
                logger.info("Recovered {} stuck jobs", stuckJobs.size());
            }
            
        } catch (Exception e) {
            logger.error("Error recovering stuck jobs", e);
        }
    }
    
    /**
     * Clear job-related cache entries
     */
    private void clearJobCacheEntries(Long jobId) {
        try {
            cacheService.delete("job:" + jobId);
            cacheService.delete("job:assignment:" + jobId);
            cacheService.delete("job:retry:" + jobId);
        } catch (Exception e) {
            logger.warn("Error clearing cache entries for job {}", jobId, e);
        }
    }
    
    /**
     * Clear worker-related cache entries
     */
    private void clearWorkerCacheEntries(String workerId) {
        try {
            cacheService.delete("worker:" + workerId);
            cacheService.delete("worker:performance:" + workerId);
            cacheService.delete("worker:load:" + workerId);
        } catch (Exception e) {
            logger.warn("Error clearing cache entries for worker {}", workerId, e);
        }
    }
    
    /**
     * Get retry statistics for monitoring
     */
    public Map<String, Object> getRetryStatistics() {
        try {
            long totalRetries = jobRepository.countByRetryCountGreaterThan(0);
            long deadLetterJobs = deadLetterQueueService.getDeadLetterQueueSize();
            long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRetries", totalRetries);
            stats.put("deadLetterJobs", deadLetterJobs);
            stats.put("failedJobs", failedJobs);
            stats.put("timestamp", LocalDateTime.now());
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Error getting retry statistics", e);
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRetries", 0L);
            stats.put("deadLetterJobs", 0L);
            stats.put("failedJobs", 0L);
            stats.put("error", e.getMessage());
            return stats;
        }
    }
    
    /**
     * Retry statistics data class
     */
    public static class RetryStatistics {
        private final long totalRetries;
        private final long deadLetterJobs;
        private final long failedJobs;
        
        public RetryStatistics(long totalRetries, long deadLetterJobs, long failedJobs) {
            this.totalRetries = totalRetries;
            this.deadLetterJobs = deadLetterJobs;
            this.failedJobs = failedJobs;
        }
        
        public long getTotalRetries() { return totalRetries; }
        public long getDeadLetterJobs() { return deadLetterJobs; }
        public long getFailedJobs() { return failedJobs; }
    }
}
