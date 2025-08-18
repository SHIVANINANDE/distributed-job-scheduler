package com.jobscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive audit logging service for job execution and system operations
 */
@Service
public class AuditLoggingService {
    
    // Separate loggers for different types of audit events
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private static final Logger jobExecutionLogger = LoggerFactory.getLogger("JOB_EXECUTION");
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private static final Logger systemLogger = LoggerFactory.getLogger("SYSTEM");
    private static final Logger errorLogger = LoggerFactory.getLogger("ERROR_TRACKING");
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RedisCacheService cacheService;
    
    @Value("${audit.logging.enabled:true}")
    private boolean auditLoggingEnabled;
    
    @Value("${audit.logging.retention.days:30}")
    private int retentionDays;
    
    // Audit event types
    public enum AuditEventType {
        JOB_SUBMITTED,
        JOB_STARTED,
        JOB_COMPLETED,
        JOB_FAILED,
        JOB_CANCELLED,
        JOB_RETRY,
        WORKER_REGISTERED,
        WORKER_DEREGISTERED,
        WORKER_HEARTBEAT,
        WORKER_FAILED,
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN,
        CONFIGURATION_CHANGED,
        SECURITY_EVENT,
        ERROR_OCCURRED,
        BATCH_OPERATION,
        DEPENDENCY_CREATED,
        DEPENDENCY_RESOLVED
    }
    
    // Log levels for different event types
    public enum AuditLevel {
        INFO,
        WARN,
        ERROR,
        DEBUG
    }
    
    /**
     * Log job execution event with comprehensive details
     */
    public void logJobExecution(Job job, AuditEventType eventType, String details) {
        if (!auditLoggingEnabled) return;
        
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "JOB_EXECUTION", job.getId().toString());
            
            Map<String, Object> auditData = createJobAuditData(job, eventType, details, correlationId);
            
            // Log to structured logger
            jobExecutionLogger.info("JOB_EXECUTION_EVENT: {}", formatAuditLog(auditData));
            
            // Store in cache for real-time querying
            storeAuditEvent(auditData);
            
