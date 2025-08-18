package com.jobscheduler.service;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import com.jobscheduler.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive Worker Registration Service
 * Handles worker lifecycle, health monitoring, and automatic cleanup
 */
@Service
@Transactional
public class WorkerRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRegistrationService.class);

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CacheService cacheService;

    // Configuration constants
    private static final int DEFAULT_HEARTBEAT_TIMEOUT_MINUTES = 5;
    private static final int DEFAULT_FAILED_THRESHOLD_MINUTES = 10;
    private static final int DEFAULT_CLEANUP_INTERVAL_MINUTES = 15;
    private static final int MAX_REGISTRATION_ATTEMPTS = 3;
    private static final int HEARTBEAT_GRACE_PERIOD_SECONDS = 30;

    // In-memory tracking
    private final ConcurrentMap<String, WorkerHealthStatus> workerHealthMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> workerHeartbeatCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> lastHealthCheckMap = new ConcurrentHashMap<>();

    // Registration tracking
    private final ConcurrentMap<String, Integer> registrationAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> lastRegistrationAttempt = new ConcurrentHashMap<>();

    /**
     * Register a new worker with comprehensive validation and setup
     */
    public WorkerRegistrationResult registerWorker(WorkerRegistrationRequest request) {
        String workerId = request.getWorkerId();
        logger.info("Attempting to register worker: {}", workerId);

        // Validate registration attempt
        if (!canAttemptRegistration(workerId)) {
            String message = "Too many registration attempts. Please wait before retrying.";
            logger.warn("Registration blocked for worker {}: {}", workerId, message);
            return new WorkerRegistrationResult(false, message, null);
        }

        try {
            // Check if worker already exists
            Optional<Worker> existingWorker = workerRepository.findByWorkerId(workerId);
            Worker worker;

            if (existingWorker.isPresent()) {
                worker = existingWorker.get();
                logger.info("Updating existing worker registration: {}", workerId);
                updateWorkerFromRequest(worker, request);
            } else {
                logger.info("Creating new worker registration: {}", workerId);
                worker = createWorkerFromRequest(request);
            }

            // Validate worker configuration
            WorkerValidationResult validation = validateWorkerConfiguration(worker);
            if (!validation.isValid()) {
                logger.error("Worker validation failed for {}: {}", workerId, validation.getMessage());
                incrementRegistrationAttempt(workerId);
                return new WorkerRegistrationResult(false, validation.getMessage(), null);
            }

            // Set initial status and timestamp
            worker.setStatus(WorkerStatus.ACTIVE);
            worker.updateHeartbeat();
            worker.updateAvailableCapacity();

            // Save worker
            Worker savedWorker = workerRepository.save(worker);

            // Initialize health tracking
            initializeWorkerHealthTracking(workerId);

            // Cache worker information
            cacheService.cacheWorker(workerId, savedWorker, 600); // 10 minutes cache

            // Reset registration attempts on success
            registrationAttempts.remove(workerId);
            lastRegistrationAttempt.remove(workerId);

            logger.info("Successfully registered worker: {} with capacity: {}", 
                       workerId, savedWorker.getMaxConcurrentJobs());

            return new WorkerRegistrationResult(true, "Worker registered successfully", savedWorker);

        } catch (Exception e) {
            logger.error("Failed to register worker {}: {}", workerId, e.getMessage(), e);
            incrementRegistrationAttempt(workerId);
            return new WorkerRegistrationResult(false, "Registration failed: " + e.getMessage(), null);
        }
    }

    /**
     * Process worker heartbeat with health status updates
     */
    public HeartbeatResult processHeartbeat(String workerId, HeartbeatRequest request) {
        try {
            Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
            if (workerOpt.isEmpty()) {
                logger.warn("Heartbeat received from unregistered worker: {}", workerId);
                return new HeartbeatResult(false, "Worker not registered", null);
            }

            Worker worker = workerOpt.get();

            // Update heartbeat timestamp
            worker.updateHeartbeat();

            // Update worker status from heartbeat
            if (request.getStatus() != null) {
                worker.setStatus(request.getStatus());
            }

            // Update capacity information if provided
            if (request.getCurrentJobCount() != null) {
                worker.setCurrentJobCount(request.getCurrentJobCount());
            }

            if (request.getAvailableCapacity() != null) {
                worker.setAvailableCapacity(request.getAvailableCapacity());
            } else {
                worker.updateAvailableCapacity();
            }

            // Update performance metrics if provided
            if (request.getCpuUsage() != null || request.getMemoryUsage() != null) {
                updateWorkerPerformanceMetrics(worker, request);
            }

            // Save updated worker
            Worker savedWorker = workerRepository.save(worker);

            // Update health tracking
            updateWorkerHealthStatus(workerId, request);

            // Update cache
            cacheService.cacheWorker(workerId, savedWorker, 300); // 5 minutes cache

            // Increment heartbeat counter
            workerHeartbeatCounts.computeIfAbsent(workerId, k -> new AtomicLong(0)).incrementAndGet();

            logger.debug("Processed heartbeat for worker: {} - Status: {}, Capacity: {}/{}", 
                        workerId, worker.getStatus(), worker.getCurrentJobCount(), worker.getMaxConcurrentJobs());

            return new HeartbeatResult(true, "Heartbeat processed successfully", savedWorker);

        } catch (Exception e) {
            logger.error("Failed to process heartbeat for worker {}: {}", workerId, e.getMessage(), e);
            return new HeartbeatResult(false, "Heartbeat processing failed: " + e.getMessage(), null);
        }
    }

    /**
     * Deregister a worker gracefully
     */
    public DeregistrationResult deregisterWorker(String workerId, DeregistrationRequest request) {
        try {
            Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
            if (workerOpt.isEmpty()) {
                logger.warn("Attempted to deregister non-existent worker: {}", workerId);
                return new DeregistrationResult(false, "Worker not found", null);
            }

            Worker worker = workerOpt.get();

            // Check if worker has active jobs
            if (worker.getCurrentJobCount() > 0 && !request.isForceDeregister()) {
                String message = String.format("Worker %s has %d active jobs. Use force deregister if needed.", 
                                              workerId, worker.getCurrentJobCount());
                logger.warn(message);
                return new DeregistrationResult(false, message, worker);
            }

            // Set worker status to inactive
            worker.setStatus(WorkerStatus.INACTIVE);
            
            // Clear job assignments if forced
            if (request.isForceDeregister() && worker.getCurrentJobCount() > 0) {
                logger.warn("Force deregistering worker {} with {} active jobs", 
                           workerId, worker.getCurrentJobCount());
                worker.clearCurrentJobs();
            }

            // Update timestamp
            worker.setUpdatedAt(LocalDateTime.now());

            // Save worker
            Worker savedWorker = workerRepository.save(worker);

            // Remove from health tracking
            cleanupWorkerTracking(workerId);

            // Remove from cache
            cacheService.evictWorkerFromCache(workerId);

            logger.info("Successfully deregistered worker: {} (force: {})", 
                       workerId, request.isForceDeregister());

            return new DeregistrationResult(true, "Worker deregistered successfully", savedWorker);

        } catch (Exception e) {
            logger.error("Failed to deregister worker {}: {}", workerId, e.getMessage(), e);
            return new DeregistrationResult(false, "Deregistration failed: " + e.getMessage(), null);
        }
    }

    /**
     * Get comprehensive health status for a worker
     */
    public WorkerHealthReport getWorkerHealthReport(String workerId) {
        try {
            Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
            if (workerOpt.isEmpty()) {
                return new WorkerHealthReport(workerId, false, "Worker not found", null, null);
            }

            Worker worker = workerOpt.get();
            WorkerHealthStatus healthStatus = workerHealthMap.get(workerId);
            
            // Calculate health metrics
            boolean isHealthy = isWorkerHealthy(worker, healthStatus);
            String healthDescription = generateHealthDescription(worker, healthStatus);
            
            // Get heartbeat statistics
            Long heartbeatCount = workerHeartbeatCounts.getOrDefault(workerId, new AtomicLong(0)).get();
            LocalDateTime lastHealthCheck = lastHealthCheckMap.get(workerId);
            
            WorkerHealthMetrics metrics = new WorkerHealthMetrics(
                isHealthy,
                worker.getLastHeartbeat(),
                lastHealthCheck,
                heartbeatCount,
                calculateUptimePercentage(workerId),
                worker.getLoadPercentage(),
                worker.getSuccessRate(),
                healthStatus != null ? healthStatus.getCpuUsage() : null,
                healthStatus != null ? healthStatus.getMemoryUsage() : null
            );

            return new WorkerHealthReport(workerId, isHealthy, healthDescription, worker, metrics);

        } catch (Exception e) {
            logger.error("Failed to generate health report for worker {}: {}", workerId, e.getMessage(), e);
            return new WorkerHealthReport(workerId, false, "Health check failed: " + e.getMessage(), null, null);
        }
    }

    /**
     * Get system-wide worker health statistics
     */
    public SystemHealthStatistics getSystemHealthStatistics() {
        try {
            List<Worker> allWorkers = workerRepository.findAll();
            
            long totalWorkers = allWorkers.size();
            long activeWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.ACTIVE).count();
            long idleWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.ACTIVE && w.getCurrentJobCount() == 0).count();
            long busyWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.BUSY).count();
            long failedWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.ERROR).count();
            long maintenanceWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.MAINTENANCE).count();

            // Calculate capacity metrics
            int totalCapacity = allWorkers.stream().mapToInt(w -> w.getMaxConcurrentJobs() != null ? w.getMaxConcurrentJobs() : 0).sum();
            int usedCapacity = allWorkers.stream().mapToInt(w -> w.getCurrentJobCount() != null ? w.getCurrentJobCount() : 0).sum();
            int availableCapacity = totalCapacity - usedCapacity;

            // Calculate health metrics
            List<Worker> unhealthyWorkers = findUnhealthyWorkers();
            double systemHealthPercentage = totalWorkers > 0 ? 
                ((double)(totalWorkers - unhealthyWorkers.size()) / totalWorkers) * 100.0 : 100.0;

            return new SystemHealthStatistics(
                totalWorkers, activeWorkers, idleWorkers, busyWorkers, failedWorkers, maintenanceWorkers,
                totalCapacity, usedCapacity, availableCapacity,
                systemHealthPercentage, unhealthyWorkers.size(),
                LocalDateTime.now()
            );

        } catch (Exception e) {
            logger.error("Failed to generate system health statistics: {}", e.getMessage(), e);
            return new SystemHealthStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, LocalDateTime.now());
        }
    }

    /**
     * Scheduled health monitoring and cleanup
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void performHealthMonitoring() {
        logger.debug("Starting scheduled health monitoring");
        
        try {
            // Update health status for all workers
            List<Worker> allWorkers = workerRepository.findAll();
            for (Worker worker : allWorkers) {
                if (worker.getStatus() == WorkerStatus.ACTIVE) {
                    updateWorkerHealthCheck(worker);
                }
            }

            // Identify and handle failed workers
            handleFailedWorkers();

            // Cleanup stale tracking data
            cleanupStaleTrackingData();

            logger.debug("Completed scheduled health monitoring");

        } catch (Exception e) {
            logger.error("Error during scheduled health monitoring: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled cleanup of failed workers
     */
    @Scheduled(fixedRate = 900000) // Run every 15 minutes
    public void performAutomaticCleanup() {
        logger.info("Starting automatic worker cleanup");
        
        try {
            List<Worker> failedWorkers = findFailedWorkers();
            int cleanedUp = 0;

            for (Worker worker : failedWorkers) {
                if (shouldCleanupWorker(worker)) {
                    cleanupFailedWorker(worker);
                    cleanedUp++;
                }
            }

            if (cleanedUp > 0) {
                logger.info("Cleaned up {} failed workers", cleanedUp);
            } else {
                logger.debug("No workers required cleanup");
            }

        } catch (Exception e) {
            logger.error("Error during automatic worker cleanup: {}", e.getMessage(), e);
        }
    }

    // Helper methods

    private boolean canAttemptRegistration(String workerId) {
        Integer attempts = registrationAttempts.get(workerId);
        LocalDateTime lastAttempt = lastRegistrationAttempt.get(workerId);

        if (attempts == null || attempts < MAX_REGISTRATION_ATTEMPTS) {
            return true;
        }

        // Allow retry after 1 hour
        return lastAttempt != null && 
               Duration.between(lastAttempt, LocalDateTime.now()).toMinutes() >= 60;
    }

    private void incrementRegistrationAttempt(String workerId) {
        registrationAttempts.merge(workerId, 1, Integer::sum);
        lastRegistrationAttempt.put(workerId, LocalDateTime.now());
    }

    private Worker createWorkerFromRequest(WorkerRegistrationRequest request) {
        Worker worker = new Worker(request.getWorkerId(), request.getName());
        updateWorkerFromRequest(worker, request);
        return worker;
    }

    private void updateWorkerFromRequest(Worker worker, WorkerRegistrationRequest request) {
        worker.setHostName(request.getHostName());
        worker.setHostAddress(request.getHostAddress());
        worker.setPort(request.getPort());
        worker.setMaxConcurrentJobs(request.getMaxConcurrentJobs());
        worker.setCapabilities(request.getCapabilities());
        worker.setTags(request.getTags());
        worker.setVersion(request.getVersion());
        worker.setPriorityThreshold(request.getPriorityThreshold());
        worker.setWorkerLoadFactor(request.getWorkerLoadFactor());
    }

    private WorkerValidationResult validateWorkerConfiguration(Worker worker) {
        List<String> errors = new ArrayList<>();

        if (worker.getWorkerId() == null || worker.getWorkerId().trim().isEmpty()) {
            errors.add("Worker ID is required");
        }

        if (worker.getName() == null || worker.getName().trim().isEmpty()) {
            errors.add("Worker name is required");
        }

        if (worker.getMaxConcurrentJobs() == null || worker.getMaxConcurrentJobs() <= 0) {
            errors.add("Max concurrent jobs must be positive");
        }

        if (worker.getMaxConcurrentJobs() != null && worker.getMaxConcurrentJobs() > 100) {
            errors.add("Max concurrent jobs cannot exceed 100");
        }

        if (worker.getPort() != null && (worker.getPort() < 1 || worker.getPort() > 65535)) {
            errors.add("Port must be between 1 and 65535");
        }

        if (worker.getWorkerLoadFactor() != null && 
            (worker.getWorkerLoadFactor() < 0.1 || worker.getWorkerLoadFactor() > 2.0)) {
            errors.add("Worker load factor must be between 0.1 and 2.0");
        }

        if (errors.isEmpty()) {
            return new WorkerValidationResult(true, "Validation passed");
        } else {
            return new WorkerValidationResult(false, String.join("; ", errors));
        }
    }

    private void initializeWorkerHealthTracking(String workerId) {
        workerHealthMap.put(workerId, new WorkerHealthStatus());
        workerHeartbeatCounts.put(workerId, new AtomicLong(0));
        lastHealthCheckMap.put(workerId, LocalDateTime.now());
    }

    private void updateWorkerHealthStatus(String workerId, HeartbeatRequest request) {
        WorkerHealthStatus healthStatus = workerHealthMap.computeIfAbsent(workerId, k -> new WorkerHealthStatus());
        
        healthStatus.setLastHeartbeat(LocalDateTime.now());
        healthStatus.setHealthy(true);
        
        if (request.getCpuUsage() != null) {
            healthStatus.setCpuUsage(request.getCpuUsage());
        }
        
        if (request.getMemoryUsage() != null) {
            healthStatus.setMemoryUsage(request.getMemoryUsage());
        }

        if (request.getErrorCount() != null) {
            healthStatus.setErrorCount(request.getErrorCount());
        }

        lastHealthCheckMap.put(workerId, LocalDateTime.now());
    }

    private void updateWorkerPerformanceMetrics(Worker worker, HeartbeatRequest request) {
        // In a real implementation, you might store these metrics in a separate table
        // For now, we'll log them and update health tracking
        logger.debug("Performance metrics for worker {}: CPU={}%, Memory={}%", 
                    worker.getWorkerId(), request.getCpuUsage(), request.getMemoryUsage());
    }

    private void updateWorkerHealthCheck(Worker worker) {
        String workerId = worker.getWorkerId();
        
        // Check heartbeat timeout
        if (worker.getLastHeartbeat() != null) {
            Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
            
            if (timeSinceHeartbeat.toMinutes() > DEFAULT_HEARTBEAT_TIMEOUT_MINUTES) {
                markWorkerAsUnhealthy(worker, "Heartbeat timeout");
            }
        }

        lastHealthCheckMap.put(workerId, LocalDateTime.now());
    }

    private void markWorkerAsUnhealthy(Worker worker, String reason) {
        String workerId = worker.getWorkerId();
        logger.warn("Marking worker {} as unhealthy: {}", workerId, reason);
        
        WorkerHealthStatus healthStatus = workerHealthMap.get(workerId);
        if (healthStatus != null) {
            healthStatus.setHealthy(false);
            healthStatus.setLastError(reason);
            healthStatus.setLastErrorTime(LocalDateTime.now());
        }

        // Update worker status if it's still marked as active
        if (worker.getStatus() == WorkerStatus.ACTIVE) {
            worker.setStatus(WorkerStatus.ERROR);
            workerRepository.save(worker);
            cacheService.evictWorkerFromCache(workerId);
        }
    }

    private void handleFailedWorkers() {
        List<Worker> failedWorkers = findFailedWorkers();
        
        for (Worker worker : failedWorkers) {
            Duration timeSinceLastHeartbeat = worker.getLastHeartbeat() != null ?
                Duration.between(worker.getLastHeartbeat(), LocalDateTime.now()) :
                Duration.ofDays(1); // Assume failed if no heartbeat ever

            if (timeSinceLastHeartbeat.toMinutes() > DEFAULT_FAILED_THRESHOLD_MINUTES) {
                if (worker.getStatus() != WorkerStatus.ERROR) {
                    markWorkerAsUnhealthy(worker, "Extended heartbeat timeout");
                }
            }
        }
    }

    private List<Worker> findFailedWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(DEFAULT_FAILED_THRESHOLD_MINUTES);
        return workerRepository.findPotentiallyDeadWorkers(threshold);
    }

    private List<Worker> findUnhealthyWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(DEFAULT_HEARTBEAT_TIMEOUT_MINUTES);
        return workerRepository.findPotentiallyDeadWorkers(threshold);
    }

    private boolean shouldCleanupWorker(Worker worker) {
        // Don't cleanup workers that were active recently
        if (worker.getLastHeartbeat() != null) {
            Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
            return timeSinceHeartbeat.toMinutes() > DEFAULT_CLEANUP_INTERVAL_MINUTES;
        }
        
        // Cleanup workers with no heartbeat that are old
        return worker.getCreatedAt() != null &&
               Duration.between(worker.getCreatedAt(), LocalDateTime.now()).toMinutes() > DEFAULT_CLEANUP_INTERVAL_MINUTES;
    }

    private void cleanupFailedWorker(Worker worker) {
        String workerId = worker.getWorkerId();
        logger.info("Cleaning up failed worker: {}", workerId);
        
        try {
            // Set status to inactive instead of deleting
            worker.setStatus(WorkerStatus.INACTIVE);
            
            // Clear any assigned jobs
            if (worker.getCurrentJobCount() > 0) {
                logger.warn("Clearing {} jobs from failed worker {}", worker.getCurrentJobCount(), workerId);
                worker.clearCurrentJobs();
            }
            
            workerRepository.save(worker);
            
            // Remove from tracking
            cleanupWorkerTracking(workerId);
            
            // Remove from cache
            cacheService.evictWorkerFromCache(workerId);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup worker {}: {}", workerId, e.getMessage(), e);
        }
    }

    private void cleanupWorkerTracking(String workerId) {
        workerHealthMap.remove(workerId);
        workerHeartbeatCounts.remove(workerId);
        lastHealthCheckMap.remove(workerId);
        registrationAttempts.remove(workerId);
        lastRegistrationAttempt.remove(workerId);
    }

    private void cleanupStaleTrackingData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        // Remove stale registration attempts
        lastRegistrationAttempt.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        // Remove stale health checks
        lastHealthCheckMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private boolean isWorkerHealthy(Worker worker, WorkerHealthStatus healthStatus) {
        if (worker.getStatus() == WorkerStatus.ERROR || worker.getStatus() == WorkerStatus.INACTIVE) {
            return false;
        }

        if (worker.getLastHeartbeat() != null) {
            Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
            if (timeSinceHeartbeat.toMinutes() > DEFAULT_HEARTBEAT_TIMEOUT_MINUTES) {
                return false;
            }
        }

        return healthStatus == null || healthStatus.isHealthy();
    }

    private String generateHealthDescription(Worker worker, WorkerHealthStatus healthStatus) {
        if (!isWorkerHealthy(worker, healthStatus)) {
            if (worker.getStatus() == WorkerStatus.ERROR) {
                return "Worker in error state";
            }
            if (worker.getStatus() == WorkerStatus.INACTIVE) {
                return "Worker inactive";
            }
            if (worker.getLastHeartbeat() != null) {
                Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
                if (timeSinceHeartbeat.toMinutes() > DEFAULT_HEARTBEAT_TIMEOUT_MINUTES) {
                    return String.format("Heartbeat timeout (%d minutes)", timeSinceHeartbeat.toMinutes());
                }
            }
            return "Unknown health issue";
        }

        return "Healthy";
    }

    private double calculateUptimePercentage(String workerId) {
        // Simplified uptime calculation based on successful heartbeats
        // In production, you might want to track this more precisely
        Long heartbeatCount = workerHeartbeatCounts.getOrDefault(workerId, new AtomicLong(0)).get();
        LocalDateTime lastHealthCheck = lastHealthCheckMap.get(workerId);
        
        if (lastHealthCheck == null || heartbeatCount == 0) {
            return 0.0;
        }

        // Rough calculation - in production you'd want more sophisticated tracking
        long minutesSinceRegistration = Duration.between(lastHealthCheck, LocalDateTime.now()).toMinutes();
        if (minutesSinceRegistration == 0) {
            return 100.0;
        }

        // Assume heartbeat every 5 minutes for healthy worker
        long expectedHeartbeats = Math.max(1, minutesSinceRegistration / 5);
        return Math.min(100.0, (double) heartbeatCount / expectedHeartbeats * 100.0);
    }

    // Data classes

    public static class WorkerRegistrationRequest {
        private String workerId;
        private String name;
        private String hostName;
        private String hostAddress;
        private Integer port;
        private Integer maxConcurrentJobs = 1;
        private String capabilities;
        private String tags;
        private String version;
        private Integer priorityThreshold = 100;
        private Double workerLoadFactor = 1.0;

        // Constructors
        public WorkerRegistrationRequest() {}

        public WorkerRegistrationRequest(String workerId, String name) {
            this.workerId = workerId;
            this.name = name;
        }

        // Getters and Setters
        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getHostName() { return hostName; }
        public void setHostName(String hostName) { this.hostName = hostName; }

        public String getHostAddress() { return hostAddress; }
        public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public Integer getMaxConcurrentJobs() { return maxConcurrentJobs; }
        public void setMaxConcurrentJobs(Integer maxConcurrentJobs) { this.maxConcurrentJobs = maxConcurrentJobs; }

        public String getCapabilities() { return capabilities; }
        public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public Integer getPriorityThreshold() { return priorityThreshold; }
        public void setPriorityThreshold(Integer priorityThreshold) { this.priorityThreshold = priorityThreshold; }

        public Double getWorkerLoadFactor() { return workerLoadFactor; }
        public void setWorkerLoadFactor(Double workerLoadFactor) { this.workerLoadFactor = workerLoadFactor; }
    }

    public static class HeartbeatRequest {
        private WorkerStatus status;
        private Integer currentJobCount;
        private Integer availableCapacity;
        private Double cpuUsage;
        private Double memoryUsage;
        private Integer errorCount;
        private String message;

        // Constructors
        public HeartbeatRequest() {}

        // Getters and Setters
        public WorkerStatus getStatus() { return status; }
        public void setStatus(WorkerStatus status) { this.status = status; }

        public Integer getCurrentJobCount() { return currentJobCount; }
        public void setCurrentJobCount(Integer currentJobCount) { this.currentJobCount = currentJobCount; }

        public Integer getAvailableCapacity() { return availableCapacity; }
        public void setAvailableCapacity(Integer availableCapacity) { this.availableCapacity = availableCapacity; }

        public Double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(Double cpuUsage) { this.cpuUsage = cpuUsage; }

        public Double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(Double memoryUsage) { this.memoryUsage = memoryUsage; }

        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class DeregistrationRequest {
        private boolean forceDeregister = false;
        private String reason;

        // Constructors
        public DeregistrationRequest() {}

        public DeregistrationRequest(boolean forceDeregister, String reason) {
            this.forceDeregister = forceDeregister;
            this.reason = reason;
        }

        // Getters and Setters
        public boolean isForceDeregister() { return forceDeregister; }
        public void setForceDeregister(boolean forceDeregister) { this.forceDeregister = forceDeregister; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class WorkerRegistrationResult {
        private final boolean success;
        private final String message;
        private final Worker worker;

        public WorkerRegistrationResult(boolean success, String message, Worker worker) {
            this.success = success;
            this.message = message;
            this.worker = worker;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
    }

    public static class HeartbeatResult {
        private final boolean success;
        private final String message;
        private final Worker worker;

        public HeartbeatResult(boolean success, String message, Worker worker) {
            this.success = success;
            this.message = message;
            this.worker = worker;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
    }

    public static class DeregistrationResult {
        private final boolean success;
        private final String message;
        private final Worker worker;

        public DeregistrationResult(boolean success, String message, Worker worker) {
            this.success = success;
            this.message = message;
            this.worker = worker;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Worker getWorker() { return worker; }
    }

    public static class WorkerValidationResult {
        private final boolean valid;
        private final String message;

        public WorkerValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    public static class WorkerHealthStatus {
        private boolean healthy = true;
        private LocalDateTime lastHeartbeat;
        private Double cpuUsage;
        private Double memoryUsage;
        private Integer errorCount = 0;
        private String lastError;
        private LocalDateTime lastErrorTime;

        // Getters and Setters
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

        public Double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(Double cpuUsage) { this.cpuUsage = cpuUsage; }

        public Double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(Double memoryUsage) { this.memoryUsage = memoryUsage; }

        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }

        public LocalDateTime getLastErrorTime() { return lastErrorTime; }
        public void setLastErrorTime(LocalDateTime lastErrorTime) { this.lastErrorTime = lastErrorTime; }
    }

    public static class WorkerHealthMetrics {
        private final boolean healthy;
        private final LocalDateTime lastHeartbeat;
        private final LocalDateTime lastHealthCheck;
        private final Long heartbeatCount;
        private final Double uptimePercentage;
        private final Double loadPercentage;
        private final Double successRate;
        private final Double cpuUsage;
        private final Double memoryUsage;

        public WorkerHealthMetrics(boolean healthy, LocalDateTime lastHeartbeat, LocalDateTime lastHealthCheck,
                                 Long heartbeatCount, Double uptimePercentage, Double loadPercentage,
                                 Double successRate, Double cpuUsage, Double memoryUsage) {
            this.healthy = healthy;
            this.lastHeartbeat = lastHeartbeat;
            this.lastHealthCheck = lastHealthCheck;
            this.heartbeatCount = heartbeatCount;
            this.uptimePercentage = uptimePercentage;
            this.loadPercentage = loadPercentage;
            this.successRate = successRate;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
        }

        // Getters
        public boolean isHealthy() { return healthy; }
        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
        public Long getHeartbeatCount() { return heartbeatCount; }
        public Double getUptimePercentage() { return uptimePercentage; }
        public Double getLoadPercentage() { return loadPercentage; }
        public Double getSuccessRate() { return successRate; }
        public Double getCpuUsage() { return cpuUsage; }
        public Double getMemoryUsage() { return memoryUsage; }
    }

    public static class WorkerHealthReport {
        private final String workerId;
        private final boolean healthy;
        private final String description;
        private final Worker worker;
        private final WorkerHealthMetrics metrics;

        public WorkerHealthReport(String workerId, boolean healthy, String description, 
                                Worker worker, WorkerHealthMetrics metrics) {
            this.workerId = workerId;
            this.healthy = healthy;
            this.description = description;
            this.worker = worker;
            this.metrics = metrics;
        }

        // Getters
        public String getWorkerId() { return workerId; }
        public boolean isHealthy() { return healthy; }
        public String getDescription() { return description; }
        public Worker getWorker() { return worker; }
        public WorkerHealthMetrics getMetrics() { return metrics; }
    }

    public static class SystemHealthStatistics {
        private final long totalWorkers;
        private final long activeWorkers;
        private final long idleWorkers;
        private final long busyWorkers;
        private final long failedWorkers;
        private final long maintenanceWorkers;
        private final int totalCapacity;
        private final int usedCapacity;
        private final int availableCapacity;
        private final double systemHealthPercentage;
        private final long unhealthyWorkers;
        private final LocalDateTime timestamp;

        public SystemHealthStatistics(long totalWorkers, long activeWorkers, long idleWorkers, 
                                    long busyWorkers, long failedWorkers, long maintenanceWorkers,
                                    int totalCapacity, int usedCapacity, int availableCapacity,
                                    double systemHealthPercentage, long unhealthyWorkers, 
                                    LocalDateTime timestamp) {
            this.totalWorkers = totalWorkers;
            this.activeWorkers = activeWorkers;
            this.idleWorkers = idleWorkers;
            this.busyWorkers = busyWorkers;
            this.failedWorkers = failedWorkers;
            this.maintenanceWorkers = maintenanceWorkers;
            this.totalCapacity = totalCapacity;
            this.usedCapacity = usedCapacity;
            this.availableCapacity = availableCapacity;
            this.systemHealthPercentage = systemHealthPercentage;
            this.unhealthyWorkers = unhealthyWorkers;
            this.timestamp = timestamp;
        }

        // Getters
        public long getTotalWorkers() { return totalWorkers; }
        public long getActiveWorkers() { return activeWorkers; }
        public long getIdleWorkers() { return idleWorkers; }
        public long getBusyWorkers() { return busyWorkers; }
        public long getFailedWorkers() { return failedWorkers; }
        public long getMaintenanceWorkers() { return maintenanceWorkers; }
        public int getTotalCapacity() { return totalCapacity; }
        public int getUsedCapacity() { return usedCapacity; }
        public int getAvailableCapacity() { return availableCapacity; }
        public double getSystemHealthPercentage() { return systemHealthPercentage; }
        public long getUnhealthyWorkers() { return unhealthyWorkers; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
