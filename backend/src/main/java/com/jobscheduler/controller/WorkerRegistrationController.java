package com.jobscheduler.controller;

import com.jobscheduler.model.Worker;
import com.jobscheduler.service.WorkerRegistrationService;
import com.jobscheduler.service.WorkerRegistrationService.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Worker Registration Controller
 * Provides comprehensive REST API for worker lifecycle management
 */
@RestController
@RequestMapping("/api/v1/worker-registration")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkerRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRegistrationController.class);

    @Autowired
    private WorkerRegistrationService workerRegistrationService;

    /**
     * Register a new worker or update existing worker
     */
    @PostMapping("/register")
    public ResponseEntity<WorkerRegistrationResponse> registerWorker(
            @Valid @RequestBody WorkerRegistrationRequest request) {
        
        logger.info("Processing worker registration request for: {}", request.getWorkerId());
        
        try {
            WorkerRegistrationResult result = workerRegistrationService.registerWorker(request);
            
            if (result.isSuccess()) {
                logger.info("Successfully registered worker: {}", request.getWorkerId());
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new WorkerRegistrationResponse(true, result.getMessage(), result.getWorker(), null));
            } else {
                logger.warn("Failed to register worker {}: {}", request.getWorkerId(), result.getMessage());
                return ResponseEntity.badRequest()
                    .body(new WorkerRegistrationResponse(false, result.getMessage(), null, "REGISTRATION_FAILED"));
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during worker registration for {}: {}", 
                        request.getWorkerId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new WorkerRegistrationResponse(false, 
                    "Internal server error during registration", null, "INTERNAL_ERROR"));
        }
    }

    /**
     * Process worker heartbeat
     */
    @PostMapping("/{workerId}/heartbeat")
    public ResponseEntity<HeartbeatResponse> processHeartbeat(
            @PathVariable String workerId,
            @RequestBody(required = false) HeartbeatRequest request) {
        
        logger.debug("Processing heartbeat for worker: {}", workerId);
        
        try {
            // Use empty request if none provided
            if (request == null) {
                request = new HeartbeatRequest();
            }
            
            HeartbeatResult result = workerRegistrationService.processHeartbeat(workerId, request);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(new HeartbeatResponse(true, result.getMessage(), 
                    result.getWorker(), System.currentTimeMillis()));
            } else {
                logger.warn("Failed to process heartbeat for worker {}: {}", workerId, result.getMessage());
                return ResponseEntity.badRequest()
                    .body(new HeartbeatResponse(false, result.getMessage(), null, System.currentTimeMillis()));
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during heartbeat processing for {}: {}", 
                        workerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new HeartbeatResponse(false, "Internal server error during heartbeat processing", 
                    null, System.currentTimeMillis()));
        }
    }

    /**
     * Deregister a worker
     */
    @PostMapping("/{workerId}/deregister")
    public ResponseEntity<DeregistrationResponse> deregisterWorker(
            @PathVariable String workerId,
            @RequestBody(required = false) DeregistrationRequest request) {
        
        logger.info("Processing worker deregistration for: {}", workerId);
        
        try {
            // Use default request if none provided
            if (request == null) {
                request = new DeregistrationRequest(false, "Manual deregistration");
            }
            
            DeregistrationResult result = workerRegistrationService.deregisterWorker(workerId, request);
            
            if (result.isSuccess()) {
                logger.info("Successfully deregistered worker: {}", workerId);
                return ResponseEntity.ok(new DeregistrationResponse(true, result.getMessage(), result.getWorker()));
            } else {
                logger.warn("Failed to deregister worker {}: {}", workerId, result.getMessage());
                return ResponseEntity.badRequest()
                    .body(new DeregistrationResponse(false, result.getMessage(), result.getWorker()));
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during worker deregistration for {}: {}", 
                        workerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DeregistrationResponse(false, "Internal server error during deregistration", null));
        }
    }

    /**
     * Force deregister a worker (even with active jobs)
     */
    @PostMapping("/{workerId}/force-deregister")
    public ResponseEntity<DeregistrationResponse> forceDeregisterWorker(
            @PathVariable String workerId,
            @RequestParam(required = false) String reason) {
        
        logger.warn("Processing FORCE deregistration for worker: {}", workerId);
        
        try {
            DeregistrationRequest request = new DeregistrationRequest(true, 
                reason != null ? reason : "Force deregistration");
            
            DeregistrationResult result = workerRegistrationService.deregisterWorker(workerId, request);
            
            if (result.isSuccess()) {
                logger.warn("Successfully FORCE deregistered worker: {}", workerId);
                return ResponseEntity.ok(new DeregistrationResponse(true, result.getMessage(), result.getWorker()));
            } else {
                return ResponseEntity.badRequest()
                    .body(new DeregistrationResponse(false, result.getMessage(), result.getWorker()));
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during force deregistration for {}: {}", 
                        workerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DeregistrationResponse(false, "Internal server error during force deregistration", null));
        }
    }

    /**
     * Get health report for a specific worker
     */
    @GetMapping("/{workerId}/health")
    public ResponseEntity<WorkerHealthReport> getWorkerHealth(@PathVariable String workerId) {
        logger.debug("Getting health report for worker: {}", workerId);
        
        try {
            WorkerHealthReport report = workerRegistrationService.getWorkerHealthReport(workerId);
            
            if (report.getWorker() != null) {
                return ResponseEntity.ok(report);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting health report for worker {}: {}", workerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get system-wide health statistics
     */
    @GetMapping("/health/system")
    public ResponseEntity<SystemHealthStatistics> getSystemHealth() {
        logger.debug("Getting system health statistics");
        
        try {
            SystemHealthStatistics stats = workerRegistrationService.getSystemHealthStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error getting system health statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get health summary for all workers
     */
    @GetMapping("/health/summary")
    public ResponseEntity<HealthSummaryResponse> getHealthSummary() {
        logger.debug("Getting health summary for all workers");
        
        try {
            SystemHealthStatistics systemStats = workerRegistrationService.getSystemHealthStatistics();
            
            HealthSummaryResponse summary = new HealthSummaryResponse(
                systemStats.getTotalWorkers(),
                systemStats.getActiveWorkers(),
                systemStats.getFailedWorkers(),
                systemStats.getSystemHealthPercentage(),
                systemStats.getTotalCapacity(),
                systemStats.getUsedCapacity(),
                systemStats.getAvailableCapacity(),
                systemStats.getTimestamp()
            );
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            logger.error("Error getting health summary: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Trigger manual health check for all workers
     */
    @PostMapping("/health/check")
    public ResponseEntity<Map<String, Object>> triggerHealthCheck() {
        logger.info("Triggering manual health check for all workers");
        
        try {
            // This would typically trigger an immediate health check
            // For now, we'll use the existing monitoring
            CompletableFuture.runAsync(() -> {
                workerRegistrationService.performHealthMonitoring();
            });
            
            return ResponseEntity.ok(Map.of(
                "message", "Health check triggered",
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("Error triggering health check: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to trigger health check"));
        }
    }

    /**
     * Trigger manual cleanup of failed workers
     */
    @PostMapping("/cleanup/failed")
    public ResponseEntity<Map<String, Object>> triggerFailedWorkerCleanup() {
        logger.info("Triggering manual cleanup of failed workers");
        
        try {
            CompletableFuture.runAsync(() -> {
                workerRegistrationService.performAutomaticCleanup();
            });
            
            return ResponseEntity.ok(Map.of(
                "message", "Failed worker cleanup triggered",
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("Error triggering failed worker cleanup: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to trigger cleanup"));
        }
    }

    /**
     * Get registration status for a worker
     */
    @GetMapping("/{workerId}/status")
    public ResponseEntity<WorkerStatusResponse> getWorkerStatus(@PathVariable String workerId) {
        logger.debug("Getting registration status for worker: {}", workerId);
        
        try {
            WorkerHealthReport report = workerRegistrationService.getWorkerHealthReport(workerId);
            
            if (report.getWorker() != null) {
                Worker worker = report.getWorker();
                WorkerStatusResponse response = new WorkerStatusResponse(
                    workerId,
                    worker.getStatus(),
                    worker.getLastHeartbeat(),
                    report.isHealthy(),
                    report.getDescription(),
                    worker.getCurrentJobCount(),
                    worker.getMaxConcurrentJobs(),
                    worker.getAvailableCapacity(),
                    worker.getLoadPercentage(),
                    worker.getSuccessRate()
                );
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Error getting worker status for {}: {}", workerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Batch worker registration
     */
    @PostMapping("/register/batch")
    public ResponseEntity<BatchRegistrationResponse> registerWorkersBatch(
            @Valid @RequestBody List<WorkerRegistrationRequest> requests) {
        
        logger.info("Processing batch worker registration for {} workers", requests.size());
        
        try {
            BatchRegistrationResponse response = new BatchRegistrationResponse();
            
            for (WorkerRegistrationRequest request : requests) {
                try {
                    WorkerRegistrationResult result = workerRegistrationService.registerWorker(request);
                    
                    if (result.isSuccess()) {
                        response.addSuccess(request.getWorkerId(), result.getWorker());
                    } else {
                        response.addFailure(request.getWorkerId(), result.getMessage());
                    }
                    
                } catch (Exception e) {
                    logger.error("Error in batch registration for worker {}: {}", 
                                request.getWorkerId(), e.getMessage());
                    response.addFailure(request.getWorkerId(), "Registration failed: " + e.getMessage());
                }
            }
            
            logger.info("Batch registration completed: {} successful, {} failed", 
                       response.getSuccessful().size(), response.getFailed().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during batch worker registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BatchRegistrationResponse());
        }
    }

    /**
     * Get registration configuration and limits
     */
    @GetMapping("/config")
    public ResponseEntity<RegistrationConfigResponse> getRegistrationConfig() {
        logger.debug("Getting registration configuration");
        
        try {
            RegistrationConfigResponse config = new RegistrationConfigResponse(
                5,    // heartbeatTimeoutMinutes
                10,   // failedThresholdMinutes  
                15,   // cleanupIntervalMinutes
                3,    // maxRegistrationAttempts
                100,  // maxConcurrentJobs
                2.0,  // maxLoadFactor
                1,    // minCapacity
                30    // heartbeatGracePeriodSeconds
            );
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            logger.error("Error getting registration config: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Response DTOs

    public static class WorkerRegistrationResponse {
        private final boolean success;
        private final String message;
        private final Worker worker;
        private final String errorCode;

        public WorkerRegistrationResponse(boolean success, String message, Worker worker, String errorCode) {
            this.success = success;
            this.message = message;
            this.worker = worker;
            this.errorCode = errorCode;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
        public String getErrorCode() { return errorCode; }
    }

    public static class HeartbeatResponse {
        private final boolean success;
        private final String message;
        private final Worker worker;
        private final long timestamp;

        public HeartbeatResponse(boolean success, String message, Worker worker, long timestamp) {
            this.success = success;
            this.message = message;
            this.worker = worker;
            this.timestamp = timestamp;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
        public long getTimestamp() { return timestamp; }
    }

    public static class DeregistrationResponse {
        private final boolean success;
        private final String message;
        private final Worker worker;

        public DeregistrationResponse(boolean success, String message, Worker worker) {
            this.success = success;
            this.message = message;
            this.worker = worker;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
    }

    public static class HealthSummaryResponse {
        private final long totalWorkers;
        private final long activeWorkers;
        private final long failedWorkers;
        private final double systemHealthPercentage;
        private final int totalCapacity;
        private final int usedCapacity;
        private final int availableCapacity;
        private final java.time.LocalDateTime timestamp;

        public HealthSummaryResponse(long totalWorkers, long activeWorkers, long failedWorkers,
                                   double systemHealthPercentage, int totalCapacity, int usedCapacity,
                                   int availableCapacity, java.time.LocalDateTime timestamp) {
            this.totalWorkers = totalWorkers;
            this.activeWorkers = activeWorkers;
            this.failedWorkers = failedWorkers;
            this.systemHealthPercentage = systemHealthPercentage;
            this.totalCapacity = totalCapacity;
            this.usedCapacity = usedCapacity;
            this.availableCapacity = availableCapacity;
            this.timestamp = timestamp;
        }

        // Getters
        public long getTotalWorkers() { return totalWorkers; }
        public long getActiveWorkers() { return activeWorkers; }
        public long getFailedWorkers() { return failedWorkers; }
        public double getSystemHealthPercentage() { return systemHealthPercentage; }
        public int getTotalCapacity() { return totalCapacity; }
        public int getUsedCapacity() { return usedCapacity; }
        public int getAvailableCapacity() { return availableCapacity; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class WorkerStatusResponse {
        private final String workerId;
        private final Worker.WorkerStatus status;
        private final java.time.LocalDateTime lastHeartbeat;
        private final boolean healthy;
        private final String healthDescription;
        private final Integer currentJobCount;
        private final Integer maxConcurrentJobs;
        private final Integer availableCapacity;
        private final Double loadPercentage;
        private final Double successRate;

        public WorkerStatusResponse(String workerId, Worker.WorkerStatus status, 
                                  java.time.LocalDateTime lastHeartbeat, boolean healthy, 
                                  String healthDescription, Integer currentJobCount, 
                                  Integer maxConcurrentJobs, Integer availableCapacity,
                                  Double loadPercentage, Double successRate) {
            this.workerId = workerId;
            this.status = status;
            this.lastHeartbeat = lastHeartbeat;
            this.healthy = healthy;
            this.healthDescription = healthDescription;
            this.currentJobCount = currentJobCount;
            this.maxConcurrentJobs = maxConcurrentJobs;
            this.availableCapacity = availableCapacity;
            this.loadPercentage = loadPercentage;
            this.successRate = successRate;
        }

        // Getters
        public String getWorkerId() { return workerId; }
        public Worker.WorkerStatus getStatus() { return status; }
        public java.time.LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public boolean isHealthy() { return healthy; }
        public String getHealthDescription() { return healthDescription; }
        public Integer getCurrentJobCount() { return currentJobCount; }
        public Integer getMaxConcurrentJobs() { return maxConcurrentJobs; }
        public Integer getAvailableCapacity() { return availableCapacity; }
        public Double getLoadPercentage() { return loadPercentage; }
        public Double getSuccessRate() { return successRate; }
    }

    public static class BatchRegistrationResponse {
        private final Map<String, Worker> successful = new java.util.HashMap<>();
        private final Map<String, String> failed = new java.util.HashMap<>();

        public void addSuccess(String workerId, Worker worker) {
            successful.put(workerId, worker);
        }

        public void addFailure(String workerId, String reason) {
            failed.put(workerId, reason);
        }

        public Map<String, Worker> getSuccessful() { return successful; }
        public Map<String, String> getFailed() { return failed; }
        public int getTotalProcessed() { return successful.size() + failed.size(); }
        public int getSuccessCount() { return successful.size(); }
        public int getFailureCount() { return failed.size(); }
    }

    public static class RegistrationConfigResponse {
        private final int heartbeatTimeoutMinutes;
        private final int failedThresholdMinutes;
        private final int cleanupIntervalMinutes;
        private final int maxRegistrationAttempts;
        private final int maxConcurrentJobs;
        private final double maxLoadFactor;
        private final int minCapacity;
        private final int heartbeatGracePeriodSeconds;

        public RegistrationConfigResponse(int heartbeatTimeoutMinutes, int failedThresholdMinutes,
                                        int cleanupIntervalMinutes, int maxRegistrationAttempts,
                                        int maxConcurrentJobs, double maxLoadFactor,
                                        int minCapacity, int heartbeatGracePeriodSeconds) {
            this.heartbeatTimeoutMinutes = heartbeatTimeoutMinutes;
            this.failedThresholdMinutes = failedThresholdMinutes;
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
            this.maxRegistrationAttempts = maxRegistrationAttempts;
            this.maxConcurrentJobs = maxConcurrentJobs;
            this.maxLoadFactor = maxLoadFactor;
            this.minCapacity = minCapacity;
            this.heartbeatGracePeriodSeconds = heartbeatGracePeriodSeconds;
        }

        // Getters
        public int getHeartbeatTimeoutMinutes() { return heartbeatTimeoutMinutes; }
        public int getFailedThresholdMinutes() { return failedThresholdMinutes; }
        public int getCleanupIntervalMinutes() { return cleanupIntervalMinutes; }
        public int getMaxRegistrationAttempts() { return maxRegistrationAttempts; }
        public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
        public double getMaxLoadFactor() { return maxLoadFactor; }
        public int getMinCapacity() { return minCapacity; }
        public int getHeartbeatGracePeriodSeconds() { return heartbeatGracePeriodSeconds; }
    }
}
