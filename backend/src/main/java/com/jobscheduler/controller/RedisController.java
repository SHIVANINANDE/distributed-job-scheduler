package com.jobscheduler.controller;

import com.jobscheduler.service.RedisCacheService;
import com.jobscheduler.service.RedisPriorityQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/redis")
@CrossOrigin(origins = "*")
public class RedisController {

    @Autowired
    private RedisCacheService cacheService;

    @Autowired
    private RedisPriorityQueueService priorityQueueService;

    /**
     * Check Redis health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        Map<String, Object> health = new HashMap<>();
        
        boolean isAvailable = cacheService.isRedisAvailable();
        health.put("status", isAvailable ? "UP" : "DOWN");
        health.put("available", isAvailable);
        
        if (isAvailable) {
            health.put("queueSize", priorityQueueService.getQueueSize());
            health.put("activeWorkers", cacheService.getActiveWorkers(5).size());
        }
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get priority queue statistics
     */
    @GetMapping("/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            stats.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(stats);
        }
        
        stats.put("queueSize", priorityQueueService.getQueueSize());
        stats.put("highestPriorityJob", priorityQueueService.peekHighestPriorityJob());
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Get active workers
     */
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> getActiveWorkers() {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        Set<String> activeWorkers = cacheService.getActiveWorkers(5);
        response.put("activeWorkers", activeWorkers);
        response.put("count", activeWorkers.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Clear cache by pattern (admin operation)
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> clearCache(@RequestParam String pattern) {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        long deletedCount = cacheService.clearCacheByPattern(pattern);
        response.put("deletedCount", deletedCount);
        response.put("pattern", pattern);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Clear entire priority queue (admin operation)
     */
    @DeleteMapping("/queue")
    public ResponseEntity<Map<String, Object>> clearQueue() {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        boolean cleared = priorityQueueService.clearQueue();
        response.put("cleared", cleared);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Add job to priority queue manually (admin operation)
     */
    @PostMapping("/queue/job")
    public ResponseEntity<Map<String, Object>> addJobToQueue(
            @RequestParam String jobId, 
            @RequestParam double priority) {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        boolean added = priorityQueueService.addJobToPriorityQueue(jobId, priority);
        response.put("added", added);
        response.put("jobId", jobId);
        response.put("priority", priority);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get job priority from queue
     */
    @GetMapping("/queue/job/{jobId}/priority")
    public ResponseEntity<Map<String, Object>> getJobPriority(@PathVariable String jobId) {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        Double priority = priorityQueueService.getJobPriority(jobId);
        response.put("jobId", jobId);
        response.put("priority", priority);
        response.put("inQueue", priority != null);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update job priority in queue
     */
    @PutMapping("/queue/job/{jobId}/priority")
    public ResponseEntity<Map<String, Object>> updateJobPriority(
            @PathVariable String jobId, 
            @RequestParam double priority) {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        boolean updated = priorityQueueService.updateJobPriority(jobId, priority);
        response.put("updated", updated);
        response.put("jobId", jobId);
        response.put("priority", priority);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Remove job from priority queue
     */
    @DeleteMapping("/queue/job/{jobId}")
    public ResponseEntity<Map<String, Object>> removeJobFromQueue(@PathVariable String jobId) {
        Map<String, Object> response = new HashMap<>();
        
        if (!cacheService.isRedisAvailable()) {
            response.put("error", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
        
        boolean removed = priorityQueueService.removeJobFromQueue(jobId);
        response.put("removed", removed);
        response.put("jobId", jobId);
        
        return ResponseEntity.ok(response);
    }
}
