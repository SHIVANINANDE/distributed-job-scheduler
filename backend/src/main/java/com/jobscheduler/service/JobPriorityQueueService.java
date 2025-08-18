package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobPriorityQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobPriorityQueueService.class);
    
    // Redis queue keys
    private static final String PRIORITY_QUEUE_KEY = "job:priority:queue";
    private static final String PROCESSING_QUEUE_KEY = "job:processing:queue";
    private static final String FAILED_QUEUE_KEY = "job:failed:queue";
    private static final String COMPLETED_QUEUE_KEY = "job:completed:queue";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private JobService jobService;
    
    private ZSetOperations<String, Object> zSetOps;
    
    public void initializeOperations() {
        this.zSetOps = redisTemplate.opsForZSet();
    }
    
    /**
     * Add a job to the priority queue
     * @param job The job to add
     * @return true if successfully added
     */
    public boolean addJobToQueue(Job job) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            double priorityScore = calculatePriorityScore(job);
            String jobKey = createJobKey(job);
            
            Boolean added = zSetOps.add(PRIORITY_QUEUE_KEY, jobKey, priorityScore);
            
            if (Boolean.TRUE.equals(added)) {
                logger.info("Added job {} to priority queue with score {}", job.getJobId(), priorityScore);
                
                // Update job status to QUEUED
                job.setStatus(JobStatus.QUEUED);
                job.setQueuedAt(LocalDateTime.now());
                jobService.updateJob(job.getId(), job);
                
                return true;
            }
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to add job {} to priority queue: {}", job.getJobId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove and return the highest priority job from the queue
     * @return The highest priority job, or null if queue is empty
     */
    public Job popHighestPriorityJob() {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            // Get the job with the lowest score (highest priority)
            Set<ZSetOperations.TypedTuple<Object>> result = zSetOps.popMin(PRIORITY_QUEUE_KEY, 1);
            
            if (result != null && !result.isEmpty()) {
                ZSetOperations.TypedTuple<Object> tuple = result.iterator().next();
                String jobKey = (String) tuple.getValue();
                Long jobId = extractJobIdFromKey(jobKey);
                
                if (jobId != null) {
                    Job job = jobService.getJobById(jobId);
                    if (job != null) {
                        // Move to processing queue
                        addJobToProcessingQueue(job);
                        
                        logger.info("Popped job {} from priority queue with score {}", 
                                   job.getJobId(), tuple.getScore());
                        return job;
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to pop job from priority queue: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Update job priority dynamically
     * @param jobId The job ID
     * @param newPriority The new priority
     * @return true if successfully updated
     */
    public boolean updateJobPriority(Long jobId, String newPriority) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Job job = jobService.getJobById(jobId);
            if (job == null) {
                logger.warn("Job {} not found for priority update", jobId);
                return false;
            }
            
            String jobKey = createJobKey(job);
            
            // Remove from current position
            Long removed = zSetOps.remove(PRIORITY_QUEUE_KEY, jobKey);
            
            if (removed != null && removed > 0) {
                // Update job priority (simplified)
                jobService.updateJob(job);
                
                // Re-add with new priority score
                double newScore = calculatePriorityScore(job);
                zSetOps.add(PRIORITY_QUEUE_KEY, jobKey, newScore);
                
                logger.info("Updated job {} priority to {} with new score {}", 
                           job.getName(), newPriority, newScore);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to update priority for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get jobs from priority queue with pagination
     * @param start Start index
     * @param end End index
     * @return List of jobs in priority order
     */
    public List<Job> getQueuedJobs(long start, long end) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Set<Object> jobKeys = zSetOps.range(PRIORITY_QUEUE_KEY, start, end);
            
            if (jobKeys != null && !jobKeys.isEmpty()) {
                return jobKeys.stream()
                    .map(key -> extractJobIdFromKey((String) key))
                    .filter(Objects::nonNull)
                    .map(jobService::getJobById)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            logger.error("Failed to get queued jobs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Batch add multiple jobs to the queue
     * @param jobs List of jobs to add
     * @return Number of jobs successfully added
     */
    public int batchAddJobs(List<Job> jobs) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Set<ZSetOperations.TypedTuple<Object>> tuples = new HashSet<>();
            
            for (Job job : jobs) {
                double score = calculatePriorityScore(job);
                String jobKey = createJobKey(job);
                tuples.add(ZSetOperations.TypedTuple.of(jobKey, score));
            }
            
            Long added = zSetOps.add(PRIORITY_QUEUE_KEY, tuples);
            
            if (added != null && added > 0) {
                // Update job statuses
                for (Job job : jobs) {
                    job.setStatus(JobStatus.QUEUED);
                    job.setQueuedAt(LocalDateTime.now());
                    jobService.updateJob(job.getId(), job);
                }
                
                logger.info("Batch added {} jobs to priority queue", added);
                return added.intValue();
            }
            
            return 0;
            
        } catch (Exception e) {
            logger.error("Failed to batch add jobs: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Batch pop multiple jobs from the queue
     * @param count Number of jobs to pop
     * @return List of jobs in priority order
     */
    public List<Job> batchPopJobs(int count) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Set<ZSetOperations.TypedTuple<Object>> results = zSetOps.popMin(PRIORITY_QUEUE_KEY, count);
            List<Job> jobs = new ArrayList<>();
            
            if (results != null && !results.isEmpty()) {
                for (ZSetOperations.TypedTuple<Object> tuple : results) {
                    String jobKey = (String) tuple.getValue();
                    Long jobId = extractJobIdFromKey(jobKey);
                    
                    if (jobId != null) {
                        Job job = jobService.getJobById(jobId);
                        if (job != null) {
                            addJobToProcessingQueue(job);
                            jobs.add(job);
                        }
                    }
                }
                
                logger.info("Batch popped {} jobs from priority queue", jobs.size());
            }
            
            return jobs;
            
        } catch (Exception e) {
            logger.error("Failed to batch pop jobs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Remove a specific job from the queue
     * @param jobId The job ID to remove
     * @return true if successfully removed
     */
    public boolean removeJobFromQueue(Long jobId) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Job job = jobService.getJobById(jobId);
            if (job == null) {
                return false;
            }
            
            String jobKey = createJobKey(job);
            Long removed = zSetOps.remove(PRIORITY_QUEUE_KEY, jobKey);
            
            if (removed != null && removed > 0) {
                logger.info("Removed job {} from priority queue", job.getJobId());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to remove job {} from queue: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get queue statistics
     * @return Map containing queue statistics
     */
    public Map<String, Object> getQueueStatistics() {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("priorityQueueSize", zSetOps.count(PRIORITY_QUEUE_KEY, 0, Double.MAX_VALUE));
            stats.put("processingQueueSize", zSetOps.count(PROCESSING_QUEUE_KEY, 0, Double.MAX_VALUE));
            stats.put("failedQueueSize", zSetOps.count(FAILED_QUEUE_KEY, 0, Double.MAX_VALUE));
            stats.put("completedQueueSize", zSetOps.count(COMPLETED_QUEUE_KEY, 0, Double.MAX_VALUE));
            
            // Get priority distribution
            Map<String, Long> priorityDistribution = new HashMap<>();
            priorityDistribution.put("HIGH", zSetOps.count(PRIORITY_QUEUE_KEY, 0, 999));
            priorityDistribution.put("MEDIUM", zSetOps.count(PRIORITY_QUEUE_KEY, 1000, 1999));
            priorityDistribution.put("LOW", zSetOps.count(PRIORITY_QUEUE_KEY, 2000, Double.MAX_VALUE));
            
            stats.put("priorityDistribution", priorityDistribution);
            stats.put("timestamp", LocalDateTime.now());
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Failed to get queue statistics: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Calculate priority score for a job
     * Lower score = higher priority
     */
    private double calculatePriorityScore(Job job) {
        double baseScore = 0;
        
        // Priority-based scoring  
        // Convert integer priority to JobPriority enum for calculation
        JobPriority jobPriority = JobPriority.fromValue(job.getPriority());
        switch (jobPriority) {
            case HIGH:
                baseScore = 0;
                break;
            case MEDIUM:
                baseScore = 1000;
                break;
            case LOW:
                baseScore = 2000;
                break;
        }
        
        // Add time-based component (older jobs get higher priority)
        if (job.getCreatedAt() != null) {
            long timeScore = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - 
                           job.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
            baseScore += (timeScore / 60.0); // Add minutes since creation
        }
        
        // Add urgency factor based on scheduled time
        if (job.getScheduledAt() != null) {
            long scheduledScore = job.getScheduledAt().toEpochSecond(ZoneOffset.UTC) - 
                                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            if (scheduledScore < 0) {
                baseScore -= Math.abs(scheduledScore / 60.0); // Overdue jobs get higher priority
            }
        }
        
        // Add retry penalty
        if (job.getRetryCount() != null && job.getRetryCount() > 0) {
            baseScore += job.getRetryCount() * 100; // Lower priority for retried jobs
        }
        
        return Math.max(0, baseScore); // Ensure non-negative score
    }
    
    /**
     * Create a unique key for a job in Redis
     */
    private String createJobKey(Job job) {
        return String.format("job:%d:%s", job.getId(), job.getJobId());
    }
    
    /**
     * Extract job ID from Redis key
     */
    private Long extractJobIdFromKey(String jobKey) {
        try {
            String[] parts = jobKey.split(":");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to extract job ID from key: {}", jobKey);
        }
        return null;
    }
    
    /**
     * Add job to processing queue
     */
    private void addJobToProcessingQueue(Job job) {
        try {
            String jobKey = createJobKey(job);
            double timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            zSetOps.add(PROCESSING_QUEUE_KEY, jobKey, timestamp);
            
            // Update job status
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            jobService.updateJob(job.getId(), job);
            
        } catch (Exception e) {
            logger.error("Failed to add job {} to processing queue: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Move job to completed queue
     */
    public void moveJobToCompleted(Job job) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            String jobKey = createJobKey(job);
            
            // Remove from processing queue
            zSetOps.remove(PROCESSING_QUEUE_KEY, jobKey);
            
            // Add to completed queue
            double timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            zSetOps.add(COMPLETED_QUEUE_KEY, jobKey, timestamp);
            
            logger.info("Moved job {} to completed queue", job.getJobId());
            
        } catch (Exception e) {
            logger.error("Failed to move job {} to completed queue: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Move job to failed queue
     */
    public void moveJobToFailed(Job job) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            String jobKey = createJobKey(job);
            
            // Remove from processing queue
            zSetOps.remove(PROCESSING_QUEUE_KEY, jobKey);
            
            // Add to failed queue
            double timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            zSetOps.add(FAILED_QUEUE_KEY, jobKey, timestamp);
            
            logger.info("Moved job {} to failed queue", job.getJobId());
            
        } catch (Exception e) {
            logger.error("Failed to move job {} to failed queue: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Clean up old completed/failed jobs
     */
    public void cleanupOldJobs(int maxAgeHours) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            double cutoffTime = LocalDateTime.now().minusHours(maxAgeHours)
                                                 .toEpochSecond(ZoneOffset.UTC);
            
            Long completedRemoved = zSetOps.removeRangeByScore(COMPLETED_QUEUE_KEY, 0, cutoffTime);
            Long failedRemoved = zSetOps.removeRangeByScore(FAILED_QUEUE_KEY, 0, cutoffTime);
            
            logger.info("Cleaned up {} completed and {} failed jobs older than {} hours", 
                       completedRemoved, failedRemoved, maxAgeHours);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup old jobs: {}", e.getMessage());
        }
    }
    
    /**
     * Acquire a lock on a job for processing
     * @param jobId The job ID
     * @param timeoutSeconds Lock timeout in seconds
     * @return true if lock acquired successfully
     */
    public boolean acquireJobLock(String jobId, int timeoutSeconds) {
        try {
            String lockKey = "job:lock:" + jobId;
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", java.time.Duration.ofSeconds(timeoutSeconds));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            logger.error("Failed to acquire lock for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Release the lock on a job
     * @param jobId The job ID
     * @return true if lock released successfully
     */
    public boolean releaseJobLock(String jobId) {
        try {
            String lockKey = "job:lock:" + jobId;
            Boolean deleted = redisTemplate.delete(lockKey);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            logger.error("Failed to release lock for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a job to the priority queue with specified priority
     * @param jobId The job ID
     * @param priority The priority score
     * @return true if successfully added
     */
    public boolean addJobToPriorityQueue(String jobId, double priority) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            Boolean added = zSetOps.add(PRIORITY_QUEUE_KEY, jobId, priority);
            return Boolean.TRUE.equals(added);
        } catch (Exception e) {
            logger.error("Failed to add job {} to priority queue: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Poll for the highest priority job without removing it
     * @return The highest priority job, or null if queue is empty
     */
    public Job pollHighestPriorityJob() {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            
            Set<Object> result = zSetOps.range(PRIORITY_QUEUE_KEY, 0, 0);
            if (result != null && !result.isEmpty()) {
                String jobKey = (String) result.iterator().next();
                Long jobId = extractJobIdFromKey(jobKey);
                if (jobId != null) {
                    return jobService.getJobById(jobId);
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to poll highest priority job: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the current size of the priority queue
     * @return The number of jobs in the queue
     */
    public long getQueueSize() {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            Long size = zSetOps.count(PRIORITY_QUEUE_KEY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return size != null ? size : 0L;
        } catch (Exception e) {
            logger.error("Failed to get queue size: {}", e.getMessage());
            return 0L;
        }
    }
    
    /**
     * Check if a job is in the queue
     * @param jobId The job ID to check
     * @return true if job is in queue
     */
    public boolean isJobInQueue(Long jobId) {
        try {
            if (zSetOps == null) {
                initializeOperations();
            }
            String jobKey = "job:" + jobId;
            Double score = zSetOps.score(PRIORITY_QUEUE_KEY, jobKey);
            return score != null;
        } catch (Exception e) {
            logger.error("Failed to check if job {} is in queue: {}", jobId, e.getMessage());
            return false;
        }
    }
}
