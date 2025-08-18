package com.jobscheduler.service;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import com.jobscheduler.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Worker Health Monitor Service
 * Provides continuous monitoring and automatic remediation for worker health
 */
@Service
@Transactional
public class WorkerHealthMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerHealthMonitorService.class);

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CacheService cacheService;

    // Configuration from application properties
    @Value("${jobscheduler.worker.heartbeat-timeout-minutes:5}")
    private int heartbeatTimeoutMinutes;

    @Value("${jobscheduler.worker.health-check-interval-minutes:2}")
    private int healthCheckIntervalMinutes;

    @Value("${jobscheduler.worker.cleanup-threshold-minutes:15}")
    private int cleanupThresholdMinutes;

    @Value("${jobscheduler.worker.auto-recovery-enabled:true}")
    private boolean autoRecoveryEnabled;

    @Value("${jobscheduler.worker.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    // Health monitoring state
    private final Map<String, WorkerHealthState> workerHealthStates = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastNotificationSent = new ConcurrentHashMap<>();
    
    // Statistics tracking
    private final AtomicLong totalHealthChecks = new AtomicLong(0);
    private final AtomicLong totalRecoveries = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);

    /**
     * Scheduled health monitoring - runs every 2 minutes by default
     */
    @Scheduled(fixedRateString = "${jobscheduler.worker.health-check-interval-ms:120000}")
    public void performHealthMonitoring() {
        long startTime = System.currentTimeMillis();
        logger.debug("Starting worker health monitoring cycle");
        
        try {
            List<Worker> allWorkers = workerRepository.findAll();
            totalHealthChecks.incrementAndGet();
            
            int healthyWorkers = 0;
            int unhealthyWorkers = 0;
            int recoveredWorkers = 0;
            
            for (Worker worker : allWorkers) {
                HealthCheckResult result = performWorkerHealthCheck(worker);
                
                switch (result.getStatus()) {
                    case HEALTHY:
                        healthyWorkers++;
                        handleHealthyWorker(worker);
                        break;
                    case UNHEALTHY:
                        unhealthyWorkers++;
                        handleUnhealthyWorker(worker, result);
                        break;
                    case RECOVERED:
                        recoveredWorkers++;
                        handleRecoveredWorker(worker);
                        break;
                    case FAILED:
                        handleFailedWorker(worker, result);
                        break;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Health monitoring completed in {}ms - Healthy: {}, Unhealthy: {}, Recovered: {}", 
                        duration, healthyWorkers, unhealthyWorkers, recoveredWorkers);
            
        } catch (Exception e) {
            logger.error("Error during health monitoring: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled cleanup of failed workers - runs every 15 minutes by default
     */
    @Scheduled(fixedRateString = "${jobscheduler.worker.cleanup-interval-ms:900000}")
    public void performFailedWorkerCleanup() {
        logger.info("Starting failed worker cleanup cycle");
        
        try {
            List<Worker> failedWorkers = findFailedWorkers();
            int cleanedUpCount = 0;
            
            for (Worker worker : failedWorkers) {
                if (shouldCleanupWorker(worker)) {
                    CleanupResult result = cleanupFailedWorker(worker);
                    if (result.isSuccess()) {
                        cleanedUpCount++;
                        logger.info("Cleaned up failed worker: {} - {}", worker.getWorkerId(), result.getMessage());
                    } else {
                        logger.warn("Failed to cleanup worker {}: {}", worker.getWorkerId(), result.getMessage());
                    }
                }
            }
            
            if (cleanedUpCount > 0) {
                logger.info("Cleanup completed: {} workers cleaned up", cleanedUpCount);
            }
            
        } catch (Exception e) {
            logger.error("Error during failed worker cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Perform health check for a specific worker
     */
    public HealthCheckResult performWorkerHealthCheck(Worker worker) {
        String workerId = worker.getWorkerId();
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // Get current health state
            WorkerHealthState currentState = workerHealthStates.get(workerId);
            boolean wasHealthy = currentState != null && currentState.isHealthy();
            
            // Perform health checks
            HealthCheckResult result = new HealthCheckResult(workerId);
            
            // Check heartbeat timeout
            boolean heartbeatHealthy = checkHeartbeatHealth(worker, result);
            
            // Check worker status
            boolean statusHealthy = checkWorkerStatus(worker, result);
            
            // Check capacity consistency
            boolean capacityHealthy = checkCapacityConsistency(worker, result);
            
            // Check job assignment consistency
            boolean jobConsistencyHealthy = checkJobAssignmentConsistency(worker, result);
            
            // Overall health determination
            boolean isHealthy = heartbeatHealthy && statusHealthy && capacityHealthy && jobConsistencyHealthy;
            
            if (isHealthy) {
                if (!wasHealthy) {
                    result.setStatus(HealthStatus.RECOVERED);
                    totalRecoveries.incrementAndGet();
                } else {
                    result.setStatus(HealthStatus.HEALTHY);
                }
                
                // Reset consecutive failures
                consecutiveFailures.remove(workerId);
                
            } else {
                result.setStatus(HealthStatus.UNHEALTHY);
                
                // Increment consecutive failures
                long failures = consecutiveFailures.computeIfAbsent(workerId, k -> new AtomicLong(0))
                    .incrementAndGet();
                
                if (failures >= maxConsecutiveFailures) {
                    result.setStatus(HealthStatus.FAILED);
                    totalFailures.incrementAndGet();
                }
            }
            
            // Update health state
            updateWorkerHealthState(workerId, isHealthy, result.getIssues(), now);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error performing health check for worker {}: {}", workerId, e.getMessage(), e);
            return new HealthCheckResult(workerId, HealthStatus.FAILED, 
                Arrays.asList("Health check failed: " + e.getMessage()));
        }
    }

    /**
     * Get comprehensive health statistics
     */
    public HealthMonitoringStatistics getHealthStatistics() {
        try {
            List<Worker> allWorkers = workerRepository.findAll();
            
            long totalWorkers = allWorkers.size();
            long healthyWorkers = workerHealthStates.values().stream()
                .mapToLong(state -> state.isHealthy() ? 1L : 0L).sum();
            long unhealthyWorkers = totalWorkers - healthyWorkers;
            
            // Calculate average response time and other metrics
            double avgResponseTime = workerHealthStates.values().stream()
                .mapToDouble(WorkerHealthState::getLastResponseTime)
                .average().orElse(0.0);
            
            Map<String, Long> statusDistribution = allWorkers.stream()
                .collect(Collectors.groupingBy(
                    w -> w.getStatus().name(),
                    Collectors.counting()
                ));
            
            return new HealthMonitoringStatistics(
                totalWorkers,
                healthyWorkers,
                unhealthyWorkers,
                totalHealthChecks.get(),
                totalRecoveries.get(),
                totalFailures.get(),
                avgResponseTime,
                statusDistribution,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            logger.error("Error generating health statistics: {}", e.getMessage(), e);
            return new HealthMonitoringStatistics(0, 0, 0, 0, 0, 0, 0.0, 
                Collections.emptyMap(), LocalDateTime.now());
        }
    }

    /**
     * Get detailed health report for a specific worker
     */
    public DetailedHealthReport getDetailedHealthReport(String workerId) {
        try {
            Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
            if (workerOpt.isEmpty()) {
                return new DetailedHealthReport(workerId, false, "Worker not found", 
                    Collections.emptyList(), null, null);
            }
            
            Worker worker = workerOpt.get();
            WorkerHealthState healthState = workerHealthStates.get(workerId);
            
            HealthCheckResult lastCheck = performWorkerHealthCheck(worker);
            
            return new DetailedHealthReport(
                workerId,
                lastCheck.getStatus() == HealthStatus.HEALTHY || lastCheck.getStatus() == HealthStatus.RECOVERED,
                "Detailed health analysis completed",
                lastCheck.getIssues(),
                healthState,
                lastCheck
            );
            
        } catch (Exception e) {
            logger.error("Error generating detailed health report for {}: {}", workerId, e.getMessage(), e);
            return new DetailedHealthReport(workerId, false, "Health report generation failed", 
                Arrays.asList("Error: " + e.getMessage()), null, null);
        }
    }

    // Helper methods

    private boolean checkHeartbeatHealth(Worker worker, HealthCheckResult result) {
        if (worker.getLastHeartbeat() == null) {
            result.addIssue("No heartbeat recorded");
            return false;
        }
        
        Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
        if (timeSinceHeartbeat.toMinutes() > heartbeatTimeoutMinutes) {
            result.addIssue(String.format("Heartbeat timeout: %d minutes since last heartbeat", 
                timeSinceHeartbeat.toMinutes()));
            return false;
        }
        
        return true;
    }

    private boolean checkWorkerStatus(Worker worker, HealthCheckResult result) {
        WorkerStatus status = worker.getStatus();
        
        if (status == WorkerStatus.ERROR) {
            result.addIssue("Worker status is ERROR");
            return false;
        }
        
        if (status == WorkerStatus.INACTIVE && worker.getCurrentJobCount() > 0) {
            result.addIssue("Worker inactive but has assigned jobs");
            return false;
        }
        
        return true;
    }

    private boolean checkCapacityConsistency(Worker worker, HealthCheckResult result) {
        Integer currentJobs = worker.getCurrentJobCount();
        Integer maxJobs = worker.getMaxConcurrentJobs();
        
        if (currentJobs == null || maxJobs == null) {
            result.addIssue("Capacity information incomplete");
            return false;
        }
        
        if (currentJobs < 0) {
            result.addIssue("Negative job count detected");
            return false;
        }
        
        if (currentJobs > maxJobs) {
            result.addIssue(String.format("Job count (%d) exceeds capacity (%d)", currentJobs, maxJobs));
            return false;
        }
        
        return true;
    }

    private boolean checkJobAssignmentConsistency(Worker worker, HealthCheckResult result) {
        // This would typically involve checking database consistency
        // For now, we'll do basic validation
        
        if (worker.getStatus() == WorkerStatus.ACTIVE && worker.getCurrentJobCount() == 0) {
            // Check if worker should be marked as IDLE instead
            result.addIssue("Active worker with no jobs - should be IDLE");
            return true; // This is a minor issue, not a health failure
        }
        
        return true;
    }

    private void handleHealthyWorker(Worker worker) {
        String workerId = worker.getWorkerId();
        
        // Update status if needed
        if (worker.getStatus() == WorkerStatus.ERROR) {
            if (autoRecoveryEnabled) {
                logger.info("Auto-recovering worker from ERROR status: {}", workerId);
                worker.setStatus(WorkerStatus.ACTIVE);
                workerRepository.save(worker);
                cacheService.evictWorkerFromCache(workerId);
            }
        }
    }

    private void handleUnhealthyWorker(Worker worker, HealthCheckResult result) {
        String workerId = worker.getWorkerId();
        logger.warn("Worker {} is unhealthy: {}", workerId, result.getIssues());
        
        // Send notification if needed (implement notification throttling)
        if (shouldSendNotification(workerId)) {
            sendHealthAlert(worker, result);
            lastNotificationSent.put(workerId, LocalDateTime.now());
        }
    }

    private void handleRecoveredWorker(Worker worker) {
        String workerId = worker.getWorkerId();
        logger.info("Worker {} has recovered", workerId);
        totalRecoveries.incrementAndGet();
        
        // Send recovery notification
        sendRecoveryNotification(worker);
    }

    private void handleFailedWorker(Worker worker, HealthCheckResult result) {
        String workerId = worker.getWorkerId();
        logger.error("Worker {} has failed health checks: {}", workerId, result.getIssues());
        
        // Mark worker as failed
        if (worker.getStatus() != WorkerStatus.ERROR) {
            worker.setStatus(WorkerStatus.ERROR);
            workerRepository.save(worker);
            cacheService.evictWorkerFromCache(workerId);
        }
        
        // Send critical alert
        sendCriticalAlert(worker, result);
    }

    private List<Worker> findFailedWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(cleanupThresholdMinutes);
        return workerRepository.findPotentiallyDeadWorkers(threshold);
    }

    private boolean shouldCleanupWorker(Worker worker) {
        // Don't cleanup recently active workers
        if (worker.getLastHeartbeat() != null) {
            Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), LocalDateTime.now());
            if (timeSinceHeartbeat.toMinutes() < cleanupThresholdMinutes) {
                return false;
            }
        }
        
        // Check if worker has been in failed state long enough
        Long failures = consecutiveFailures.getOrDefault(worker.getWorkerId(), new AtomicLong(0)).get();
        return failures >= maxConsecutiveFailures;
    }

    private CleanupResult cleanupFailedWorker(Worker worker) {
        String workerId = worker.getWorkerId();
        
        try {
            logger.info("Cleaning up failed worker: {}", workerId);
            
            // Set status to inactive
            worker.setStatus(WorkerStatus.INACTIVE);
            
            // Clear job assignments if any
            if (worker.getCurrentJobCount() > 0) {
                logger.warn("Clearing {} jobs from failed worker {}", worker.getCurrentJobCount(), workerId);
                worker.clearCurrentJobs();
            }
            
            // Save changes
            workerRepository.save(worker);
            
            // Cleanup tracking data
            workerHealthStates.remove(workerId);
            consecutiveFailures.remove(workerId);
            lastNotificationSent.remove(workerId);
            
            // Remove from cache
            cacheService.evictWorkerFromCache(workerId);
            
            return new CleanupResult(true, "Worker successfully cleaned up and marked inactive");
            
        } catch (Exception e) {
            logger.error("Error cleaning up worker {}: {}", workerId, e.getMessage(), e);
            return new CleanupResult(false, "Cleanup failed: " + e.getMessage());
        }
    }

    private void updateWorkerHealthState(String workerId, boolean healthy, List<String> issues, LocalDateTime timestamp) {
        WorkerHealthState state = workerHealthStates.computeIfAbsent(workerId, k -> new WorkerHealthState());
        state.setHealthy(healthy);
        state.setLastCheckTime(timestamp);
        state.setLastIssues(issues);
        state.setLastResponseTime(System.currentTimeMillis() % 1000); // Simplified response time
    }

    private boolean shouldSendNotification(String workerId) {
        LocalDateTime lastSent = lastNotificationSent.get(workerId);
        if (lastSent == null) {
            return true;
        }
        
        // Send notification at most once every 30 minutes
        return Duration.between(lastSent, LocalDateTime.now()).toMinutes() >= 30;
    }

    private void sendHealthAlert(Worker worker, HealthCheckResult result) {
        // Implement your notification system here
        logger.warn("HEALTH ALERT for worker {}: {}", worker.getWorkerId(), result.getIssues());
    }

    private void sendRecoveryNotification(Worker worker) {
        // Implement your notification system here
        logger.info("RECOVERY NOTIFICATION for worker {}: Worker has recovered", worker.getWorkerId());
    }

    private void sendCriticalAlert(Worker worker, HealthCheckResult result) {
        // Implement your notification system here
        logger.error("CRITICAL ALERT for worker {}: Worker has failed - {}", worker.getWorkerId(), result.getIssues());
    }

    // Data classes

    public enum HealthStatus {
        HEALTHY, UNHEALTHY, RECOVERED, FAILED
    }

    public static class HealthCheckResult {
        private final String workerId;
        private HealthStatus status;
        private final List<String> issues = new ArrayList<>();
        private final LocalDateTime timestamp = LocalDateTime.now();

        public HealthCheckResult(String workerId) {
            this.workerId = workerId;
            this.status = HealthStatus.HEALTHY;
        }

        public HealthCheckResult(String workerId, HealthStatus status, List<String> issues) {
            this.workerId = workerId;
            this.status = status;
            this.issues.addAll(issues);
        }

        public void addIssue(String issue) {
            this.issues.add(issue);
        }

        // Getters
        public String getWorkerId() { return workerId; }
        public HealthStatus getStatus() { return status; }
        public void setStatus(HealthStatus status) { this.status = status; }
        public List<String> getIssues() { return issues; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class WorkerHealthState {
        private boolean healthy = true;
        private LocalDateTime lastCheckTime;
        private List<String> lastIssues = new ArrayList<>();
        private double lastResponseTime = 0.0;

        // Getters and Setters
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public LocalDateTime getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(LocalDateTime lastCheckTime) { this.lastCheckTime = lastCheckTime; }

        public List<String> getLastIssues() { return lastIssues; }
        public void setLastIssues(List<String> lastIssues) { this.lastIssues = new ArrayList<>(lastIssues); }

        public double getLastResponseTime() { return lastResponseTime; }
        public void setLastResponseTime(double lastResponseTime) { this.lastResponseTime = lastResponseTime; }
    }

    public static class CleanupResult {
        private final boolean success;
        private final String message;

        public CleanupResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class HealthMonitoringStatistics {
        private final long totalWorkers;
        private final long healthyWorkers;
        private final long unhealthyWorkers;
        private final long totalHealthChecks;
        private final long totalRecoveries;
        private final long totalFailures;
        private final double averageResponseTime;
        private final Map<String, Long> statusDistribution;
        private final LocalDateTime timestamp;

        public HealthMonitoringStatistics(long totalWorkers, long healthyWorkers, long unhealthyWorkers,
                                        long totalHealthChecks, long totalRecoveries, long totalFailures,
                                        double averageResponseTime, Map<String, Long> statusDistribution,
                                        LocalDateTime timestamp) {
            this.totalWorkers = totalWorkers;
            this.healthyWorkers = healthyWorkers;
            this.unhealthyWorkers = unhealthyWorkers;
            this.totalHealthChecks = totalHealthChecks;
            this.totalRecoveries = totalRecoveries;
            this.totalFailures = totalFailures;
            this.averageResponseTime = averageResponseTime;
            this.statusDistribution = statusDistribution;
            this.timestamp = timestamp;
        }

        // Getters
        public long getTotalWorkers() { return totalWorkers; }
        public long getHealthyWorkers() { return healthyWorkers; }
        public long getUnhealthyWorkers() { return unhealthyWorkers; }
        public long getTotalHealthChecks() { return totalHealthChecks; }
        public long getTotalRecoveries() { return totalRecoveries; }
        public long getTotalFailures() { return totalFailures; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Long> getStatusDistribution() { return statusDistribution; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class DetailedHealthReport {
        private final String workerId;
        private final boolean healthy;
        private final String summary;
        private final List<String> issues;
        private final WorkerHealthState healthState;
        private final HealthCheckResult lastCheck;

        public DetailedHealthReport(String workerId, boolean healthy, String summary, 
                                  List<String> issues, WorkerHealthState healthState, 
                                  HealthCheckResult lastCheck) {
            this.workerId = workerId;
            this.healthy = healthy;
            this.summary = summary;
            this.issues = issues;
            this.healthState = healthState;
            this.lastCheck = lastCheck;
        }

        // Getters
        public String getWorkerId() { return workerId; }
        public boolean isHealthy() { return healthy; }
        public String getSummary() { return summary; }
        public List<String> getIssues() { return issues; }
        public WorkerHealthState getHealthState() { return healthState; }
        public HealthCheckResult getLastCheck() { return lastCheck; }
    }
}
