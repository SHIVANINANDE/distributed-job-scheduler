package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class JobService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobService.class);
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    @Lazy
    private JobPriorityQueueService priorityQueueService;
    
    @Autowired
    private RedisCacheService cacheService;
    
    // Create a new job
    public Job createJob(Job job) {
        logger.info("Creating new job: {}", job.getName());
        if (job.getStatus() == null) {
            job.setStatus(JobStatus.PENDING);
        }
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now());
        }
        
        Job savedJob = jobRepository.save(job);
        
        // Add job to Redis priority queue
        try {
            boolean added = priorityQueueService.addJobToQueue(savedJob);
            if (added) {
                logger.info("Job {} added to priority queue successfully", savedJob.getJobId());
            } else {
                logger.warn("Failed to add job {} to priority queue", savedJob.getJobId());
            }
        } catch (Exception e) {
            logger.warn("Error adding job {} to priority queue: {}", savedJob.getJobId(), e.getMessage());
        }
        
        // Cache the job
        try {
            cacheService.cacheJob(savedJob.getId().toString(), savedJob, 3600); // Cache for 1 hour
        } catch (Exception e) {
            logger.warn("Failed to cache job {}: {}", savedJob.getJobId(), e.getMessage());
        }
        
        logger.info("Created job: {}", savedJob.getJobId());
        return savedJob;
    }
    
    // Get all jobs with pagination
    public Page<Job> getAllJobs(Pageable pageable) {
        return jobRepository.findAll(pageable);
    }
    
    // Get job by ID
    public Optional<Job> getJobById(Long id) {
        String jobId = id.toString();
        
        // First try to get from cache
        Job cachedJob = cacheService.getCachedJob(jobId);
        if (cachedJob != null) {
            logger.debug("Retrieved job {} from cache", id);
            return Optional.of(cachedJob);
        }
        
        // If not in cache, get from database and cache it
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            cacheService.cacheJob(jobId, job, 60); // Cache for 1 hour
            logger.debug("Retrieved job {} from database and cached it", id);
        }
        
        return jobOpt;
    }
    
    // Get job by ID - direct return (for assignment service)
    public Job getJobByIdDirect(Long id) {
        Optional<Job> jobOpt = getJobById(id);
        return jobOpt.orElse(null);
    }
    
    // Update job
    public Job updateJob(Job job) {
        logger.info("Updating job: {}", job.getId());
        Job updatedJob = jobRepository.save(job);
        
        String jobId = updatedJob.getId().toString();
        
        // Update cache
        cacheService.evictJob(jobId);
        cacheService.cacheJob(jobId, updatedJob, 3600); // Cache for 1 hour
        
        // Update priority queue if priority has changed
        if (updatedJob.getStatus() == JobStatus.PENDING) {
            double newPriority = calculateJobPriority(updatedJob);
            priorityQueueService.updateJobPriority(Long.parseLong(jobId), String.valueOf(newPriority));
        }
        
        return updatedJob;
    }
    
    // Overloaded update job method (for backward compatibility)
    public Job updateJob(Long jobId, Job job) {
        job.setId(jobId);
        return updateJob(job);
    }
    
    // Update job status with queue management
    public Job updateJobStatus(Long jobId, JobStatus newStatus) {
        logger.info("Updating job {} status to {}", jobId, newStatus);
        
        Job job = getJobByIdDirect(jobId);
        if (job == null) {
            throw new RuntimeException("Job not found: " + jobId);
        }
        
        JobStatus oldStatus = job.getStatus();
        job.setStatus(newStatus);
        
        // Update timestamps based on status
        switch (newStatus) {
            case QUEUED:
                job.setQueuedAt(LocalDateTime.now());
                break;
            case RUNNING:
                job.setStartedAt(LocalDateTime.now());
                break;
            case COMPLETED:
                job.setCompletedAt(LocalDateTime.now());
                priorityQueueService.moveJobToCompleted(job);
                break;
            case FAILED:
                job.setCompletedAt(LocalDateTime.now());
                priorityQueueService.moveJobToFailed(job);
                break;
        }
        
        Job updatedJob = jobRepository.save(job);
        
        // Update cache
        try {
            cacheService.evictJobFromCache(jobId.toString());
            cacheService.cacheJob(jobId.toString(), updatedJob, 3600);
        } catch (Exception e) {
            logger.warn("Failed to update cache for job {}: {}", jobId, e.getMessage());
        }
        
        logger.info("Updated job {} status from {} to {}", jobId, oldStatus, newStatus);
        return updatedJob;
    }
    
    // Delete job
    public void deleteJob(Long id) {
        logger.info("Deleting job: {}", id);
        
        // Remove from priority queue
        try {
            priorityQueueService.removeJobFromQueue(id);
        } catch (Exception e) {
            logger.warn("Failed to remove job {} from queue: {}", id, e.getMessage());
        }
        
        // Remove from cache
        try {
            cacheService.evictJobFromCache(id.toString());
        } catch (Exception e) {
            logger.warn("Failed to evict job {} from cache: {}", id, e.getMessage());
        }
        
        // Delete from database
        jobRepository.deleteById(id);
        logger.info("Deleted job: {}", id);
    }
    
    // Get jobs by status
    public List<Job> getJobsByStatus(JobStatus status) {
        return jobRepository.findByStatus(status);
    }
    
    // Start a job
    public Job startJob(Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            String jobId = job.getId().toString();
            
            // Try to acquire lock for job processing
            if (!priorityQueueService.acquireJobLock(jobId, 300)) { // 5 minutes lock
                throw new IllegalStateException("Job is currently being processed by another worker");
            }
            
            try {
                if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.SCHEDULED) {
                    job.setStatus(JobStatus.RUNNING);
                    job.setStartedAt(LocalDateTime.now());
                    
                    Job updatedJob = jobRepository.save(job);
                    
                    // Update cache and remove from priority queue
                    cacheService.cacheJob(jobId, updatedJob, 60);
                    cacheService.cacheJobStatus(jobId, JobStatus.RUNNING);
                    priorityQueueService.removeJobFromQueue(Long.parseLong(jobId));
                    
                    // Record execution metrics
                    cacheService.incrementJobExecutionCount(jobId);
                    
                    logger.info("Started job: {}", job.getId());
                    return updatedJob;
                } else {
                    priorityQueueService.releaseJobLock(jobId);
                    throw new IllegalStateException("Job cannot be started from status: " + job.getStatus());
                }
            } catch (Exception e) {
                priorityQueueService.releaseJobLock(jobId);
                throw e;
            }
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Complete a job
    public Job completeJob(Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            String jobId = job.getId().toString();
            
            if (job.getStatus() == JobStatus.RUNNING) {
                LocalDateTime startTime = job.getStartedAt();
                LocalDateTime endTime = LocalDateTime.now();
                
                job.setStatus(JobStatus.COMPLETED);
                job.setCompletedAt(endTime);
                
                Job updatedJob = jobRepository.save(job);
                
                // Update cache and metrics
                cacheService.cacheJob(jobId, updatedJob, 60);
                cacheService.cacheJobStatus(jobId, JobStatus.COMPLETED);
                
                // Record execution time if start time is available
                if (startTime != null) {
                    long executionTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
                    cacheService.recordJobExecutionTime(jobId, executionTimeMs);
                }
                
                // Release job lock
                priorityQueueService.releaseJobLock(jobId);
                
                logger.info("Completed job: {}", job.getId());
                return updatedJob;
            } else {
                throw new IllegalStateException("Job cannot be completed from status: " + job.getStatus());
            }
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Fail a job
    public Job failJob(Long id, String errorMessage) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            String jobId = job.getId().toString();
            
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(LocalDateTime.now());
            job.setRetryCount(job.getRetryCount() + 1);
            
            Job updatedJob = jobRepository.save(job);
            
            // Update cache
            cacheService.cacheJob(jobId, updatedJob, 60);
            cacheService.cacheJobStatus(jobId, JobStatus.FAILED);
            
            // Release job lock
            priorityQueueService.releaseJobLock(jobId);
            
            // If job can be retried, add back to priority queue with lower priority
            if (job.getRetryCount() < job.getMaxRetries()) {
                double retryPriority = calculateJobPriority(updatedJob) - (job.getRetryCount() * 10);
                priorityQueueService.addJobToPriorityQueue(jobId, retryPriority);
                logger.info("Job {} failed but will be retried. Retry count: {}/{}", 
                    job.getId(), job.getRetryCount(), job.getMaxRetries());
            }
            
            logger.error("Failed job: {} - {}", job.getId(), errorMessage);
            return updatedJob;
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Cancel a job
    public Job cancelJob(Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            String jobId = job.getId().toString();
            
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.SCHEDULED) {
                job.setStatus(JobStatus.CANCELLED);
                job.setCompletedAt(LocalDateTime.now());
                
                Job updatedJob = jobRepository.save(job);
                
                // Update cache and remove from priority queue
                cacheService.cacheJob(jobId, updatedJob, 60);
                cacheService.cacheJobStatus(jobId, JobStatus.CANCELLED);
                priorityQueueService.removeJobFromQueue(Long.parseLong(jobId));
                
                // Release job lock if it was acquired
                priorityQueueService.releaseJobLock(jobId);
                
                logger.info("Cancelled job: {}", job.getId());
                return updatedJob;
            } else {
                throw new IllegalStateException("Job cannot be cancelled from status: " + job.getStatus());
            }
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Get job statistics
    public JobStatistics getJobStatistics() {
        long totalJobs = jobRepository.count();
        long pendingJobs = jobRepository.countByStatus(JobStatus.PENDING);
        long runningJobs = jobRepository.countByStatus(JobStatus.RUNNING);
        long completedJobs = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);
        long cancelledJobs = jobRepository.countByStatus(JobStatus.CANCELLED);
        long scheduledJobs = jobRepository.countByStatus(JobStatus.SCHEDULED);
        
        return new JobStatistics(totalJobs, pendingJobs, runningJobs, completedJobs, failedJobs, cancelledJobs, scheduledJobs);
    }
    
    // Get retryable jobs
    public List<Job> getRetryableJobs() {
        return jobRepository.findRetryableJobs();
    }
    
    // Get recent jobs (last 24 hours)
    public List<Job> getRecentJobs() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return jobRepository.findRecentJobs(since);
    }
    
    // Search jobs by name
    public List<Job> searchJobsByName(String name) {
        return jobRepository.findByNameContainingIgnoreCase(name);
    }
    
        // Get next job from priority queue
    public Job getNextJobFromQueue() {
        Job job = priorityQueueService.pollHighestPriorityJob();
        if (job != null) {
            try {
                // Job is already available directly from priority queue
                if (job.getStatus() == JobStatus.PENDING) {
                    job.setStatus(JobStatus.RUNNING);
                    job.setStartedAt(LocalDateTime.now());
                    jobRepository.save(job);
                    
                    // Cache the updated job
                    cacheService.cacheJob(job.getId().toString(), job, 3600);
                    return job;
                }
            } catch (Exception e) {
                logger.error("Error getting next job from queue: ", e);
            }
        }
        return null;
    }
    
    // Get queue size
    public long getQueueSize() {
        return priorityQueueService.getQueueSize();
    }
    
    // Get active workers count
    public int getActiveWorkersCount() {
        return cacheService.getActiveWorkers(5).size(); // 5 minutes timeout
    }
    
    // Check Redis availability
    public boolean isRedisAvailable() {
        return cacheService.isRedisAvailable();
    }
    
    // Calculate job priority based on various factors
    private double calculateJobPriority(Job job) {
        double basePriority = 100.0;
        
        // Higher priority for older jobs (prevent starvation)
        if (job.getCreatedAt() != null) {
            long hoursOld = java.time.Duration.between(job.getCreatedAt(), LocalDateTime.now()).toHours();
            basePriority += hoursOld; // Add 1 point per hour
        }
        
        // Adjust priority based on retry count (lower priority for retries)
        if (job.getRetryCount() != null && job.getRetryCount() > 0) {
            basePriority -= (job.getRetryCount() * 20);
        }
        
        // Adjust priority based on job type if specified
        if (job.getJobType() != null) {
            switch (job.getJobType().toLowerCase()) {
                case "critical":
                case "high":
                    basePriority += 50;
                    break;
                case "low":
                    basePriority -= 30;
                    break;
                default:
                    // Normal priority, no adjustment
                    break;
            }
        }
        
        // Ensure priority is never negative
        return Math.max(basePriority, 1.0);
    }
    
    // Inner class for job statistics
    public static class JobStatistics {
        private final long totalJobs;
        private final long pendingJobs;
        private final long runningJobs;
        private final long completedJobs;
        private final long failedJobs;
        private final long cancelledJobs;
        private final long scheduledJobs;
        
        public JobStatistics(long totalJobs, long pendingJobs, long runningJobs, 
                           long completedJobs, long failedJobs, long cancelledJobs, long scheduledJobs) {
            this.totalJobs = totalJobs;
            this.pendingJobs = pendingJobs;
            this.runningJobs = runningJobs;
            this.completedJobs = completedJobs;
            this.failedJobs = failedJobs;
            this.cancelledJobs = cancelledJobs;
            this.scheduledJobs = scheduledJobs;
        }
        
        // Getters
        public long getTotalJobs() { return totalJobs; }
        public long getPendingJobs() { return pendingJobs; }
        public long getRunningJobs() { return runningJobs; }
        public long getCompletedJobs() { return completedJobs; }
        public long getFailedJobs() { return failedJobs; }
        public long getCancelledJobs() { return cancelledJobs; }
        public long getScheduledJobs() { return scheduledJobs; }
    }
    
    // Get scheduled jobs due for execution
    public List<Job> getScheduledJobsDueForExecution() {
        LocalDateTime now = LocalDateTime.now();
        return jobRepository.findByStatusAndScheduledAtLessThanEqual(JobStatus.SCHEDULED, now);
    }
    
    // Get jobs by worker
    public List<Job> getJobsByWorker(String workerId) {
        return jobRepository.findByAssignedWorkerId(workerId);
    }
    
    // Update job result
    public Job updateJobResult(Long jobId, String result) {
        Optional<Job> optionalJob = jobRepository.findById(jobId);
        if (optionalJob.isPresent()) {
            Job job = optionalJob.get();
            job.setResult(result);
            job.setCompletedAt(LocalDateTime.now());
            return jobRepository.save(job);
        }
        throw new RuntimeException("Job not found: " + jobId);
    }
}
