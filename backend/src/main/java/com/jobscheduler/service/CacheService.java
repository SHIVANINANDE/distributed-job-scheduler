package com.jobscheduler.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface CacheService {
    
    // Basic cache operations
    void put(String key, Object value);
    void put(String key, Object value, long ttlSeconds);
    
    <T> Optional<T> get(String key, Class<T> type);
    
    void evict(String key);
    void evictPattern(String pattern);
    void clear();
    
    // Job-specific caching
    void cacheJob(String jobId, Object job);
    void evictJob(String jobId);
    void evictJobDependenciesFromCache(String cacheKey);
    
    // Worker-specific caching
    void cacheWorker(String workerId, Object worker);
    void cacheWorker(String workerId, Object worker, int ttlMinutes); // Legacy method for backward compatibility
    void evictWorker(String workerId);
    void evictWorkerFromCache(String workerId); // Legacy method for backward compatibility
    
    // Health check
    boolean isAvailable();
    
    // Statistics
    long getHitCount();
    long getMissCount();
    double getHitRatio();
    
    // Cache management
    void refreshCache();
    long getCacheSize();
    void setCacheEvictionPolicy(String policy);
}
