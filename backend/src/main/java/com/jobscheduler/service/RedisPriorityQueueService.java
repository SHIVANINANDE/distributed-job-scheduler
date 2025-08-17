package com.jobscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisPriorityQueueService {

    private static final Logger logger = LoggerFactory.getLogger(RedisPriorityQueueService.class);
    
    private static final String PRIORITY_QUEUE_KEY = "job:priority:queue";
    private static final String JOB_LOCK_PREFIX = "job:lock:";
    
    @Autowired
    private RedisTemplate<String, String> priorityQueueTemplate;
    
    @Autowired
    private ZSetOperations<String, String> zSetOperations;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Add a job to the priority queue
     * Higher priority values mean higher priority (executed first)
     * 
     * @param jobId The job identifier
     * @param priority The job priority (higher number = higher priority)
     * @return true if job was added successfully
     */
    public boolean addJobToPriorityQueue(String jobId, double priority) {
        try {
            Boolean result = zSetOperations.add(PRIORITY_QUEUE_KEY, jobId, priority);
            logger.info("Added job {} to priority queue with priority {}", jobId, priority);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.error("Failed to add job {} to priority queue", jobId, e);
            return false;
        }
    }

    /**
     * Get the highest priority job from the queue (without removing it)
     * 
     * @return The job ID with highest priority, or null if queue is empty
     */
    public String peekHighestPriorityJob() {
        try {
            Set<String> jobs = zSetOperations.reverseRange(PRIORITY_QUEUE_KEY, 0, 0);
            if (jobs != null && !jobs.isEmpty()) {
                String jobId = jobs.iterator().next();
                logger.debug("Highest priority job: {}", jobId);
                return jobId;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to peek highest priority job", e);
            return null;
        }
    }

    /**
     * Remove and return the highest priority job from the queue
     * 
     * @return The job ID with highest priority, or null if queue is empty
     */
    public String pollHighestPriorityJob() {
        try {
            Set<ZSetOperations.TypedTuple<String>> jobs = zSetOperations.reverseRangeWithScores(PRIORITY_QUEUE_KEY, 0, 0);
            if (jobs != null && !jobs.isEmpty()) {
                ZSetOperations.TypedTuple<String> job = jobs.iterator().next();
                String jobId = job.getValue();
                
                // Remove the job from the queue
                Long removed = zSetOperations.remove(PRIORITY_QUEUE_KEY, jobId);
                if (removed != null && removed > 0) {
                    logger.info("Polled highest priority job: {} with priority {}", jobId, job.getScore());
                    return jobId;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to poll highest priority job", e);
            return null;
        }
    }

    /**
     * Remove a specific job from the priority queue
     * 
     * @param jobId The job identifier to remove
     * @return true if job was removed successfully
     */
    public boolean removeJobFromQueue(String jobId) {
        try {
            Long removed = zSetOperations.remove(PRIORITY_QUEUE_KEY, jobId);
            boolean success = removed != null && removed > 0;
            logger.info("Removed job {} from priority queue: {}", jobId, success);
            return success;
        } catch (Exception e) {
            logger.error("Failed to remove job {} from priority queue", jobId, e);
            return false;
        }
    }

    /**
     * Get the priority of a specific job
     * 
     * @param jobId The job identifier
     * @return The priority score, or null if job not found
     */
    public Double getJobPriority(String jobId) {
        try {
            Double score = zSetOperations.score(PRIORITY_QUEUE_KEY, jobId);
            logger.debug("Job {} has priority: {}", jobId, score);
            return score;
        } catch (Exception e) {
            logger.error("Failed to get priority for job {}", jobId, e);
            return null;
        }
    }

    /**
     * Update the priority of an existing job
     * 
     * @param jobId The job identifier
     * @param newPriority The new priority value
     * @return true if priority was updated successfully
     */
    public boolean updateJobPriority(String jobId, double newPriority) {
        try {
            // Check if job exists in queue
            Double currentPriority = zSetOperations.score(PRIORITY_QUEUE_KEY, jobId);
            if (currentPriority != null) {
                zSetOperations.add(PRIORITY_QUEUE_KEY, jobId, newPriority);
                logger.info("Updated job {} priority from {} to {}", jobId, currentPriority, newPriority);
                return true;
            } else {
                logger.warn("Job {} not found in priority queue for priority update", jobId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to update priority for job {}", jobId, e);
            return false;
        }
    }

    /**
     * Get the current size of the priority queue
     * 
     * @return The number of jobs in the queue
     */
    public long getQueueSize() {
        try {
            Long size = zSetOperations.count(PRIORITY_QUEUE_KEY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return size != null ? size : 0;
        } catch (Exception e) {
            logger.error("Failed to get queue size", e);
            return 0;
        }
    }

    /**
     * Get jobs within a priority range
     * 
     * @param minPriority Minimum priority (inclusive)
     * @param maxPriority Maximum priority (inclusive)
     * @return Set of job IDs within the priority range
     */
    public Set<String> getJobsByPriorityRange(double minPriority, double maxPriority) {
        try {
            Set<String> jobs = zSetOperations.rangeByScore(PRIORITY_QUEUE_KEY, minPriority, maxPriority);
            logger.debug("Found {} jobs with priority between {} and {}", 
                    jobs != null ? jobs.size() : 0, minPriority, maxPriority);
            return jobs;
        } catch (Exception e) {
            logger.error("Failed to get jobs by priority range", e);
            return Set.of();
        }
    }

    /**
     * Clear all jobs from the priority queue
     * 
     * @return true if queue was cleared successfully
     */
    public boolean clearQueue() {
        try {
            Boolean deleted = priorityQueueTemplate.delete(PRIORITY_QUEUE_KEY);
            logger.info("Cleared priority queue: {}", deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            logger.error("Failed to clear priority queue", e);
            return false;
        }
    }

    /**
     * Acquire a lock for job processing to prevent duplicate execution
     * 
     * @param jobId The job identifier
     * @param lockTimeoutSeconds Timeout for the lock in seconds
     * @return true if lock was acquired successfully
     */
    public boolean acquireJobLock(String jobId, long lockTimeoutSeconds) {
        try {
            String lockKey = JOB_LOCK_PREFIX + jobId;
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", lockTimeoutSeconds, TimeUnit.SECONDS);
            boolean success = Boolean.TRUE.equals(lockAcquired);
            logger.debug("Lock acquisition for job {}: {}", jobId, success);
            return success;
        } catch (Exception e) {
            logger.error("Failed to acquire lock for job {}", jobId, e);
            return false;
        }
    }

    /**
     * Release a job processing lock
     * 
     * @param jobId The job identifier
     * @return true if lock was released successfully
     */
    public boolean releaseJobLock(String jobId) {
        try {
            String lockKey = JOB_LOCK_PREFIX + jobId;
            Boolean deleted = redisTemplate.delete(lockKey);
            boolean success = Boolean.TRUE.equals(deleted);
            logger.debug("Lock release for job {}: {}", jobId, success);
            return success;
        } catch (Exception e) {
            logger.error("Failed to release lock for job {}", jobId, e);
            return false;
        }
    }

    /**
     * Check if a job is currently locked
     * 
     * @param jobId The job identifier
     * @return true if job is locked
     */
    public boolean isJobLocked(String jobId) {
        try {
            String lockKey = JOB_LOCK_PREFIX + jobId;
            Boolean exists = redisTemplate.hasKey(lockKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Failed to check lock status for job {}", jobId, e);
            return false;
        }
    }
}
