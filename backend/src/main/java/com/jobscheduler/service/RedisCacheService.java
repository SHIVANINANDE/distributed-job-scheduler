package com.jobscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker;
import com.jobscheduler.model.JobDependency;
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
public class RedisCacheService implements CacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);
    
    private static final String JOB_CACHE_KEY_PREFIX = "job:cache:";
    private static final String JOB_STATUS_KEY_PREFIX = "job:status:";
    private static final String WORKER_HEARTBEAT_PREFIX = "worker:heartbeat:";
    private static final String JOB_METRICS_PREFIX = "job:metrics:";
    private static final String WORKER_CACHE_KEY_PREFIX = "worker:cache:";
    private static final String DEPENDENCY_CACHE_KEY_PREFIX = "dependency:cache:";
    private static final String JOB_DEPENDENCIES_KEY_PREFIX = "job:dependencies:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;

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

    // Worker caching methods
    
    /**
     * Cache a worker object
     * 
     * @param workerId The worker identifier
     * @param worker The worker object to cache
     * @param ttlMinutes Time to live in minutes
     */
    @CachePut(value = "workers", key = "#workerId")
    public Worker cacheWorker(String workerId, Worker worker, long ttlMinutes) {
        try {
            String key = WORKER_CACHE_KEY_PREFIX + workerId;
            redisTemplate.opsForValue().set(key, worker, ttlMinutes, TimeUnit.MINUTES);
            logger.debug("Cached worker {} with TTL {} minutes", workerId, ttlMinutes);
            return worker;
        } catch (Exception e) {
            logger.error("Failed to cache worker {}", workerId, e);
            return worker;
        }
    }

    /**
     * Retrieve a cached worker
     * 
     * @param workerId The worker identifier
     * @return The cached worker or null if not found
     */
    @Cacheable(value = "workers", key = "#workerId")
    public Worker getCachedWorker(String workerId) {
        try {
            String key = WORKER_CACHE_KEY_PREFIX + workerId;
            Object cachedWorker = redisTemplate.opsForValue().get(key);
            if (cachedWorker instanceof Worker) {
                logger.debug("Retrieved cached worker {}", workerId);
                return (Worker) cachedWorker;
            }
            logger.debug("Worker {} not found in cache", workerId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached worker {}", workerId, e);
            return null;
        }
    }

    /**
     * Remove a worker from cache
     * 
     * @param workerId The worker identifier
     */
    @CacheEvict(value = "workers", key = "#workerId")
    public void evictWorkerFromCache(String workerId) {
        try {
            String key = WORKER_CACHE_KEY_PREFIX + workerId;
            Boolean deleted = redisTemplate.delete(key);
            logger.debug("Evicted worker {} from cache: {}", workerId, deleted);
        } catch (Exception e) {
            logger.error("Failed to evict worker {} from cache", workerId, e);
        }
    }

    // Dependency caching methods
    
    /**
     * Cache a job dependency
     * 
     * @param dependencyKey The dependency cache key
     * @param dependency The dependency object to cache
     * @param ttlMinutes Time to live in minutes
     */
    public JobDependency cacheDependency(String dependencyKey, JobDependency dependency, long ttlMinutes) {
        try {
            String key = DEPENDENCY_CACHE_KEY_PREFIX + dependencyKey;
            redisTemplate.opsForValue().set(key, dependency, ttlMinutes, TimeUnit.MINUTES);
            logger.debug("Cached dependency {} with TTL {} minutes", dependencyKey, ttlMinutes);
            return dependency;
        } catch (Exception e) {
            logger.error("Failed to cache dependency {}", dependencyKey, e);
            return dependency;
        }
    }

    /**
     * Retrieve a cached dependency
     * 
     * @param dependencyKey The dependency cache key
     * @return The cached dependency or null if not found
     */
    public JobDependency getCachedDependency(String dependencyKey) {
        try {
            String key = DEPENDENCY_CACHE_KEY_PREFIX + dependencyKey;
            Object cachedDependency = redisTemplate.opsForValue().get(key);
            if (cachedDependency instanceof JobDependency) {
                logger.debug("Retrieved cached dependency {}", dependencyKey);
                return (JobDependency) cachedDependency;
            }
            logger.debug("Dependency {} not found in cache", dependencyKey);
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached dependency {}", dependencyKey, e);
            return null;
        }
    }

    /**
     * Remove a dependency from cache
     * 
     * @param dependencyKey The dependency cache key
     */
    public void evictDependencyFromCache(String dependencyKey) {
        try {
            String key = DEPENDENCY_CACHE_KEY_PREFIX + dependencyKey;
            Boolean deleted = redisTemplate.delete(key);
            logger.debug("Evicted dependency {} from cache: {}", dependencyKey, deleted);
        } catch (Exception e) {
            logger.error("Failed to evict dependency {} from cache", dependencyKey, e);
        }
    }

    /**
     * Cache job dependencies list
     * 
     * @param cacheKey The cache key for the dependencies list
     * @param dependencies The list of dependencies to cache
     * @param ttlMinutes Time to live in minutes
     */
    public void cacheJobDependencies(String cacheKey, List<JobDependency> dependencies, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(cacheKey, dependencies, ttlMinutes, TimeUnit.MINUTES);
            logger.debug("Cached job dependencies list {} (size: {}) for {} minutes", 
                        cacheKey, dependencies.size(), ttlMinutes);
        } catch (Exception e) {
            logger.error("Failed to cache job dependencies list {}", cacheKey, e);
        }
    }

    /**
     * Retrieve cached job dependencies list
     * 
     * @param cacheKey The cache key for the dependencies list
     * @return The cached dependencies list or null if not found
     */
    @SuppressWarnings("unchecked")
    public List<JobDependency> getCachedJobDependencies(String cacheKey) {
        try {
            Object cachedDependencies = redisTemplate.opsForValue().get(cacheKey);
            if (cachedDependencies instanceof List) {
                logger.debug("Retrieved cached job dependencies list {}", cacheKey);
                return (List<JobDependency>) cachedDependencies;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve cached job dependencies list {}", cacheKey, e);
            return null;
        }
    }

    /**
     * Remove job dependencies list from cache
     * 
     * @param cacheKey The cache key for the dependencies list
     */
    public void evictJobDependenciesFromCache(String cacheKey) {
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            logger.debug("Evicted job dependencies list {} from cache: {}", cacheKey, deleted);
        } catch (Exception e) {
            logger.error("Failed to evict job dependencies list {} from cache", cacheKey, e);
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
    
    // Implement missing CacheService interface methods
    
    @Override
    public void put(String key, Object value) {
        put(key, value, 3600); // Default 1 hour TTL
    }
    
    @Override
    public void put(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to put {} to cache", key, e);
        }
    }
    
    @Override
    public <T> java.util.Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && type.isInstance(value)) {
                return java.util.Optional.of(type.cast(value));
            }
        } catch (Exception e) {
            logger.error("Failed to get {} from cache", key, e);
        }
        return java.util.Optional.empty();
    }
    
    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Failed to evict {} from cache", key, e);
        }
    }
    
    @Override
    public void evictPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            logger.error("Failed to evict pattern {} from cache", pattern, e);
        }
    }
    
    @Override
    public void clear() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            logger.error("Failed to clear cache", e);
        }
    }
    
    @Override
    public void cacheJob(String jobId, Object job) {
        cacheJob(jobId, (Job) job, 60); // Default 60 minutes TTL
    }
    
    @Override
    public void evictJob(String jobId) {
        evictJobFromCache(jobId);
    }
    
    @Override
    public void cacheWorker(String workerId, Object worker) {
        cacheWorker(workerId, (Worker) worker, 300L); // Call the existing method with explicit long
    }
    
    @Override
    public void cacheWorker(String workerId, Object worker, int ttlMinutes) {
        cacheWorker(workerId, (Worker) worker, (long) ttlMinutes); // Cast to long to match existing method
    }
    
    @Override
    public void evictWorker(String workerId) {
        evictWorkerFromCache(workerId); // Use existing method
    }
    
    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            logger.error("Redis cache is not available", e);
            return false;
        }
    }
    
    /**
     * Ping Redis to check connectivity
     */
    public void ping() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            logger.debug("Redis ping successful");
        } catch (Exception e) {
            logger.error("Redis ping failed", e);
            throw new RuntimeException("Redis connection failed", e);
        }
    }
    
    @Override
    public long getHitCount() {
        // Redis doesn't provide built-in hit count, return 0 for now
        return 0L;
    }
    
    @Override
    public long getMissCount() {
        // Redis doesn't provide built-in miss count, return 0 for now
        return 0L;
    }
    
    @Override
    public double getHitRatio() {
        // Redis doesn't provide built-in hit ratio, return 0.0 for now
        return 0.0;
    }
    
    @Override
    public void refreshCache() {
        // For Redis, this could involve reloading data, but we'll keep it simple
        logger.info("Cache refresh requested - Redis cache is always fresh");
    }
    
    @Override
    public long getCacheSize() {
        try {
            return redisTemplate.getConnectionFactory().getConnection().dbSize();
        } catch (Exception e) {
            logger.error("Failed to get cache size", e);
            return 0L;
        }
    }
    
    @Override
    public void setCacheEvictionPolicy(String policy) {
        logger.info("Cache eviction policy set to: {}", policy);
        // Redis eviction policy is typically set at server level
    }
    
    /**
     * Add value to Redis set
     */
    public Long sadd(String key, String value) {
        try {
            return redisTemplate.opsForSet().add(key, value);
        } catch (Exception e) {
            logger.error("Error adding to set key: {}", key, e);
            return 0L;
        }
    }
    
    /**
     * Get all members of Redis set
     */
    public List<String> smembers(String key) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            return members != null ? 
                   members.stream().map(Object::toString).toList() : 
                   List.of();
        } catch (Exception e) {
            logger.error("Error getting set members for key: {}", key, e);
            return List.of();
        }
    }
    
    /**
     * Get string value from Redis
     */
    public String get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            logger.error("Error getting string value for key: {}", key, e);
            return null;
        }
    }
    
    /**
     * Delete key from Redis
     */
    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Error deleting key: {}", key, e);
            return false;
        }
    }
    
    /**
     * Remove value from Redis set
     */
    public Long srem(String key, String value) {
        try {
            return redisTemplate.opsForSet().remove(key, value);
        } catch (Exception e) {
            logger.error("Error removing from set key: {}", key, e);
            return 0L;
        }
    }
    
    /**
     * Get cardinality (size) of Redis set
     */
    public Long scard(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            logger.error("Error getting set size for key: {}", key, e);
            return 0L;
        }
    }
    
    // Set cache entry with TTL
    public void setCacheWithTTL(String key, Object value, long timeoutSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofSeconds(timeoutSeconds));
            logger.debug("Set cache entry with TTL: {} (expires in {} seconds)", key, timeoutSeconds);
        } catch (Exception e) {
            logger.error("Error setting cache entry with TTL for key {}: {}", key, e.getMessage());
        }
    }
    
    // Check if key exists in cache
    public boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            logger.error("Error checking cache key existence {}: {}", key, e.getMessage());
            return false;
        }
    }
    
    // Get cache TTL
    public long getTTL(String key) {
        try {
            return redisTemplate.getExpire(key);
        } catch (Exception e) {
            logger.error("Error getting TTL for key {}: {}", key, e.getMessage());
            return -1;
        }
    }
    
    // Cache job batch results
    public void cacheJobBatch(String batchId, Object batchResult, int timeoutSeconds) {
        try {
            String key = "batch:" + batchId;
            setCacheWithTTL(key, batchResult, timeoutSeconds);
        } catch (Exception e) {
            logger.error("Error caching job batch {}: {}", batchId, e.getMessage());
        }
    }
    
    // Get cached job batch
    public Object getCachedJobBatch(String batchId) {
        try {
            String key = "batch:" + batchId;
            String value = (String) redisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, Object.class);
            }
        } catch (Exception e) {
            logger.error("Error getting cached job batch {}: {}", batchId, e.getMessage());
        }
        return null;
    }
}
