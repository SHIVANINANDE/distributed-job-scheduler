package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for maintaining job execution history for failure analysis and recovery
 */
@Service
@Transactional
public class JobExecutionHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobExecutionHistoryService.class);
    
    @Autowired
    private RedisCacheService cacheService;
    
    // In-memory backup for execution history
    private final ConcurrentLinkedQueue<ExecutionHistoryEntry> executionHistory = 
            new ConcurrentLinkedQueue<>();
    
    private final Map<Long, List<ExecutionHistoryEntry>> jobHistoryIndex = 
            new ConcurrentHashMap<>();
    
    private static final int MAX_HISTORY_SIZE = 10000;
    private static final int HISTORY_RETENTION_DAYS = 30;
    
    /**
     * Record job failure in execution history
     */
    public void recordJobFailure(Job job, String errorMessage, Exception exception) {
        logger.debug("Recording job failure: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    job.getAssignedWorkerId(),
                    ExecutionEventType.JOB_FAILED,
                    "Job failed: " + errorMessage,
                    errorMessage,
                    exception != null ? exception.getClass().getSimpleName() : null,
                    LocalDateTime.now(),
                    job.getRetryCount()
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording job failure for job {}", job.getId(), e);
        }
    }
    
    /**
     * Record job moved to dead letter queue
     */
    public void recordJobMovedToDeadLetter(Job job, String reason) {
        logger.debug("Recording job moved to DLQ: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    job.getAssignedWorkerId(),
                    ExecutionEventType.MOVED_TO_DLQ,
                    "Job moved to dead letter queue: " + reason,
                    reason,
                    null,
                    LocalDateTime.now(),
                    job.getRetryCount()
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording DLQ move for job {}", job.getId(), e);
        }
    }
    
    /**
     * Record worker failure
     */
    public void recordWorkerFailure(Worker worker, String reason, int affectedJobs) {
        logger.debug("Recording worker failure: Worker ID: {}", worker.getWorkerId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    null, // No specific job ID
                    "Worker: " + worker.getName(),
                    worker.getWorkerId(),
                    ExecutionEventType.WORKER_FAILED,
                    String.format("Worker failed: %s (affected %d jobs)", reason, affectedJobs),
                    reason,
                    "WorkerFailure",
                    LocalDateTime.now(),
                    null
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording worker failure for worker {}", 
                        worker.getWorkerId(), e);
        }
    }
    
    /**
     * Record job reassignment
     */
    public void recordJobReassignment(Job job, Worker failedWorker, String reason) {
        logger.debug("Recording job reassignment: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    failedWorker.getWorkerId(),
                    ExecutionEventType.JOB_REASSIGNED,
                    String.format("Job reassigned from worker %s: %s", 
                                 failedWorker.getWorkerId(), reason),
                    reason,
                    "Reassignment",
                    LocalDateTime.now(),
                    job.getRetryCount()
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording job reassignment for job {}", job.getId(), e);
        }
    }
    
    /**
     * Record job timeout
     */
    public void recordJobTimeout(Job job) {
        logger.debug("Recording job timeout: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    job.getAssignedWorkerId(),
                    ExecutionEventType.JOB_TIMEOUT,
                    "Job execution timeout",
                    "Execution time exceeded limit",
                    "TimeoutException",
                    LocalDateTime.now(),
                    job.getRetryCount()
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording job timeout for job {}", job.getId(), e);
        }
    }
    
    /**
     * Record job retry
     */
    public void recordJobRetry(Job job, int newRetryCount, long delaySeconds) {
        logger.debug("Recording job retry: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    job.getAssignedWorkerId(),
                    ExecutionEventType.JOB_RETRY,
                    String.format("Job retry scheduled (attempt %d) with delay %d seconds", 
                                 newRetryCount, delaySeconds),
                    String.format("Retry attempt %d", newRetryCount),
                    "RetryScheduled",
                    LocalDateTime.now(),
                    newRetryCount
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording job retry for job {}", job.getId(), e);
        }
    }
    
    /**
     * Record job recovery
     */
    public void recordJobRecovery(Job job, String recoveryMethod) {
        logger.debug("Recording job recovery: Job ID: {}", job.getId());
        
        try {
            ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                    job.getId(),
                    job.getName(),
                    job.getAssignedWorkerId(),
                    ExecutionEventType.JOB_RECOVERED,
                    "Job recovered: " + recoveryMethod,
                    recoveryMethod,
                    "Recovery",
                    LocalDateTime.now(),
                    job.getRetryCount()
            );
            
            storeExecutionHistory(entry);
            
        } catch (Exception e) {
            logger.error("Error recording job recovery for job {}", job.getId(), e);
        }
    }
    
    /**
     * Store execution history entry
     */
    private void storeExecutionHistory(ExecutionHistoryEntry entry) {
        try {
            // Store in Redis with TTL
            storeInRedis(entry);
            
            // Store in memory as backup
            storeInMemory(entry);
            
            // Enforce size limits
            enforceHistoryLimits();
            
        } catch (Exception e) {
            logger.error("Error storing execution history entry", e);
        }
    }
    
    /**
     * Store entry in Redis
     */
    private void storeInRedis(ExecutionHistoryEntry entry) {
        try {
            String key = "execution_history:" + System.currentTimeMillis() + ":" + 
                        (entry.getJobId() != null ? entry.getJobId() : "worker");
            String value = serializeHistoryEntry(entry);
            
            // Store with retention period
            int retentionSeconds = HISTORY_RETENTION_DAYS * 24 * 60 * 60;
            cacheService.put(key, value, retentionSeconds);
            
            // Add to job index if applicable
            if (entry.getJobId() != null) {
                cacheService.sadd("job_history_index:" + entry.getJobId(), key);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to store execution history in Redis", e);
        }
    }
    
    /**
     * Store entry in memory
     */
    private void storeInMemory(ExecutionHistoryEntry entry) {
        executionHistory.offer(entry);
        
        if (entry.getJobId() != null) {
            jobHistoryIndex.computeIfAbsent(entry.getJobId(), k -> new ArrayList<>())
                          .add(entry);
        }
    }
    
    /**
     * Get execution history for a specific job
     */
    public List<ExecutionHistoryEntry> getJobExecutionHistory(Long jobId) {
        logger.debug("Getting execution history for job {}", jobId);
        
        try {
            // Try Redis first
            List<ExecutionHistoryEntry> redisHistory = getRedisJobHistory(jobId);
            if (!redisHistory.isEmpty()) {
                return redisHistory;
            }
            
            // Fallback to memory
            return jobHistoryIndex.getOrDefault(jobId, new ArrayList<>());
            
        } catch (Exception e) {
            logger.error("Error getting job execution history for job {}", jobId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get Redis job history
     */
    private List<ExecutionHistoryEntry> getRedisJobHistory(Long jobId) {
        List<ExecutionHistoryEntry> history = new ArrayList<>();
        
        try {
            List<String> keys = cacheService.smembers("job_history_index:" + jobId);
            
            for (String key : keys) {
                String entryData = cacheService.get(key);
                if (entryData != null) {
                    ExecutionHistoryEntry entry = deserializeHistoryEntry(entryData);
                    if (entry != null) {
                        history.add(entry);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error getting Redis job history", e);
        }
        
        return history;
    }
    
    /**
     * Get recent execution history
     */
    public List<ExecutionHistoryEntry> getRecentExecutionHistory(int limit) {
        List<ExecutionHistoryEntry> recent = new ArrayList<>();
        
        try {
            // Get from memory first (most recent)
            List<ExecutionHistoryEntry> memoryEntries = new ArrayList<>(executionHistory);
            
            // Sort by timestamp descending and take the most recent
            memoryEntries.sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()));
            
            int count = Math.min(limit, memoryEntries.size());
            for (int i = 0; i < count; i++) {
                recent.add(memoryEntries.get(i));
            }
            
        } catch (Exception e) {
            logger.error("Error getting recent execution history", e);
        }
        
        return recent;
    }
    
    /**
     * Enforce history size limits
     */
    private void enforceHistoryLimits() {
        // Clean memory history
        while (executionHistory.size() > MAX_HISTORY_SIZE) {
            ExecutionHistoryEntry oldest = executionHistory.poll();
            if (oldest != null && oldest.getJobId() != null) {
                List<ExecutionHistoryEntry> jobHistory = jobHistoryIndex.get(oldest.getJobId());
                if (jobHistory != null) {
                    jobHistory.remove(oldest);
                    if (jobHistory.isEmpty()) {
                        jobHistoryIndex.remove(oldest.getJobId());
                    }
                }
            }
        }
    }
    
    /**
     * Clean up old execution history
     */
    public void cleanupOldHistory() {
        logger.debug("Cleaning up old execution history");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(HISTORY_RETENTION_DAYS);
            
            // Clean memory entries
            List<ExecutionHistoryEntry> toRemove = new ArrayList<>();
            for (ExecutionHistoryEntry entry : executionHistory) {
                if (entry.getTimestamp().isBefore(cutoffTime)) {
                    toRemove.add(entry);
                }
            }
            
            for (ExecutionHistoryEntry entry : toRemove) {
                executionHistory.remove(entry);
                if (entry.getJobId() != null) {
                    List<ExecutionHistoryEntry> jobHistory = jobHistoryIndex.get(entry.getJobId());
                    if (jobHistory != null) {
                        jobHistory.remove(entry);
                        if (jobHistory.isEmpty()) {
                            jobHistoryIndex.remove(entry.getJobId());
                        }
                    }
                }
            }
            
            if (!toRemove.isEmpty()) {
                logger.info("Cleaned up {} old execution history entries", toRemove.size());
            }
            
        } catch (Exception e) {
            logger.error("Error cleaning up old execution history", e);
        }
    }
    
    /**
     * Serialize history entry
     */
    private String serializeHistoryEntry(ExecutionHistoryEntry entry) {
        return String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s",
                entry.getJobId() != null ? entry.getJobId().toString() : "",
                entry.getJobName() != null ? entry.getJobName() : "",
                entry.getWorkerId() != null ? entry.getWorkerId() : "",
                entry.getEventType().name(),
                entry.getDescription() != null ? entry.getDescription() : "",
                entry.getDetails() != null ? entry.getDetails() : "",
                entry.getExceptionType() != null ? entry.getExceptionType() : "",
                entry.getTimestamp().toString(),
                entry.getRetryCount() != null ? entry.getRetryCount().toString() : ""
        );
    }
    
    /**
     * Deserialize history entry
     */
    private ExecutionHistoryEntry deserializeHistoryEntry(String data) {
        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length != 9) {
                return null;
            }
            
            return new ExecutionHistoryEntry(
                    parts[0].isEmpty() ? null : Long.valueOf(parts[0]),
                    parts[1].isEmpty() ? null : parts[1],
                    parts[2].isEmpty() ? null : parts[2],
                    ExecutionEventType.valueOf(parts[3]),
                    parts[4].isEmpty() ? null : parts[4],
                    parts[5].isEmpty() ? null : parts[5],
                    parts[6].isEmpty() ? null : parts[6],
                    LocalDateTime.parse(parts[7]),
                    parts[8].isEmpty() ? null : Integer.valueOf(parts[8])
            );
            
        } catch (Exception e) {
            logger.warn("Error deserializing history entry: {}", data, e);
            return null;
        }
    }
    
    /**
     * Execution event types
     */
    public enum ExecutionEventType {
        JOB_FAILED,
        MOVED_TO_DLQ,
        WORKER_FAILED,
        JOB_REASSIGNED,
        JOB_TIMEOUT,
        JOB_RETRY,
        JOB_RECOVERED
    }
    
    /**
     * Execution history entry data class
     */
    public static class ExecutionHistoryEntry {
        private final Long jobId;
        private final String jobName;
        private final String workerId;
        private final ExecutionEventType eventType;
        private final String description;
        private final String details;
        private final String exceptionType;
        private final LocalDateTime timestamp;
        private final Integer retryCount;
        
        public ExecutionHistoryEntry(Long jobId, String jobName, String workerId,
                                   ExecutionEventType eventType, String description,
                                   String details, String exceptionType,
                                   LocalDateTime timestamp, Integer retryCount) {
            this.jobId = jobId;
            this.jobName = jobName;
            this.workerId = workerId;
            this.eventType = eventType;
            this.description = description;
            this.details = details;
            this.exceptionType = exceptionType;
            this.timestamp = timestamp;
            this.retryCount = retryCount;
        }
        
        // Getters
        public Long getJobId() { return jobId; }
        public String getJobName() { return jobName; }
        public String getWorkerId() { return workerId; }
        public ExecutionEventType getEventType() { return eventType; }
        public String getDescription() { return description; }
        public String getDetails() { return details; }
        public String getExceptionType() { return exceptionType; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Integer getRetryCount() { return retryCount; }
    }
}
