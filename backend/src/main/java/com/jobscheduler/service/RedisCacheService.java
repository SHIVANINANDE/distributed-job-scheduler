package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);
    
    private static final String JOB_CACHE_KEY_PREFIX = "job:cache:";
    private static final String JOB_STATUS_KEY_PREFIX = "job:status:";
    private static final String WORKER_HEARTBEAT_PREFIX = "worker:heartbeat:";
    private static final String JOB_METRICS_PREFIX = "job:metrics:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache a job object
     * 
     * @param jobId The job identifier
     * @param job The job object to cache
     * @param ttlMinutes Time to live in minutes
     */
    @CachePut(value = "jobs", key = "#jobId")
    public Job cacheJob(String jobId, Job job, long ttlMinutes) {
        try {
            String key = JOB_CACHE_KEY_PREFIX + jobId;
            redisTemplate.opsForValue().set(key, job, ttlMinutes, TimeUnit.MINUTES);
            logger.debug("Cached job {} with TTL {} minutes", jobId, ttlMinutes);
            return job;
        } catch (Exception e) {
            logger.error("Failed to cache job {}", jobId, e);
            return job;
        }
    }

    /**
     * Retrieve a cached job
     * 
     * @param jobId The job identifier
     * @return The cached job or null if not found
     */
    @Cacheable(value = "jobs", key = "#jobId")
    public Job getCachedJob(String jobId) {
        try {
            String key = JOB_CACHE_KEY_PREFIX + jobId;
            Object cachedJob = redisTemplate.opsForValue().get(key);
            if (cachedJob instanceof Job) {
                logger.debug("Retrieved cached job {}", jobId);
                return (Job) cachedJob;
            }
            logger.debug("Job {} not found in cache", jobId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached job {}", jobId, e);
            return null;
        }
    }

    /**
     * Remove a job from cache
     * 
     * @param jobId The job identifier
     */
    @CacheEvict(value = "jobs", key = "#jobId")
    public void evictJobFromCache(String jobId) {
        try {
            String key = JOB_CACHE_KEY_PREFIX + jobId;
            Boolean deleted = redisTemplate.delete(key);
            logger.debug("Evicted job {} from cache: {}", jobId, deleted);
        } catch (Exception e) {
            logger.error("Failed to evict job {} from cache", jobId, e);
        }
    }

    /**
     * Cache job status for quick access
     * 
     * @param jobId The job identifier
     * @param status The job status
     */
    public void cacheJobStatus(String jobId, JobStatus status) {
        try {
            String key = JOB_STATUS_KEY_PREFIX + jobId;
            redisTemplate.opsForValue().set(key, status.name(), 1, TimeUnit.HOURS);
            logger.debug("Cached job status {} for job {}", status, jobId);
        } catch (Exception e) {
            logger.error("Failed to cache job status for job {}", jobId, e);
        }
    }

    /**
     * Get cached job status
     * 
     * @param jobId The job identifier
     * @return The job status or null if not cached
     */
    public JobStatus getCachedJobStatus(String jobId) {
        try {
            String key = JOB_STATUS_KEY_PREFIX + jobId;
            Object statusValue = redisTemplate.opsForValue().get(key);
            if (statusValue != null) {
                JobStatus status = JobStatus.valueOf(statusValue.toString());
                logger.debug("Retrieved cached job status {} for job {}", status, jobId);
                return status;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached job status for job {}", jobId, e);
            return null;
        }
    }

    /**
     * Record worker heartbeat
     * 
     * @param workerId The worker identifier
     * @param heartbeatTime The heartbeat timestamp
     */
    public void recordWorkerHeartbeat(String workerId, LocalDateTime heartbeatTime) {
        try {
            String key = WORKER_HEARTBEAT_PREFIX + workerId;
            redisTemplate.opsForValue().set(key, heartbeatTime.toString(), 5, TimeUnit.MINUTES);
            logger.debug("Recorded heartbeat for worker {} at {}", workerId, heartbeatTime);
        } catch (Exception e) {
            logger.error("Failed to record heartbeat for worker {}", workerId, e);
        }
    }

    /**
     * Get worker heartbeat time
     * 
     * @param workerId The worker identifier
     * @return The last heartbeat time or null if not found
     */
    public LocalDateTime getWorkerHeartbeat(String workerId) {
        try {
            String key = WORKER_HEARTBEAT_PREFIX + workerId;
            Object heartbeatValue = redisTemplate.opsForValue().get(key);
            if (heartbeatValue != null) {
                LocalDateTime heartbeat = LocalDateTime.parse(heartbeatValue.toString());
                logger.debug("Retrieved heartbeat for worker {} at {}", workerId, heartbeat);
                return heartbeat;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve heartbeat for worker {}", workerId, e);
            return null;
        }
    }

    /**
     * Get all active workers (those with recent heartbeats)
     * 
     * @param timeoutMinutes Timeout threshold in minutes
     * @return Set of active worker IDs
     */
    public Set<String> getActiveWorkers(int timeoutMinutes) {
        try {
            String pattern = WORKER_HEARTBEAT_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
            
            return keys.stream()
                    .filter(key -> {
                        try {
                            Object heartbeatValue = redisTemplate.opsForValue().get(key);
                            if (heartbeatValue != null) {
                                LocalDateTime heartbeat = LocalDateTime.parse(heartbeatValue.toString());
                                return heartbeat.isAfter(cutoffTime);
                            }
                            return false;
                        } catch (Exception e) {
                            logger.warn("Failed to parse heartbeat for key {}", key);
                            return false;
                        }
                    })
                    .map(key -> key.substring(WORKER_HEARTBEAT_PREFIX.length()))
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            logger.error("Failed to get active workers", e);
            return Set.of();
        }
    }

    /**
     * Increment job execution counter
     * 
     * @param jobId The job identifier
     * @return The new counter value
     */
    public Long incrementJobExecutionCount(String jobId) {
        try {
            String key = JOB_METRICS_PREFIX + jobId + ":executions";
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            logger.debug("Incremented execution count for job {} to {}", jobId, count);
            return count;
        } catch (Exception e) {
            logger.error("Failed to increment execution count for job {}", jobId, e);
            return 0L;
        }
    }

    /**
     * Record job execution time
     * 
     * @param jobId The job identifier
     * @param executionTimeMs Execution time in milliseconds
     */
    public void recordJobExecutionTime(String jobId, long executionTimeMs) {
        try {
            String key = JOB_METRICS_PREFIX + jobId + ":execution_time";
            redisTemplate.opsForValue().set(key, executionTimeMs, 24, TimeUnit.HOURS);
            logger.debug("Recorded execution time for job {}: {}ms", jobId, executionTimeMs);
        } catch (Exception e) {
            logger.error("Failed to record execution time for job {}", jobId, e);
        }
    }

    /**
     * Get job execution metrics
     * 
     * @param jobId The job identifier
     * @return Execution count and last execution time
     */
    public JobExecutionMetrics getJobExecutionMetrics(String jobId) {
        try {
            String countKey = JOB_METRICS_PREFIX + jobId + ":executions";
            String timeKey = JOB_METRICS_PREFIX + jobId + ":execution_time";
            
            Object countValue = redisTemplate.opsForValue().get(countKey);
            Object timeValue = redisTemplate.opsForValue().get(timeKey);
            
            long executionCount = countValue != null ? Long.parseLong(countValue.toString()) : 0L;
            long lastExecutionTime = timeValue != null ? Long.parseLong(timeValue.toString()) : 0L;
            
            return new JobExecutionMetrics(executionCount, lastExecutionTime);
        } catch (Exception e) {
            logger.error("Failed to get execution metrics for job {}", jobId, e);
            return new JobExecutionMetrics(0L, 0L);
        }
    }

    /**
     * Store a list of objects in Redis
     * 
     * @param key The cache key
     * @param list The list to store
     * @param ttlMinutes Time to live in minutes
     */
    public void cacheList(String key, List<?> list, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, list, ttlMinutes, TimeUnit.MINUTES);
            logger.debug("Cached list with key {} (size: {}) for {} minutes", key, list.size(), ttlMinutes);
        } catch (Exception e) {
            logger.error("Failed to cache list with key {}", key, e);
        }
    }

    /**
     * Retrieve a cached list
     * 
     * @param key The cache key
     * @return The cached list or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getCachedList(String key, Class<T> elementType) {
        try {
            Object cachedList = redisTemplate.opsForValue().get(key);
            if (cachedList instanceof List) {
                logger.debug("Retrieved cached list with key {}", key);
                return (List<T>) cachedList;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached list with key {}", key, e);
            return null;
        }
    }

    /**
     * Check if Redis is available
     * 
     * @return true if Redis is available
     */
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("redis:health:check");
            return true;
        } catch (Exception e) {
            logger.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clear all cache entries with a specific pattern
     * 
     * @param pattern The key pattern to match
     * @return Number of keys deleted
     */
    public long clearCacheByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                logger.info("Cleared {} cache entries matching pattern {}", deleted, pattern);
                return deleted != null ? deleted : 0L;
            }
            return 0L;
        } catch (Exception e) {
            logger.error("Failed to clear cache by pattern {}", pattern, e);
            return 0L;
        }
    }

    /**
     * Inner class for job execution metrics
     */
    public static class JobExecutionMetrics {
        private final long executionCount;
        private final long lastExecutionTimeMs;

        public JobExecutionMetrics(long executionCount, long lastExecutionTimeMs) {
            this.executionCount = executionCount;
            this.lastExecutionTimeMs = lastExecutionTimeMs;
        }

        public long getExecutionCount() {
            return executionCount;
        }

        public long getLastExecutionTimeMs() {
            return lastExecutionTimeMs;
        }

        @Override
        public String toString() {
            return String.format("JobExecutionMetrics{executionCount=%d, lastExecutionTimeMs=%d}", 
                    executionCount, lastExecutionTimeMs);
        }
    }
}
