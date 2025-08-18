package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Optional;
import java.util.HashMap;
import static java.util.stream.Collectors.*;

/**
 * Service for managing the Dead Letter Queue (DLQ) for jobs that have failed
 * after exhausting all retry attempts.
 */
@Service
@Transactional
public class DeadLetterQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueService.class);
    
    @Autowired
    private RedisCacheService cacheService;
    
    @Autowired
    private JobRepository jobRepository;
    
    @Value("${dead-letter-queue.max-size:1000}")
    private int maxDeadLetterQueueSize;
    
    @Value("${dead-letter-queue.retention-days:30}")
    private int retentionDays;
    
    // In-memory backup for DLQ when Redis is unavailable
    private final ConcurrentLinkedQueue<DeadLetterEntry> deadLetterQueue = 
            new ConcurrentLinkedQueue<>();
    
    private final Map<Long, DeadLetterEntry> deadLetterIndex = 
            new ConcurrentHashMap<>();
    
    /**
     * Add a job to the dead letter queue
     */
    public void addToDeadLetterQueue(Job job, String reason) {
        logger.warn("Adding job to dead letter queue: Job ID: {}, Reason: {}", 
                   job.getId(), reason);
        
        try {
            DeadLetterEntry entry = new DeadLetterEntry(
                    job.getId(),
                    job.getName(),
                    job.getJobType(),
                    job.getAssignedWorkerId(),
                    job.getRetryCount(),
                    reason,
                    job.getErrorMessage(),
                    LocalDateTime.now()
            );
            
            // Try to store in Redis first
            boolean storedInRedis = storeInRedisDeadLetterQueue(entry);
            
            // Always store in memory as backup
            storeInMemoryDeadLetterQueue(entry);
            
            // Enforce queue size limits
            enforceQueueSizeLimit();
            
            logger.info("Job {} added to dead letter queue successfully", job.getId());
            
        } catch (Exception e) {
            logger.error("Error adding job {} to dead letter queue", job.getId(), e);
        }
    }
    
    /**
     * Store entry in Redis dead letter queue
     */
    private boolean storeInRedisDeadLetterQueue(DeadLetterEntry entry) {
        try {
            String key = "dlq:job:" + entry.getJobId();
            String value = serializeDeadLetterEntry(entry);
            
            // Store with retention period
            int retentionSeconds = retentionDays * 24 * 60 * 60;
            cacheService.put(key, value, retentionSeconds);
            
            // Add to DLQ index
            cacheService.sadd("dlq:index", String.valueOf(entry.getJobId()));
            
            return true;
            
        } catch (Exception e) {
            logger.warn("Failed to store in Redis DLQ, using memory backup", e);
            return false;
        }
    }
    
    /**
     * Store entry in memory dead letter queue
     */
    private void storeInMemoryDeadLetterQueue(DeadLetterEntry entry) {
        deadLetterQueue.offer(entry);
        deadLetterIndex.put(entry.getJobId(), entry);
    }
    
    /**
     * Get dead letter queue entries
     */
    public List<DeadLetterQueueEntry> getDeadLetterQueueEntries(int page, int size, String sortBy) {
        logger.debug("Retrieving DLQ entries: page={}, size={}, sortBy={}", page, size, sortBy);
        
        int limit = size;
        int offset = page * size;
        
        try {
            // Try Redis first
            List<DeadLetterEntry> redisEntries = getRedisDeadLetterEntries(limit, offset);
            if (!redisEntries.isEmpty()) {
                return redisEntries.stream()
                               .map(this::convertToDeadLetterQueueEntry)
                               .toList();
            }
            
            // Fallback to memory
            return getMemoryDeadLetterEntries(limit, offset).stream()
                   .map(this::convertToDeadLetterQueueEntry)
                   .toList();
            
        } catch (Exception e) {
            logger.error("Error retrieving DLQ entries", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get dead letter queue entries (original method for backward compatibility)
     */
    public List<DeadLetterEntry> getDeadLetterQueueEntries(int limit, int offset) {
        logger.debug("Retrieving DLQ entries: limit={}, offset={}", limit, offset);
        
        try {
            // Try Redis first
            List<DeadLetterEntry> redisEntries = getRedisDeadLetterEntries(limit, offset);
            if (!redisEntries.isEmpty()) {
                return redisEntries;
            }
            
            // Fallback to memory
            return getMemoryDeadLetterEntries(limit, offset);
            
        } catch (Exception e) {
            logger.error("Error retrieving DLQ entries", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get DLQ entries from Redis
     */
    private List<DeadLetterEntry> getRedisDeadLetterEntries(int limit, int offset) {
        List<DeadLetterEntry> entries = new ArrayList<>();
        
        try {
            List<String> jobIds = cacheService.smembers("dlq:index");
            
            int start = Math.min(offset, jobIds.size());
            int end = Math.min(offset + limit, jobIds.size());
            
            for (int i = start; i < end; i++) {
                String jobId = jobIds.get(i);
                String entryData = cacheService.get("dlq:job:" + jobId);
                
                if (entryData != null) {
                    DeadLetterEntry entry = deserializeDeadLetterEntry(entryData);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error getting Redis DLQ entries", e);
        }
        
        return entries;
    }
    
    /**
     * Get DLQ entries from memory
     */
    private List<DeadLetterEntry> getMemoryDeadLetterEntries(int limit, int offset) {
        List<DeadLetterEntry> entries = new ArrayList<>();
        List<DeadLetterEntry> allEntries = new ArrayList<>(deadLetterQueue);
        
        int start = Math.min(offset, allEntries.size());
        int end = Math.min(offset + limit, allEntries.size());
        
        for (int i = start; i < end; i++) {
            entries.add(allEntries.get(i));
        }
        
        return entries;
    }
    
    /**
     * Remove job from dead letter queue
     */
    public boolean removeFromDeadLetterQueue(Long jobId) {
        logger.info("Removing job {} from dead letter queue", jobId);
        
        try {
            // Remove from Redis
            cacheService.delete("dlq:job:" + jobId);
            cacheService.srem("dlq:index", String.valueOf(jobId));
            
            // Remove from memory
            DeadLetterEntry entry = deadLetterIndex.remove(jobId);
            if (entry != null) {
                deadLetterQueue.remove(entry);
            }
            
            logger.info("Job {} removed from dead letter queue", jobId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error removing job {} from DLQ", jobId, e);
            return false;
        }
    }
    
    /**
     * Get dead letter queue size
     */
    public long getDeadLetterQueueSize() {
        try {
            // Try Redis first
            Long redisSize = cacheService.scard("dlq:index");
            if (redisSize != null && redisSize > 0) {
                return redisSize;
            }
            
            // Fallback to memory
            return deadLetterQueue.size();
            
        } catch (Exception e) {
            logger.warn("Error getting DLQ size", e);
            return deadLetterQueue.size();
        }
    }
    
    /**
     * Clean up expired entries from dead letter queue
     */
    public void cleanupExpiredEntries() {
        logger.debug("Cleaning up expired DLQ entries");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            
            // Clean Redis entries
            cleanupRedisExpiredEntries(cutoffTime);
            
            // Clean memory entries
            cleanupMemoryExpiredEntries(cutoffTime);
            
        } catch (Exception e) {
            logger.error("Error cleaning up expired DLQ entries", e);
        }
    }
    
    /**
     * Clean up expired Redis entries
     */
    private void cleanupRedisExpiredEntries(LocalDateTime cutoffTime) {
        try {
            List<String> jobIds = cacheService.smembers("dlq:index");
            int removed = 0;
            
            for (String jobId : jobIds) {
                String entryData = cacheService.get("dlq:job:" + jobId);
                if (entryData != null) {
                    DeadLetterEntry entry = deserializeDeadLetterEntry(entryData);
                    if (entry != null && entry.getCreatedAt().isBefore(cutoffTime)) {
                        removeFromDeadLetterQueue(Long.valueOf(jobId));
                        removed++;
                    }
                }
            }
            
            if (removed > 0) {
                logger.info("Cleaned up {} expired entries from Redis DLQ", removed);
            }
            
        } catch (Exception e) {
            logger.warn("Error cleaning up Redis DLQ entries", e);
        }
    }
    
    /**
     * Clean up expired memory entries
     */
    private void cleanupMemoryExpiredEntries(LocalDateTime cutoffTime) {
        List<DeadLetterEntry> toRemove = new ArrayList<>();
        
        for (DeadLetterEntry entry : deadLetterQueue) {
            if (entry.getCreatedAt().isBefore(cutoffTime)) {
                toRemove.add(entry);
            }
        }
        
        for (DeadLetterEntry entry : toRemove) {
            deadLetterQueue.remove(entry);
            deadLetterIndex.remove(entry.getJobId());
        }
        
        if (!toRemove.isEmpty()) {
            logger.info("Cleaned up {} expired entries from memory DLQ", toRemove.size());
        }
    }
    
    /**
     * Enforce queue size limit
     */
    private void enforceQueueSizeLimit() {
        // Clean memory queue
        while (deadLetterQueue.size() > maxDeadLetterQueueSize) {
            DeadLetterEntry oldest = deadLetterQueue.poll();
            if (oldest != null) {
                deadLetterIndex.remove(oldest.getJobId());
            }
        }
        
        // Note: Redis entries are handled by TTL
    }
    
    /**
     * Serialize dead letter entry to string
     */
    private String serializeDeadLetterEntry(DeadLetterEntry entry) {
        return String.format("%d|%s|%s|%s|%d|%s|%s|%s",
                entry.getJobId(),
                entry.getJobName() != null ? entry.getJobName() : "",
                entry.getJobType() != null ? entry.getJobType() : "",
                entry.getAssignedWorkerId() != null ? entry.getAssignedWorkerId() : "",
                entry.getRetryCount() != null ? entry.getRetryCount() : 0,
                entry.getFailureReason() != null ? entry.getFailureReason() : "",
                entry.getErrorMessage() != null ? entry.getErrorMessage() : "",
                entry.getCreatedAt().toString()
        );
    }
    
    /**
     * Deserialize dead letter entry from string
     */
    private DeadLetterEntry deserializeDeadLetterEntry(String data) {
        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length != 8) {
                return null;
            }
            
            return new DeadLetterEntry(
                    Long.valueOf(parts[0]),
                    parts[1].isEmpty() ? null : parts[1],
                    parts[2].isEmpty() ? null : parts[2],
                    parts[3].isEmpty() ? null : parts[3],
                    Integer.valueOf(parts[4]),
                    parts[5].isEmpty() ? null : parts[5],
                    parts[6].isEmpty() ? null : parts[6],
                    LocalDateTime.parse(parts[7])
            );
            
        } catch (Exception e) {
            logger.warn("Error deserializing DLQ entry: {}", data, e);
            return null;
        }
    }
    
    /**
     * Get DLQ statistics
     */
    public DLQStatistics getDLQStatistics() {
        try {
            long totalEntries = getDeadLetterQueueSize();
            
            // Count entries by failure reason (simplified)
            Map<String, Long> reasonCounts = new ConcurrentHashMap<>();
            
            List<DeadLetterEntry> entries = getDeadLetterQueueEntries(100, 0);
            for (DeadLetterEntry entry : entries) {
                String reason = entry.getFailureReason() != null ? 
                               entry.getFailureReason() : "Unknown";
                reasonCounts.merge(reason, 1L, Long::sum);
            }
            
            return new DLQStatistics(totalEntries, reasonCounts);
            
        } catch (Exception e) {
            logger.error("Error getting DLQ statistics", e);
            return new DLQStatistics(0, new ConcurrentHashMap<>());
        }
    }
    
    /**
     * Dead Letter Entry data class
     */
    public static class DeadLetterEntry {
        private final Long jobId;
        private final String jobName;
        private final String jobType;
        private final String assignedWorkerId;
        private final Integer retryCount;
        private final String failureReason;
        private final String errorMessage;
        private final LocalDateTime createdAt;
        
        public DeadLetterEntry(Long jobId, String jobName, String jobType, 
                              String assignedWorkerId, Integer retryCount,
                              String failureReason, String errorMessage, 
                              LocalDateTime createdAt) {
            this.jobId = jobId;
            this.jobName = jobName;
            this.jobType = jobType;
            this.assignedWorkerId = assignedWorkerId;
            this.retryCount = retryCount;
            this.failureReason = failureReason;
            this.errorMessage = errorMessage;
            this.createdAt = createdAt;
        }
        
        // Getters
        public Long getJobId() { return jobId; }
        public String getJobName() { return jobName; }
        public String getJobType() { return jobType; }
        public String getAssignedWorkerId() { return assignedWorkerId; }
        public Integer getRetryCount() { return retryCount; }
        public String getFailureReason() { return failureReason; }
        public String getErrorMessage() { return errorMessage; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    /**
     * DLQ Statistics data class
     */
    public static class DLQStatistics {
        private final long totalEntries;
        private final Map<String, Long> failureReasonCounts;
        
        public DLQStatistics(long totalEntries, Map<String, Long> failureReasonCounts) {
            this.totalEntries = totalEntries;
            this.failureReasonCounts = failureReasonCounts;
        }
        
        public long getTotalEntries() { return totalEntries; }
        public Map<String, Long> getFailureReasonCounts() { return failureReasonCounts; }
    }
    
    /**
     * Dead letter queue entry for API responses
     */
    public static class DeadLetterQueueEntry {
        private final Long jobId;
        private final String jobName;
        private final String jobType;
        private final String failureReason;
        private final String errorMessage;
        private final LocalDateTime addedAt;
        private final Integer retryCount;
        
        public DeadLetterQueueEntry(Long jobId, String jobName, String jobType,
                                  String failureReason, String errorMessage,
                                  LocalDateTime addedAt, Integer retryCount) {
            this.jobId = jobId;
            this.jobName = jobName;
            this.jobType = jobType;
            this.failureReason = failureReason;
            this.errorMessage = errorMessage;
            this.addedAt = addedAt;
            this.retryCount = retryCount;
        }
        
        // Getters
        public Long getJobId() { return jobId; }
        public String getJobName() { return jobName; }
        public String getJobType() { return jobType; }
        public String getFailureReason() { return failureReason; }
        public String getErrorMessage() { return errorMessage; }
        public LocalDateTime getAddedAt() { return addedAt; }
        public Integer getRetryCount() { return retryCount; }
    }
    
    /**
     * Convert DeadLetterEntry to DeadLetterQueueEntry
     */
    private DeadLetterQueueEntry convertToDeadLetterQueueEntry(DeadLetterEntry entry) {
        return new DeadLetterQueueEntry(
            entry.getJobId(),
            entry.getJobName(),
            entry.getJobType(),
            entry.getFailureReason(),
            entry.getErrorMessage(),
            entry.getCreatedAt(),
            entry.getRetryCount()
        );
    }
    
    /**
     * Get dead letter queue statistics
     */
    public Map<String, Object> getDeadLetterQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long totalEntries = getDeadLetterQueueSize();
            List<DeadLetterEntry> entries = getDeadLetterQueueEntries(100, 0);
            
            // Count failure reasons
            Map<String, Long> reasonCounts = entries.stream()
                .collect(groupingBy(DeadLetterEntry::getFailureReason, counting()));
            
            stats.put("totalEntries", totalEntries);
            stats.put("failureReasonCounts", reasonCounts);
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error getting DLQ statistics", e);
            stats.put("totalEntries", 0L);
            stats.put("failureReasonCounts", Map.of());
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Retry job from dead letter queue
     */
    public boolean retryFromDeadLetterQueue(Long jobId, boolean resetRetryCount) {
        logger.info("Retrying job {} from DLQ, resetRetryCount={}", jobId, resetRetryCount);
        
        try {
            // Remove from DLQ and get job details
            if (removeFromDeadLetterQueue(jobId)) {
                // Get job from database
                Optional<Job> jobOpt = jobRepository.findById(jobId);
                if (jobOpt.isPresent()) {
                    Job job = jobOpt.get();
                    
                    // Reset retry count if requested
                    if (resetRetryCount) {
                        job.setRetryCount(0);
                    }
                    
                    // Reset job for retry
                    job.setStatus(JobStatus.PENDING);
                    job.setAssignedWorkerId(null);
                    job.setAssignedWorkerName(null);
                    job.setWorkerHost(null);
                    job.setWorkerPort(null);
                    job.setWorkerAssignedAt(null);
                    job.setStartedAt(null);
                    job.setCompletedAt(null);
                    job.setErrorMessage(null);
                    job.setUpdatedAt(LocalDateTime.now());
                    
                    jobRepository.save(job);
                    
                    logger.info("Successfully retried job {} from DLQ", jobId);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error retrying job {} from DLQ", jobId, e);
            return false;
        }
    }
}
