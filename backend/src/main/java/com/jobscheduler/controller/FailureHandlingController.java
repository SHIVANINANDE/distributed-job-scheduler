package com.jobscheduler.controller;

import com.jobscheduler.service.FailureHandlingService;
import com.jobscheduler.service.DeadLetterQueueService;
import com.jobscheduler.service.JobExecutionHistoryService;
import com.jobscheduler.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for failure handling operations
 */
@RestController
@RequestMapping("/api/failure-handling")
@CrossOrigin(origins = "*")
public class FailureHandlingController {
    
    private static final Logger logger = LoggerFactory.getLogger(FailureHandlingController.class);
    
    @Autowired
    private FailureHandlingService failureHandlingService;
    
    @Autowired
    private DeadLetterQueueService deadLetterQueueService;
    
    @Autowired
    private JobExecutionHistoryService executionHistoryService;
    
    /**
     * Get failure handling statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFailureHandlingStats() {
        logger.debug("Getting failure handling statistics");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get retry statistics
            Map<String, Object> retryStats = failureHandlingService.getRetryStatistics();
            stats.put("retryStatistics", retryStats);
            
            // Get DLQ statistics
            Map<String, Object> dlqStats = deadLetterQueueService.getDeadLetterQueueStats();
            stats.put("deadLetterQueueStatistics", dlqStats);
            
            // Add timestamp
            stats.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error getting failure handling statistics", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get dead letter queue entries
     */
    @GetMapping("/dead-letter-queue")
    public ResponseEntity<List<DeadLetterQueueService.DeadLetterQueueEntry>> getDeadLetterQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy) {
        
        logger.debug("Getting dead letter queue entries: page={}, size={}, sortBy={}", 
                    page, size, sortBy);
        
        try {
            List<DeadLetterQueueService.DeadLetterQueueEntry> entries = 
                    deadLetterQueueService.getDeadLetterQueueEntries(page, size, sortBy);
            
            return ResponseEntity.ok(entries);
            
        } catch (Exception e) {
            logger.error("Error getting dead letter queue entries", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Retry a job from dead letter queue
     */
    @PostMapping("/retry-from-dlq/{jobId}")
    public ResponseEntity<Map<String, Object>> retryFromDeadLetterQueue(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "false") boolean resetRetryCount) {
        
        logger.info("Retrying job {} from dead letter queue, resetRetryCount={}", 
                   jobId, resetRetryCount);
        
        try {
            boolean success = deadLetterQueueService.retryFromDeadLetterQueue(
                    jobId, resetRetryCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("jobId", jobId);
            response.put("resetRetryCount", resetRetryCount);
            response.put("timestamp", System.currentTimeMillis());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Job not found in dead letter queue or retry failed");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error retrying job {} from dead letter queue", jobId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retry job: " + e.getMessage());
            error.put("jobId", jobId);
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Remove a job from dead letter queue
     */
    @DeleteMapping("/dead-letter-queue/{jobId}")
    public ResponseEntity<Map<String, Object>> removeFromDeadLetterQueue(
            @PathVariable Long jobId) {
        
        logger.info("Removing job {} from dead letter queue", jobId);
        
        try {
            boolean success = deadLetterQueueService.removeFromDeadLetterQueue(jobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("jobId", jobId);
            response.put("timestamp", System.currentTimeMillis());
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Job not found in dead letter queue");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error removing job {} from dead letter queue", jobId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to remove job: " + e.getMessage());
            error.put("jobId", jobId);
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get job execution history
     */
    @GetMapping("/execution-history/{jobId}")
    public ResponseEntity<List<JobExecutionHistoryService.ExecutionHistoryEntry>> getJobExecutionHistory(
            @PathVariable Long jobId) {
        
        logger.debug("Getting execution history for job {}", jobId);
        
        try {
            List<JobExecutionHistoryService.ExecutionHistoryEntry> history = 
                    executionHistoryService.getJobExecutionHistory(jobId);
            
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            logger.error("Error getting execution history for job {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get recent execution history
     */
    @GetMapping("/execution-history")
    public ResponseEntity<List<JobExecutionHistoryService.ExecutionHistoryEntry>> getRecentExecutionHistory(
            @RequestParam(defaultValue = "50") int limit) {
        
        logger.debug("Getting recent execution history with limit {}", limit);
        
        try {
            List<JobExecutionHistoryService.ExecutionHistoryEntry> history = 
                    executionHistoryService.getRecentExecutionHistory(limit);
            
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            logger.error("Error getting recent execution history", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Manually trigger worker failure detection
     */
    @PostMapping("/detect-worker-failures")
    public ResponseEntity<Map<String, Object>> detectWorkerFailures() {
        logger.info("Manually triggering worker failure detection");
        
        try {
            failureHandlingService.detectAndHandleWorkerFailures();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Worker failure detection triggered");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error triggering worker failure detection", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to detect worker failures: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get failure handling configuration
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getFailureHandlingConfig() {
        logger.debug("Getting failure handling configuration");
        
        try {
            Map<String, Object> config = new HashMap<>();
            
            // Get configuration from FailureHandlingService
            config.put("maxRetryAttempts", 3); // This would come from config
            config.put("initialRetryDelaySeconds", 30);
            config.put("maxRetryDelaySeconds", 3600);
            config.put("exponentialBackoffMultiplier", 2.0);
            config.put("workerHeartbeatTimeoutMinutes", 5);
            config.put("deadLetterQueueMaxSize", 1000);
            config.put("deadLetterQueueRetentionDays", 7);
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            logger.error("Error getting failure handling configuration", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get configuration: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Clean up dead letter queue
     */
    @PostMapping("/dead-letter-queue/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupDeadLetterQueue() {
        logger.info("Manually triggering dead letter queue cleanup");
        
        try {
            deadLetterQueueService.cleanupExpiredEntries();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dead letter queue cleanup completed");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error cleaning up dead letter queue", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to cleanup dead letter queue: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Bulk retry jobs from dead letter queue
     */
    @PostMapping("/dead-letter-queue/bulk-retry")
    public ResponseEntity<Map<String, Object>> bulkRetryFromDeadLetterQueue(
            @RequestParam(defaultValue = "10") int maxJobs,
            @RequestParam(defaultValue = "false") boolean resetRetryCount) {
        
        logger.info("Bulk retrying jobs from dead letter queue: maxJobs={}, resetRetryCount={}", 
                   maxJobs, resetRetryCount);
        
        try {
            // Get DLQ entries to retry
            List<DeadLetterQueueService.DeadLetterQueueEntry> entries = 
                    deadLetterQueueService.getDeadLetterQueueEntries(0, maxJobs, "oldest");
            
            int retryCount = 0;
            int successCount = 0;
            
            for (DeadLetterQueueService.DeadLetterQueueEntry entry : entries) {
                if (retryCount >= maxJobs) {
                    break;
                }
                
                try {
                    boolean success = deadLetterQueueService.retryFromDeadLetterQueue(
                            entry.getJobId(), resetRetryCount);
                    if (success) {
                        successCount++;
                    }
                    retryCount++;
                } catch (Exception e) {
                    logger.warn("Failed to retry job {} from DLQ", entry.getJobId(), e);
                    retryCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalAttempted", retryCount);
            response.put("successfulRetries", successCount);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error bulk retrying jobs from dead letter queue", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to bulk retry jobs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
