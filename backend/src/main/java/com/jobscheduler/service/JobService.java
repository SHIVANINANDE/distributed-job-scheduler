package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    // Create a new job
    public Job createJob(Job job) {
        logger.info("Creating new job: {}", job.getName());
        job.setStatus(JobStatus.PENDING);
        return jobRepository.save(job);
    }
    
    // Get all jobs with pagination
    public Page<Job> getAllJobs(Pageable pageable) {
        return jobRepository.findAll(pageable);
    }
    
    // Get job by ID
    public Optional<Job> getJobById(Long id) {
        return jobRepository.findById(id);
    }
    
    // Update job
    public Job updateJob(Job job) {
        logger.info("Updating job: {}", job.getId());
        return jobRepository.save(job);
    }
    
    // Delete job
    public void deleteJob(Long id) {
        logger.info("Deleting job: {}", id);
        jobRepository.deleteById(id);
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
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.SCHEDULED) {
                job.setStatus(JobStatus.RUNNING);
                job.setStartedAt(LocalDateTime.now());
                logger.info("Started job: {}", job.getId());
                return jobRepository.save(job);
            } else {
                throw new IllegalStateException("Job cannot be started from status: " + job.getStatus());
            }
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Complete a job
    public Job completeJob(Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            if (job.getStatus() == JobStatus.RUNNING) {
                job.setStatus(JobStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                logger.info("Completed job: {}", job.getId());
                return jobRepository.save(job);
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
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(LocalDateTime.now());
            job.setRetryCount(job.getRetryCount() + 1);
            logger.error("Failed job: {} - {}", job.getId(), errorMessage);
            return jobRepository.save(job);
        }
        throw new RuntimeException("Job not found: " + id);
    }
    
    // Cancel a job
    public Job cancelJob(Long id) {
        Optional<Job> jobOpt = jobRepository.findById(id);
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.SCHEDULED) {
                job.setStatus(JobStatus.CANCELLED);
                job.setCompletedAt(LocalDateTime.now());
                logger.info("Cancelled job: {}", job.getId());
                return jobRepository.save(job);
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
}