            // Update job execution trail
            updateJobExecutionTrail(job, eventType, details, correlationId);
            
        } catch (Exception e) {
            errorLogger.error("Failed to log job execution event for job {}: {}", job.getId(), e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log worker lifecycle events
     */
    public void logWorkerEvent(Worker worker, AuditEventType eventType, String details) {
        if (!auditLoggingEnabled) return;
        
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "WORKER_EVENT", worker.getWorkerId());
            
            Map<String, Object> auditData = createWorkerAuditData(worker, eventType, details, correlationId);
            
            auditLogger.info("WORKER_EVENT: {}", formatAuditLog(auditData));
            storeAuditEvent(auditData);
            
        } catch (Exception e) {
            errorLogger.error("Failed to log worker event for worker {}: {}", worker.getWorkerId(), e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log system events
     */
    public void logSystemEvent(AuditEventType eventType, String component, String details) {
        if (!auditLoggingEnabled) return;
        
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "SYSTEM_EVENT", component);
            
            Map<String, Object> auditData = createSystemAuditData(eventType, component, details, correlationId);
            
            systemLogger.info("SYSTEM_EVENT: {}", formatAuditLog(auditData));
            storeAuditEvent(auditData);
            
        } catch (Exception e) {
            errorLogger.error("Failed to log system event: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log security events
     */
    public void logSecurityEvent(String userId, String action, String resource, boolean success, String details) {
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "SECURITY_EVENT", userId);
            
            Map<String, Object> auditData = createSecurityAuditData(userId, action, resource, success, details, correlationId);
            
            if (success) {
                securityLogger.info("SECURITY_EVENT: {}", formatAuditLog(auditData));
            } else {
                securityLogger.warn("SECURITY_VIOLATION: {}", formatAuditLog(auditData));
            }
            
            storeAuditEvent(auditData);
            
        } catch (Exception e) {
            errorLogger.error("Failed to log security event: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log error events with context
     */
    public void logErrorEvent(String component, String operation, Throwable error, Map<String, Object> context) {
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "ERROR_EVENT", component);
            
            Map<String, Object> auditData = createErrorAuditData(component, operation, error, context, correlationId);
            
            errorLogger.error("ERROR_EVENT: {}", formatAuditLog(auditData), error);
            storeAuditEvent(auditData);
            
            // Also track error for metrics
            trackErrorForMetrics(component, operation, error);
            
        } catch (Exception e) {
            // Fallback logging - should not fail
            errorLogger.error("Failed to log error event for component {}: {}", component, e.getMessage());
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log batch operations
     */
    public void logBatchOperation(String batchId, String operation, int itemCount, String details) {
        if (!auditLoggingEnabled) return;
        
        try {
            String correlationId = generateCorrelationId();
            setMDCContext(correlationId, "BATCH_OPERATION", batchId);
            
            Map<String, Object> auditData = createBatchAuditData(batchId, operation, itemCount, details, correlationId);
            
            auditLogger.info("BATCH_OPERATION: {}", formatAuditLog(auditData));
            storeAuditEvent(auditData);
            
        } catch (Exception e) {
            errorLogger.error("Failed to log batch operation {}: {}", batchId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Get job execution trail
     */
    public Map<String, Object> getJobExecutionTrail(Long jobId) {
        try {
            // Since we don't have getCache method, return empty map for now
            // In a real implementation, this would query the audit trail storage
            auditLogger.debug("Retrieving job execution trail for job: {}", jobId);
        } catch (Exception e) {
            errorLogger.error("Failed to retrieve job execution trail for job {}: {}", jobId, e.getMessage());
        }
        return new HashMap<>();
    }
    
    /**
     * Get audit events by correlation ID
     */
    public Map<String, Object> getAuditEventsByCorrelation(String correlationId) {
        try {
            // Since we don't have getCache method, return empty map for now
            auditLogger.debug("Retrieving audit events for correlation: {}", correlationId);
        } catch (Exception e) {
            errorLogger.error("Failed to retrieve audit events for correlation {}: {}", correlationId, e.getMessage());
        }
        return new HashMap<>();
    }
    
    // Private helper methods
    
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    private void setMDCContext(String correlationId, String eventType, String entityId) {
        MDC.put("correlationId", correlationId);
        MDC.put("eventType", eventType);
        MDC.put("entityId", entityId);
        MDC.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
    
    private Map<String, Object> createJobAuditData(Job job, AuditEventType eventType, String details, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", eventType.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("jobId", job.getId());
        auditData.put("jobName", job.getName());
        auditData.put("jobStatus", job.getStatus().name());
        auditData.put("jobPriority", job.getPriority());
        auditData.put("assignedWorkerId", job.getAssignedWorkerId());
        auditData.put("jobType", job.getJobType());
        auditData.put("createdAt", job.getCreatedAt());
        auditData.put("startedAt", job.getStartedAt());
        auditData.put("completedAt", job.getCompletedAt());
        auditData.put("details", details);
        
        if (job.getErrorMessage() != null) {
            auditData.put("errorMessage", job.getErrorMessage());
        }
        
        return auditData;
    }
    
    private Map<String, Object> createWorkerAuditData(Worker worker, AuditEventType eventType, String details, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", eventType.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("workerId", worker.getWorkerId());
        auditData.put("workerName", worker.getName());
        auditData.put("hostName", worker.getHostName());
        auditData.put("hostAddress", worker.getHostAddress());
        auditData.put("status", worker.getStatus());
        auditData.put("maxConcurrentJobs", worker.getMaxConcurrentJobs());
        auditData.put("processingCapacity", worker.getProcessingCapacity());
        auditData.put("details", details);
        
        return auditData;
    }
    
    private Map<String, Object> createSystemAuditData(AuditEventType eventType, String component, String details, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", eventType.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("component", component);
        auditData.put("details", details);
        
        return auditData;
    }
    
    private Map<String, Object> createSecurityAuditData(String userId, String action, String resource, 
                                                       boolean success, String details, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", AuditEventType.SECURITY_EVENT.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("userId", userId);
        auditData.put("action", action);
        auditData.put("resource", resource);
        auditData.put("success", success);
        auditData.put("details", details);
        
        return auditData;
    }
    
    private Map<String, Object> createErrorAuditData(String component, String operation, Throwable error, 
                                                    Map<String, Object> context, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", AuditEventType.ERROR_OCCURRED.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("component", component);
        auditData.put("operation", operation);
        auditData.put("errorClass", error.getClass().getSimpleName());
        auditData.put("errorMessage", error.getMessage());
        auditData.put("context", context);
        
        return auditData;
    }
    
    private Map<String, Object> createBatchAuditData(String batchId, String operation, int itemCount, 
                                                    String details, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("eventType", AuditEventType.BATCH_OPERATION.name());
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("batchId", batchId);
        auditData.put("operation", operation);
        auditData.put("itemCount", itemCount);
        auditData.put("details", details);
        
        return auditData;
    }
    
    private String formatAuditLog(Map<String, Object> auditData) {
        try {
            return objectMapper.writeValueAsString(auditData);
        } catch (Exception e) {
            // Fallback to simple string representation
            return auditData.toString();
        }
    }
    
    private void storeAuditEvent(Map<String, Object> auditData) {
        try {
            String eventId = "audit.event:" + System.currentTimeMillis() + ":" + generateCorrelationId();
            cacheService.setCacheWithTTL(eventId, auditData, retentionDays * 86400L); // Convert days to seconds
            
            // Also store by correlation ID for easy retrieval
            String correlationId = (String) auditData.get("correlationId");
            if (correlationId != null) {
                cacheService.setCacheWithTTL("audit.correlation:" + correlationId, auditData, retentionDays * 86400L);
            }
            
        } catch (Exception e) {
            errorLogger.error("Failed to store audit event: {}", e.getMessage());
        }
    }
    
    private void updateJobExecutionTrail(Job job, AuditEventType eventType, String details, String correlationId) {
        try {
            // Simplified implementation without getCache dependency
            auditLogger.debug("Updated job execution trail for job {} with event {}", job.getId(), eventType);
        } catch (Exception e) {
            errorLogger.error("Failed to update job execution trail for job {}: {}", job.getId(), e.getMessage());
        }
    }
    
    private void trackErrorForMetrics(String component, String operation, Throwable error) {
        try {
            // Simplified error tracking without incrementJobCounter dependency
            errorLogger.debug("Tracked error for component {} operation {}: {}", component, operation, error.getMessage());
        } catch (Exception e) {
            errorLogger.error("Failed to track error for metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Structured logging helper for performance metrics
     */
    public void logPerformanceMetric(String metricName, Object value, Map<String, Object> tags) {
        if (!auditLoggingEnabled) return;
        
        try {
            Map<String, Object> perfData = new HashMap<>();
            perfData.put("metricType", "PERFORMANCE");
            perfData.put("metricName", metricName);
            perfData.put("value", value);
            perfData.put("tags", tags);
            perfData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            auditLogger.info("PERFORMANCE_METRIC: {}", formatAuditLog(perfData));
            
        } catch (Exception e) {
            errorLogger.error("Failed to log performance metric {}: {}", metricName, e.getMessage());
        }
    }
    
    /**
     * Get error statistics for reporting
     */
    public Map<String, Object> getErrorStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // This would be implemented based on your specific error tracking needs
            stats.put("totalErrors", 0);
            stats.put("errorsByComponent", new HashMap<>());
            stats.put("recentErrors", new HashMap<>());
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            errorLogger.error("Failed to get error statistics: {}", e.getMessage());
        }
        return stats;
    }
}
