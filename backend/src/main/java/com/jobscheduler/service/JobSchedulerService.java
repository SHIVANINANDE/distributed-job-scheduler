package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.Job.JobStatus;
import com.jobscheduler.model.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class JobSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobSchedulerService.class);
    
    @Autowired
    private JobPriorityQueueService queueService;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private WorkerService workerService;
    
    /**
     * Process jobs from the priority queue periodically
     */
    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void processJobQueue() {
        try {
            logger.debug("Processing job queue...");
            
            // Get available workers
            List<Worker> availableWorkers = workerService.getAvailableWorkers();
            
            if (availableWorkers.isEmpty()) {
                logger.debug("No available workers found");
                return;
            }
            
            // Process jobs for each available worker
            for (Worker worker : availableWorkers) {
                if (worker.hasAvailableCapacity()) {
                    Job job = queueService.popHighestPriorityJob();
                    if (job != null) {
                        assignJobToWorker(job, worker);
                    } else {
                        break; // No more jobs in queue
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing job queue: {}", e.getMessage());
        }
    }
    
    /**
     * Assign a job to a worker
     */
    @Async
    public CompletableFuture<Void> assignJobToWorker(Job job, Worker worker) {
        try {
            logger.info("Assigning job {} to worker {}", job.getJobId(), worker.getWorkerId());
            
            // Update job status
            job.setAssignedWorker(worker.getWorkerId());
            job.setStartedAt(LocalDateTime.now());
            jobService.updateJobStatus(job.getId(), JobStatus.RUNNING);
            
            // Update worker
            worker.addCurrentJob(job.getId());
            workerService.updateWorker(worker.getId(), worker);
            
            // Simulate job execution (in real implementation, this would be a call to the worker)
            executeJob(job, worker);
            
        } catch (Exception e) {
            logger.error("Error assigning job {} to worker {}: {}", 
                        job.getJobId(), worker.getWorkerId(), e.getMessage());
            
            // Move job back to queue or to failed queue
            handleJobExecutionFailure(job, worker, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Execute a job (simulation)
     */
    @Async
    public CompletableFuture<Void> executeJob(Job job, Worker worker) {
        try {
            logger.info("Executing job {} on worker {}", job.getJobId(), worker.getWorkerId());
            
            // Simulate job execution time
            Thread.sleep(job.getEstimatedDuration() != null ? 
                        job.getEstimatedDuration() * 1000 : 10000);
            
            // Simulate success/failure (90% success rate)
            boolean success = Math.random() > 0.1;
            
            if (success) {
                handleJobSuccess(job, worker);
            } else {
                handleJobFailure(job, worker, new RuntimeException("Simulated job failure"));
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleJobFailure(job, worker, e);
        } catch (Exception e) {
            handleJobFailure(job, worker, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Handle successful job completion
     */
    private void handleJobSuccess(Job job, Worker worker) {
        try {
            logger.info("Job {} completed successfully on worker {}", 
                       job.getJobId(), worker.getWorkerId());
            
            // Update job status
            job.setCompletedAt(LocalDateTime.now());
            job.setResult("Job completed successfully");
            jobService.updateJobStatus(job.getId(), JobStatus.COMPLETED);
            
            // Update worker
            worker.removeCurrentJob(job.getId());
            worker.incrementSuccessfulJobs();
            workerService.updateWorker(worker.getId(), worker);
            
            // Move to completed queue
            queueService.moveJobToCompleted(job);
            
        } catch (Exception e) {
            logger.error("Error handling job success for {}: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Handle job execution failure
     */
    private void handleJobFailure(Job job, Worker worker, Exception error) {
        try {
            logger.warn("Job {} failed on worker {}: {}", 
                       job.getJobId(), worker.getWorkerId(), error.getMessage());
            
            // Update job
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(error.getMessage());
            
            // Check if job should be retried
            if (job.getRetryCount() != null && job.getRetryCount() < job.getMaxRetries()) {
                // Increment retry count and add back to queue
                job.setRetryCount(job.getRetryCount() + 1);
                job.setStatus(JobStatus.PENDING);
                jobService.updateJob(job.getId(), job);
                
                // Add back to queue with lower priority
                queueService.addJobToQueue(job);
                
                logger.info("Job {} added back to queue for retry (attempt {}/{})", 
                           job.getJobId(), job.getRetryCount(), job.getMaxRetries());
            } else {
                // Mark as failed
                jobService.updateJobStatus(job.getId(), JobStatus.FAILED);
                queueService.moveJobToFailed(job);
                
                logger.info("Job {} marked as failed after {} attempts", 
                           job.getJobId(), job.getRetryCount());
            }
            
            // Update worker
            if (worker != null) {
                worker.removeCurrentJob(job.getId());
                worker.incrementFailedJobs();
                workerService.updateWorker(worker.getId(), worker);
            }
            
        } catch (Exception e) {
            logger.error("Error handling job failure for {}: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Handle job execution failure during assignment
     */
    private void handleJobExecutionFailure(Job job, Worker worker, Exception error) {
        logger.error("Failed to execute job {} on worker {}: {}", 
                    job.getJobId(), worker.getWorkerId(), error.getMessage());
        
        // Reset job status
        job.setStatus(JobStatus.PENDING);
        job.setAssignedWorker(null);
        job.setStartedAt(null);
        jobService.updateJob(job.getId(), job);
        
        // Add back to queue
        queueService.addJobToQueue(job);
        
        // Update worker
        worker.removeCurrentJob(job.getId());
        workerService.updateWorker(worker.getId(), worker);
    }
    
    /**
     * Process scheduled jobs that are due for execution
     */
    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    public void processScheduledJobs() {
        try {
            logger.debug("Processing scheduled jobs...");
            
            List<Job> scheduledJobs = jobService.getScheduledJobsDueForExecution();
            
            for (Job job : scheduledJobs) {
                logger.info("Adding scheduled job {} to priority queue", job.getJobId());
                queueService.addJobToQueue(job);
            }
            
        } catch (Exception e) {
            logger.error("Error processing scheduled jobs: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up old completed and failed jobs
     */
    @Scheduled(fixedDelay = 3600000) // Run every hour
    public void cleanupOldJobs() {
        try {
            logger.info("Cleaning up old jobs...");
            queueService.cleanupOldJobs(24); // Remove jobs older than 24 hours
        } catch (Exception e) {
            logger.error("Error cleaning up old jobs: {}", e.getMessage());
        }
    }
    
    /**
     * Monitor worker health and reassign jobs from unhealthy workers
     */
    @Scheduled(fixedDelay = 60000) // Run every minute
    public void monitorWorkerHealth() {
        try {
            logger.debug("Monitoring worker health...");
            
            List<Worker> unhealthyWorkers = workerService.getUnhealthyWorkers();
            
            for (Worker worker : unhealthyWorkers) {
                logger.warn("Worker {} is unhealthy, reassigning jobs", worker.getWorkerId());
                reassignJobsFromWorker(worker);
            }
            
        } catch (Exception e) {
            logger.error("Error monitoring worker health: {}", e.getMessage());
        }
    }
    
    /**
     * Reassign jobs from an unhealthy worker
     */
    private void reassignJobsFromWorker(Worker worker) {
        try {
            List<Long> currentJobs = worker.getCurrentJobIds();
            
            for (Long jobId : currentJobs) {
                Job job = jobService.getJobById(jobId);
                if (job != null && job.getStatus() == JobStatus.RUNNING) {
                    logger.info("Reassigning job {} from unhealthy worker {}", 
                               job.getJobId(), worker.getWorkerId());
                    
                    // Reset job status
                    job.setStatus(JobStatus.PENDING);
                    job.setAssignedWorker(null);
                    job.setStartedAt(null);
                    jobService.updateJob(job.getId(), job);
                    
                    // Add back to queue
                    queueService.addJobToQueue(job);
                }
            }
            
            // Clear worker's job list
            worker.clearCurrentJobs();
            workerService.updateWorker(worker.getId(), worker);
            
        } catch (Exception e) {
            logger.error("Error reassigning jobs from worker {}: {}", 
                        worker.getWorkerId(), e.getMessage());
        }
    }
    
    /**
     * Get queue statistics
     */
    public Object getQueueStatistics() {
        return queueService.getQueueStatistics();
    }
}
