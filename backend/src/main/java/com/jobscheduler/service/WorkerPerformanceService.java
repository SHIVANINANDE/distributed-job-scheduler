package com.jobscheduler.service;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Worker Performance Service for tracking and analyzing worker performance metrics
 * Provides performance analytics, trend analysis, and optimization recommendations
 */
@Service
public class WorkerPerformanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkerPerformanceService.class);
    
    @Autowired
    private WorkerService workerService;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private CacheService cacheService;
    
    @Value("${worker.performance.monitoring.enabled:true}")
    private boolean performanceMonitoringEnabled;
    
    @Value("${worker.performance.window.hours:24}")
    private int performanceWindowHours;
    
    @Value("${worker.performance.min-jobs-for-rating:10}")
    private int minJobsForRating;
    
    @Value("${worker.performance.blacklist.threshold:0.5}")
    private double blacklistThreshold;
    
    @Value("${worker.performance.blacklist.duration-minutes:30}")
    private int blacklistDurationMinutes;
    
    // Performance metrics storage
    private final Map<String, WorkerPerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<PerformanceSnapshot>> performanceHistory = new ConcurrentHashMap<>();
    
    /**
     * Worker Performance Metrics class
     */
    public static class WorkerPerformanceMetrics {
        private String workerId;
        private long totalJobsProcessed;
        private long successfulJobs;
        private long failedJobs;
        private double averageExecutionTime;
        private double successRate;
        private double reliabilityScore;
        private double efficiencyScore;
        private double overallPerformanceScore;
        private LocalDateTime lastUpdated;
        private List<Long> recentExecutionTimes = new ArrayList<>();
        private int consecutiveFailures;
        private int consecutiveSuccesses;
        
        public WorkerPerformanceMetrics(String workerId) {
            this.workerId = workerId;
            this.lastUpdated = LocalDateTime.now();
        }
        
        public void recordJobCompletion(boolean success, long executionTimeMs) {
            totalJobsProcessed++;
            if (success) {
                successfulJobs++;
                consecutiveSuccesses++;
                consecutiveFailures = 0;
            } else {
                failedJobs++;
                consecutiveFailures++;
                consecutiveSuccesses = 0;
            }
            
            // Track execution times for successful jobs
            if (success && executionTimeMs > 0) {
                recentExecutionTimes.add(executionTimeMs);
                // Keep only recent 100 execution times
                if (recentExecutionTimes.size() > 100) {
                    recentExecutionTimes.remove(0);
                }
                updateAverageExecutionTime();
            }
            
            updateMetrics();
            lastUpdated = LocalDateTime.now();
        }
        
        private void updateAverageExecutionTime() {
            if (!recentExecutionTimes.isEmpty()) {
                averageExecutionTime = recentExecutionTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);
            }
        }
        
        private void updateMetrics() {
            // Calculate success rate
            successRate = totalJobsProcessed > 0 ? 
                    (double) successfulJobs / totalJobsProcessed * 100.0 : 0.0;
            
            // Calculate reliability score (considering consecutive failures)
            reliabilityScore = Math.max(0, successRate - (consecutiveFailures * 10));
            
            // Calculate efficiency score (based on execution time consistency)
            efficiencyScore = calculateEfficiencyScore();
            
            // Calculate overall performance score
            overallPerformanceScore = (successRate * 0.4) + (reliabilityScore * 0.3) + (efficiencyScore * 0.3);
        }
        
        private double calculateEfficiencyScore() {
            if (recentExecutionTimes.size() < 5) {
                return successRate; // Default to success rate if insufficient data
            }
            
            // Calculate coefficient of variation (std dev / mean)
            double mean = recentExecutionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = recentExecutionTimes.stream()
                    .mapToDouble(time -> Math.pow(time - mean, 2))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);
            double coefficientOfVariation = mean > 0 ? stdDev / mean : 1.0;
            
            // Lower variation = higher efficiency score
            return Math.max(0, 100 - (coefficientOfVariation * 100));
        }
        
        // Getters
        public String getWorkerId() { return workerId; }
        public long getTotalJobsProcessed() { return totalJobsProcessed; }
        public long getSuccessfulJobs() { return successfulJobs; }
        public long getFailedJobs() { return failedJobs; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getSuccessRate() { return successRate; }
        public double getReliabilityScore() { return reliabilityScore; }
        public double getEfficiencyScore() { return efficiencyScore; }
        public double getOverallPerformanceScore() { return overallPerformanceScore; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    }
    
    /**
     * Performance Snapshot for historical tracking
     */
    public static class PerformanceSnapshot {
        private LocalDateTime timestamp;
        private double successRate;
        private double averageExecutionTime;
        private double overallScore;
        private long totalJobs;
        
        public PerformanceSnapshot(WorkerPerformanceMetrics metrics) {
            this.timestamp = LocalDateTime.now();
            this.successRate = metrics.getSuccessRate();
            this.averageExecutionTime = metrics.getAverageExecutionTime();
            this.overallScore = metrics.getOverallPerformanceScore();
            this.totalJobs = metrics.getTotalJobsProcessed();
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getSuccessRate() { return successRate; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getOverallScore() { return overallScore; }
        public long getTotalJobs() { return totalJobs; }
    }
    
    /**
     * Record job completion for performance tracking
     */
    public void recordJobCompletion(String workerId, Long jobId, boolean success, long executionTimeMs) {
        if (!performanceMonitoringEnabled) {
            return;
        }
        
        try {
            logger.debug("Recording job completion for worker {}: success={}, executionTime={}ms", 
                        workerId, success, executionTimeMs);
            
            WorkerPerformanceMetrics metrics = performanceMetrics.computeIfAbsent(
                    workerId, WorkerPerformanceMetrics::new);
            
            metrics.recordJobCompletion(success, executionTimeMs);
            
            // Check for blacklisting
            if (shouldBlacklistWorker(metrics)) {
                blacklistWorker(workerId, "Poor performance: consecutive failures");
            }
            
            // Cache updated metrics
            cachePerformanceMetrics(workerId, metrics);
            
        } catch (Exception e) {
            logger.error("Error recording job completion for worker {}: {}", workerId, e.getMessage(), e);
        }
    }
    
    /**
     * Get performance metrics for a specific worker
     */
    public WorkerPerformanceMetrics getWorkerPerformanceMetrics(String workerId) {
        return performanceMetrics.getOrDefault(workerId, new WorkerPerformanceMetrics(workerId));
    }
    
    /**
     * Get all worker performance metrics
     */
    public Map<String, WorkerPerformanceMetrics> getAllPerformanceMetrics() {
        return new HashMap<>(performanceMetrics);
    }
    
    /**
     * Get top performing workers
     */
    public List<WorkerPerformanceMetrics> getTopPerformingWorkers(int limit) {
        return performanceMetrics.values().stream()
                .filter(metrics -> metrics.getTotalJobsProcessed() >= minJobsForRating)
                .sorted(Comparator.comparing(WorkerPerformanceMetrics::getOverallPerformanceScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get underperforming workers
     */
    public List<WorkerPerformanceMetrics> getUnderperformingWorkers(double thresholdScore) {
        return performanceMetrics.values().stream()
                .filter(metrics -> metrics.getTotalJobsProcessed() >= minJobsForRating)
                .filter(metrics -> metrics.getOverallPerformanceScore() < thresholdScore)
                .sorted(Comparator.comparing(WorkerPerformanceMetrics::getOverallPerformanceScore))
                .collect(Collectors.toList());
    }
    
    /**
     * Get performance trend for a worker
     */
    public List<PerformanceSnapshot> getWorkerPerformanceTrend(String workerId, int hours) {
        List<PerformanceSnapshot> history = performanceHistory.get(workerId);
        if (history == null) {
            return new ArrayList<>();
        }
        
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return history.stream()
                .filter(snapshot -> snapshot.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate worker efficiency rating
     */
    public String getWorkerEfficiencyRating(String workerId) {
        WorkerPerformanceMetrics metrics = getWorkerPerformanceMetrics(workerId);
        
        if (metrics.getTotalJobsProcessed() < minJobsForRating) {
            return "INSUFFICIENT_DATA";
        }
        
        double score = metrics.getOverallPerformanceScore();
        
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 70) return "AVERAGE";
        if (score >= 60) return "BELOW_AVERAGE";
        return "POOR";
    }
    
    /**
     * Get performance recommendations for a worker
     */
    public List<String> getPerformanceRecommendations(String workerId) {
        WorkerPerformanceMetrics metrics = getWorkerPerformanceMetrics(workerId);
        List<String> recommendations = new ArrayList<>();
        
        if (metrics.getTotalJobsProcessed() < minJobsForRating) {
            recommendations.add("Insufficient data for analysis. Continue processing jobs.");
            return recommendations;
        }
        
        if (metrics.getSuccessRate() < 85) {
            recommendations.add("Improve job success rate. Current: " + 
                              String.format("%.1f%%", metrics.getSuccessRate()));
        }
        
        if (metrics.getConsecutiveFailures() >= 3) {
            recommendations.add("Investigate recent consecutive failures. Check worker health and capacity.");
        }
        
        if (metrics.getEfficiencyScore() < 70) {
            recommendations.add("Improve execution time consistency. Consider optimizing job processing logic.");
        }
        
        if (metrics.getAverageExecutionTime() > 30000) { // 30 seconds
            recommendations.add("Consider optimizing job execution time. Current average: " + 
                              String.format("%.1f seconds", metrics.getAverageExecutionTime() / 1000.0));
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is good. Continue current approach.");
        }
        
        return recommendations;
    }
    
    /**
     * Check if worker should be blacklisted
     */
    private boolean shouldBlacklistWorker(WorkerPerformanceMetrics metrics) {
        // Blacklist if consecutive failures exceed threshold and success rate is very low
        return metrics.getConsecutiveFailures() >= 5 && 
               metrics.getSuccessRate() < (blacklistThreshold * 100) &&
               metrics.getTotalJobsProcessed() >= minJobsForRating;
    }
    
    /**
     * Blacklist worker temporarily
     */
    private void blacklistWorker(String workerId, String reason) {
        try {
            String blacklistKey = "worker:blacklist:" + workerId;
            cacheService.put(blacklistKey, true, blacklistDurationMinutes * 60); // Convert to seconds
            
            logger.warn("Worker {} blacklisted for {} minutes. Reason: {}", 
                       workerId, blacklistDurationMinutes, reason);
            
            // Notify worker service about blacklisting
            workerService.markWorkerAsUnhealthy(workerId, reason);
            
        } catch (Exception e) {
            logger.error("Error blacklisting worker {}: {}", workerId, e.getMessage(), e);
        }
    }
    
    /**
     * Cache performance metrics
     */
    private void cachePerformanceMetrics(String workerId, WorkerPerformanceMetrics metrics) {
        try {
            String metricsKey = "worker:performance:" + workerId;
            cacheService.put(metricsKey, metrics, 3600); // 1 hour TTL
        } catch (Exception e) {
            logger.debug("Error caching performance metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Periodic performance snapshot creation
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void createPerformanceSnapshots() {
        if (!performanceMonitoringEnabled) {
            return;
        }
        
        logger.debug("Creating performance snapshots for {} workers", performanceMetrics.size());
        
        for (Map.Entry<String, WorkerPerformanceMetrics> entry : performanceMetrics.entrySet()) {
            String workerId = entry.getKey();
            WorkerPerformanceMetrics metrics = entry.getValue();
            
            // Create snapshot
            PerformanceSnapshot snapshot = new PerformanceSnapshot(metrics);
            
            // Add to history
            List<PerformanceSnapshot> history = performanceHistory.computeIfAbsent(
                    workerId, k -> new ArrayList<>());
            history.add(snapshot);
            
            // Keep only recent snapshots (24 hours worth)
            LocalDateTime cutoff = LocalDateTime.now().minusHours(performanceWindowHours);
            history.removeIf(s -> s.getTimestamp().isBefore(cutoff));
        }
    }
    
    /**
     * Cleanup old performance data
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupOldPerformanceData() {
        logger.debug("Cleaning up old performance data");
        
        LocalDateTime cutoff = LocalDateTime.now().minusHours(performanceWindowHours * 2);
        
        // Remove old snapshots
        for (List<PerformanceSnapshot> history : performanceHistory.values()) {
            history.removeIf(snapshot -> snapshot.getTimestamp().isBefore(cutoff));
        }
        
        // Remove metrics for workers that haven't been active
        performanceMetrics.entrySet().removeIf(entry -> 
                entry.getValue().getLastUpdated().isBefore(cutoff));
    }
    
    /**
     * Get system-wide performance statistics
     */
    public Map<String, Object> getSystemPerformanceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<WorkerPerformanceMetrics> allMetrics = new ArrayList<>(performanceMetrics.values());
        
        if (allMetrics.isEmpty()) {
            stats.put("totalWorkers", 0);
            return stats;
        }
        
        double avgSuccessRate = allMetrics.stream()
                .mapToDouble(WorkerPerformanceMetrics::getSuccessRate)
                .average()
                .orElse(0.0);
        
        double avgPerformanceScore = allMetrics.stream()
                .mapToDouble(WorkerPerformanceMetrics::getOverallPerformanceScore)
                .average()
                .orElse(0.0);
        
        long totalJobsProcessed = allMetrics.stream()
                .mapToLong(WorkerPerformanceMetrics::getTotalJobsProcessed)
                .sum();
        
        long totalSuccessfulJobs = allMetrics.stream()
                .mapToLong(WorkerPerformanceMetrics::getSuccessfulJobs)
                .sum();
        
        stats.put("totalWorkers", allMetrics.size());
        stats.put("averageSuccessRate", avgSuccessRate);
        stats.put("averagePerformanceScore", avgPerformanceScore);
        stats.put("totalJobsProcessed", totalJobsProcessed);
        stats.put("totalSuccessfulJobs", totalSuccessfulJobs);
        stats.put("systemSuccessRate", totalJobsProcessed > 0 ? 
                 (double) totalSuccessfulJobs / totalJobsProcessed * 100.0 : 0.0);
        
        return stats;
    }
}
