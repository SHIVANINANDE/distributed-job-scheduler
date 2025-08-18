package com.jobscheduler.controller;

import com.jobscheduler.service.BatchJobService;
import com.jobscheduler.service.AdvancedSchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST Controller for advanced scheduling features including batch processing and scheduling policies
 */
@RestController
@RequestMapping("/api/advanced-scheduling")
@CrossOrigin(origins = "*")
public class AdvancedSchedulingController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedSchedulingController.class);
    
    @Autowired
    private BatchJobService batchJobService;
    
    @Autowired
    private AdvancedSchedulingService advancedSchedulingService;
    
    /**
     * Submit batch of jobs
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchJobService.BatchJobResult> submitBatchJobs(
            @Valid @RequestBody BatchJobService.BatchJobRequest request) {
        
        logger.info("Received batch job submission request: batchId={}, jobCount={}", 
                   request.getBatchId(), request.getJobs().size());
        
        try {
            // Validate request
            if (request.getJobs() == null || request.getJobs().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            if (request.getJobs().size() > 1000) { // Reasonable batch size limit
                logger.warn("Batch size exceeds limit: {}", request.getJobs().size());
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            
            BatchJobService.BatchJobResult result = batchJobService.submitBatchJobs(request);
            
            logger.info("Batch job submission completed: batchId={}, created={}, failed={}", 
                       result.getBatchId(), result.getCreatedJobs().size(), result.getErrors().size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error processing batch job submission: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get batch job status
     */
    @GetMapping("/batch/{batchId}/status")
    public ResponseEntity<BatchJobService.BatchJobStatus> getBatchStatus(@PathVariable String batchId) {
        logger.debug("Getting batch status for batchId: {}", batchId);
        
        try {
            BatchJobService.BatchJobStatus status = batchJobService.getBatchStatus(batchId);
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error getting batch status for {}: {}", batchId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Cancel batch jobs
     */
    @DeleteMapping("/batch/{batchId}")
    public ResponseEntity<BatchJobService.BatchCancellationResult> cancelBatch(
            @PathVariable String batchId,
            @RequestParam(defaultValue = "false") boolean force) {
        
        logger.info("Cancelling batch: batchId={}, force={}", batchId, force);
        
        try {
            BatchJobService.BatchCancellationResult result = batchJobService.cancelBatch(batchId, force);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error cancelling batch {}: {}", batchId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create cron schedule
     */
    @PostMapping("/cron-schedule")
    public ResponseEntity<Map<String, Object>> createCronSchedule(@Valid @RequestBody CronScheduleRequest request) {
        logger.info("Creating cron schedule: id={}, expression={}", request.getScheduleId(), request.getCronExpression());
        
        try {
            String scheduleId = advancedSchedulingService.scheduleJobWithCron(
                request.getScheduleId(),
                request.getCronExpression(),
                request.getTimezone(),
                request.getJobTemplate(),
                request.getParameters()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("scheduleId", scheduleId);
            response.put("status", "created");
            response.put("cronExpression", request.getCronExpression());
            response.put("timezone", request.getTimezone());
            response.put("createdAt", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid cron schedule request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid request",
                "message", e.getMessage()
            ));
            
        } catch (Exception e) {
            logger.error("Error creating cron schedule: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "message", "Failed to create cron schedule"
            ));
        }
    }
    
    /**
     * Update cron schedule
     */
    @PutMapping("/cron-schedule/{scheduleId}")
    public ResponseEntity<Map<String, Object>> updateCronSchedule(
            @PathVariable String scheduleId,
            @Valid @RequestBody CronScheduleUpdateRequest request) {
        
        logger.info("Updating cron schedule: id={}", scheduleId);
        
        try {
            boolean updated = advancedSchedulingService.updateCronSchedule(
                scheduleId,
                request.getCronExpression(),
                request.getTimezone(),
                request.getParameters()
            );
            
            if (updated) {
                return ResponseEntity.ok(Map.of(
                    "scheduleId", scheduleId,
                    "status", "updated",
                    "updatedAt", LocalDateTime.now()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error updating cron schedule {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Delete cron schedule
     */
    @DeleteMapping("/cron-schedule/{scheduleId}")
    public ResponseEntity<Map<String, Object>> deleteCronSchedule(@PathVariable String scheduleId) {
        logger.info("Deleting cron schedule: id={}", scheduleId);
        
        try {
            boolean removed = advancedSchedulingService.removeCronSchedule(scheduleId);
            
            if (removed) {
                return ResponseEntity.ok(Map.of(
                    "scheduleId", scheduleId,
                    "status", "deleted",
                    "deletedAt", LocalDateTime.now()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error deleting cron schedule {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all cron schedules
     */
    @GetMapping("/cron-schedules")
    public ResponseEntity<Map<String, AdvancedSchedulingService.CronSchedule>> getCronSchedules() {
        logger.debug("Getting all cron schedules");
        
        try {
            Map<String, AdvancedSchedulingService.CronSchedule> schedules = 
                advancedSchedulingService.getCronSchedules();
            return ResponseEntity.ok(schedules);
            
        } catch (Exception e) {
            logger.error("Error getting cron schedules: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Add resource constraint
     */
    @PostMapping("/resource-constraints")
    public ResponseEntity<Map<String, Object>> addResourceConstraint(@Valid @RequestBody ResourceConstraintRequest request) {
        logger.info("Adding resource constraint: type={}, maxConcurrent={}", 
                   request.getResourceType(), request.getMaxConcurrent());
        
        try {
            advancedSchedulingService.addResourceConstraint(
                request.getResourceType(),
                request.getMaxConcurrent(),
                request.getMetadata()
            );
            
            return ResponseEntity.ok(Map.of(
                "resourceType", request.getResourceType(),
                "maxConcurrent", request.getMaxConcurrent(),
                "status", "created",
                "createdAt", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            logger.error("Error adding resource constraint: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get resource constraints
     */
    @GetMapping("/resource-constraints")
    public ResponseEntity<Map<String, AdvancedSchedulingService.ResourceConstraint>> getResourceConstraints() {
        logger.debug("Getting all resource constraints");
        
        try {
            Map<String, AdvancedSchedulingService.ResourceConstraint> constraints = 
                advancedSchedulingService.getResourceConstraints();
            return ResponseEntity.ok(constraints);
            
        } catch (Exception e) {
            logger.error("Error getting resource constraints: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update priority inheritance policy
     */
    @PutMapping("/priority-inheritance-policy")
    public ResponseEntity<Map<String, Object>> updatePriorityInheritancePolicy(
            @Valid @RequestBody PriorityInheritancePolicyRequest request) {
        
        logger.info("Updating priority inheritance policy: enabled={}, strategy={}", 
                   request.isEnabled(), request.getStrategy());
        
        try {
            AdvancedSchedulingService.PriorityInheritancePolicy policy = 
                new AdvancedSchedulingService.PriorityInheritancePolicy();
            
            policy.setEnabled(request.isEnabled());
            policy.setStrategy(request.getStrategy());
            policy.setMaxDepth(request.getMaxDepth());
            policy.setInheritanceDecay(request.getInheritanceDecay());
            
            advancedSchedulingService.updatePriorityInheritancePolicy(policy);
            
            return ResponseEntity.ok(Map.of(
                "status", "updated",
                "policy", Map.of(
                    "enabled", policy.isEnabled(),
                    "strategy", policy.getStrategy().toString(),
                    "maxDepth", policy.getMaxDepth(),
                    "inheritanceDecay", policy.getInheritanceDecay()
                ),
                "updatedAt", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            logger.error("Error updating priority inheritance policy: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get scheduling statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSchedulingStatistics() {
        logger.debug("Getting scheduling statistics");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get advanced scheduling statistics
            Map<String, Object> advancedStats = advancedSchedulingService.getSchedulingStatistics();
            stats.putAll(advancedStats);
            
            // Get batch processing statistics
            Map<String, Object> batchStats = batchJobService.getBatchProcessingStatistics();
            stats.put("batch_processing", batchStats);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error getting scheduling statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Apply priority inheritance to specific job
     */
    @PostMapping("/jobs/{jobId}/priority-inheritance")
    public ResponseEntity<Map<String, Object>> applyPriorityInheritance(@PathVariable String jobId) {
        logger.info("Applying priority inheritance to job: {}", jobId);
        
        try {
            advancedSchedulingService.applyPriorityInheritance(jobId);
            
            return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "priority inheritance applied",
                "appliedAt", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            logger.error("Error applying priority inheritance to job {}: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // DTO Classes for requests
    
    public static class CronScheduleRequest {
        private String scheduleId;
        private String cronExpression;
        private String timezone;
        private String jobTemplate;
        private Map<String, Object> parameters;
        
        // Getters and setters
        public String getScheduleId() { return scheduleId; }
        public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }
        
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        
        public String getJobTemplate() { return jobTemplate; }
        public void setJobTemplate(String jobTemplate) { this.jobTemplate = jobTemplate; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    public static class CronScheduleUpdateRequest {
        private String cronExpression;
        private String timezone;
        private Map<String, Object> parameters;
        
        // Getters and setters
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    public static class ResourceConstraintRequest {
        private String resourceType;
        private int maxConcurrent;
        private Map<String, Object> metadata;
        
        // Getters and setters
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    public static class PriorityInheritancePolicyRequest {
        private boolean enabled;
        private AdvancedSchedulingService.PriorityInheritancePolicy.InheritanceStrategy strategy;
        private int maxDepth;
        private double inheritanceDecay;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public AdvancedSchedulingService.PriorityInheritancePolicy.InheritanceStrategy getStrategy() { return strategy; }
        public void setStrategy(AdvancedSchedulingService.PriorityInheritancePolicy.InheritanceStrategy strategy) { 
            this.strategy = strategy; 
        }
        
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
        
        public double getInheritanceDecay() { return inheritanceDecay; }
        public void setInheritanceDecay(double inheritanceDecay) { this.inheritanceDecay = inheritanceDecay; }
    }
}
