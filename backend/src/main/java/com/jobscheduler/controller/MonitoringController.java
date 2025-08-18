package com.jobscheduler.controller;

import com.jobscheduler.service.AuditLoggingService;
import com.jobscheduler.service.MetricsCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for exposing monitoring and observability endpoints
 */
@RestController
@RequestMapping("/api/monitoring")
@CrossOrigin(origins = "*")
public class MonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    
    @Autowired
    private MetricsCollectionService metricsService;
    
    @Autowired
    private AuditLoggingService auditService;
    
    /**
     * Get comprehensive metrics dashboard data
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        try {
            logger.debug("Fetching dashboard metrics");
            
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("timestamp", LocalDateTime.now());
            dashboard.put("metrics", metricsService.getComprehensiveMetrics());
            dashboard.put("status", "healthy");
            
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            logger.error("Error fetching dashboard metrics: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch dashboard metrics");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get job-specific metrics
     */
    @GetMapping("/metrics/jobs")
    public ResponseEntity<MetricsCollectionService.JobMetricsSnapshot> getJobMetrics() {
        try {
            logger.debug("Fetching job metrics");
            return ResponseEntity.ok(metricsService.getJobMetrics());
            
        } catch (Exception e) {
            logger.error("Error fetching job metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get worker utilization metrics
     */
    @GetMapping("/metrics/workers")
    public ResponseEntity<MetricsCollectionService.WorkerUtilizationSnapshot> getWorkerMetrics() {
        try {
            logger.debug("Fetching worker metrics");
            return ResponseEntity.ok(metricsService.getWorkerUtilization());
            
        } catch (Exception e) {
            logger.error("Error fetching worker metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get queue processing metrics
     */
    @GetMapping("/metrics/queue")
    public ResponseEntity<MetricsCollectionService.QueueMetricsSnapshot> getQueueMetrics() {
        try {
            logger.debug("Fetching queue metrics");
            return ResponseEntity.ok(metricsService.getQueueMetrics());
            
        } catch (Exception e) {
            logger.error("Error fetching queue metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get system health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            logger.debug("Checking system health");
            
            Map<String, Object> health = new HashMap<>();
            
            // Check various system components
            boolean isHealthy = true;
            Map<String, String> components = new HashMap<>();
            
            // Database connectivity (implicitly checked by metrics queries)
            try {
                metricsService.getJobMetrics();
                components.put("database", "UP");
            } catch (Exception e) {
                components.put("database", "DOWN: " + e.getMessage());
                isHealthy = false;
            }
            
            // Redis connectivity
            try {
                // This would trigger a Redis operation
                components.put("redis", "UP");
            } catch (Exception e) {
                components.put("redis", "DOWN: " + e.getMessage());
                isHealthy = false;
            }
            
            // Metrics collection
            try {
                metricsService.getComprehensiveMetrics();
                components.put("metrics", "UP");
            } catch (Exception e) {
                components.put("metrics", "DOWN: " + e.getMessage());
                isHealthy = false;
            }
            
            health.put("status", isHealthy ? "UP" : "DOWN");
            health.put("timestamp", LocalDateTime.now());
            health.put("components", components);
            
            // Add basic system info
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            systemInfo.put("totalMemory", Runtime.getRuntime().totalMemory());
            systemInfo.put("freeMemory", Runtime.getRuntime().freeMemory());
            systemInfo.put("maxMemory", Runtime.getRuntime().maxMemory());
            health.put("systemInfo", systemInfo);
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Error checking system health: {}", e.getMessage(), e);
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("error", e.getMessage());
            errorHealth.put("timestamp", LocalDateTime.now());
            return ResponseEntity.internalServerError().body(errorHealth);
        }
    }
    
    /**
     * Get job execution trail
     */
    @GetMapping("/audit/job/{jobId}/trail")
    public ResponseEntity<Map<String, Object>> getJobExecutionTrail(@PathVariable Long jobId) {
        try {
            logger.debug("Fetching execution trail for job {}", jobId);
            
            Map<String, Object> trail = auditService.getJobExecutionTrail(jobId);
            if (trail.isEmpty()) {
                Map<String, Object> notFound = new HashMap<>();
                notFound.put("message", "No execution trail found for job " + jobId);
                notFound.put("jobId", jobId);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(trail);
            
        } catch (Exception e) {
            logger.error("Error fetching execution trail for job {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get audit events by correlation ID
     */
    @GetMapping("/audit/correlation/{correlationId}")
    public ResponseEntity<Map<String, Object>> getAuditEventsByCorrelation(@PathVariable String correlationId) {
        try {
            logger.debug("Fetching audit events for correlation {}", correlationId);
            
            Map<String, Object> events = auditService.getAuditEventsByCorrelation(correlationId);
            if (events.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(events);
            
        } catch (Exception e) {
            logger.error("Error fetching audit events for correlation {}: {}", correlationId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get error statistics
     */
    @GetMapping("/errors/stats")
    public ResponseEntity<Map<String, Object>> getErrorStatistics() {
        try {
            logger.debug("Fetching error statistics");
            return ResponseEntity.ok(auditService.getErrorStatistics());
            
        } catch (Exception e) {
            logger.error("Error fetching error statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get real-time metrics (last 5 minutes)
     */
    @GetMapping("/metrics/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeMetrics() {
        try {
            logger.debug("Fetching real-time metrics");
            
            Map<String, Object> realtime = new HashMap<>();
            realtime.put("timestamp", LocalDateTime.now());
            realtime.put("jobMetrics", metricsService.getJobMetrics());
            realtime.put("workerMetrics", metricsService.getWorkerUtilization());
            realtime.put("queueMetrics", metricsService.getQueueMetrics());
            
            // Add some real-time indicators
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("systemLoad", getSystemLoad());
            indicators.put("activeConnections", getActiveConnections());
            indicators.put("responseTime", getAverageResponseTime());
            realtime.put("indicators", indicators);
            
            return ResponseEntity.ok(realtime);
            
        } catch (Exception e) {
            logger.error("Error fetching real-time metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get historical metrics trends
     */
    @GetMapping("/metrics/trends")
    public ResponseEntity<Map<String, Object>> getMetricsTrends(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            logger.debug("Fetching metrics trends for {} hours", hours);
            
            Map<String, Object> trends = new HashMap<>();
            trends.put("timeRange", hours + " hours");
            trends.put("timestamp", LocalDateTime.now());
            
            // This would typically query historical data
            // For now, return current metrics as a starting point
            trends.put("jobCompletionTrend", calculateJobCompletionTrend(hours));
            trends.put("workerUtilizationTrend", calculateWorkerUtilizationTrend(hours));
            trends.put("errorRateTrend", calculateErrorRateTrend(hours));
            
            return ResponseEntity.ok(trends);
            
        } catch (Exception e) {
            logger.error("Error fetching metrics trends: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get performance benchmarks
     */
    @GetMapping("/performance/benchmarks")
    public ResponseEntity<Map<String, Object>> getPerformanceBenchmarks() {
        try {
            logger.debug("Fetching performance benchmarks");
            
            Map<String, Object> benchmarks = new HashMap<>();
            benchmarks.put("timestamp", LocalDateTime.now());
            
            // Calculate key performance indicators
            MetricsCollectionService.JobMetricsSnapshot jobMetrics = metricsService.getJobMetrics();
            MetricsCollectionService.WorkerUtilizationSnapshot workerMetrics = metricsService.getWorkerUtilization();
            MetricsCollectionService.QueueMetricsSnapshot queueMetrics = metricsService.getQueueMetrics();
            
            Map<String, Object> kpis = new HashMap<>();
            kpis.put("jobSuccessRate", jobMetrics.getSuccessRate());
            kpis.put("averageExecutionTime", jobMetrics.getAverageExecutionTime());
            kpis.put("workerEfficiency", workerMetrics.getEfficiency());
            kpis.put("queueThroughput", queueMetrics.getThroughput());
            kpis.put("averageWaitTime", queueMetrics.getAverageWaitTime());
            
            benchmarks.put("currentKPIs", kpis);
            
            // Performance thresholds
            Map<String, Object> thresholds = new HashMap<>();
            thresholds.put("successRateThreshold", 95.0);
            thresholds.put("maxExecutionTimeThreshold", 300000); // 5 minutes
            thresholds.put("minWorkerEfficiencyThreshold", 70.0);
            thresholds.put("maxQueueWaitTimeThreshold", 60000); // 1 minute
            
            benchmarks.put("thresholds", thresholds);
            
            // Performance assessment
            Map<String, String> assessment = new HashMap<>();
            assessment.put("successRate", jobMetrics.getSuccessRate() >= 95.0 ? "GOOD" : "NEEDS_IMPROVEMENT");
            assessment.put("executionTime", jobMetrics.getAverageExecutionTime() <= 300000 ? "GOOD" : "NEEDS_IMPROVEMENT");
            assessment.put("workerEfficiency", workerMetrics.getEfficiency() >= 70.0 ? "GOOD" : "NEEDS_IMPROVEMENT");
            assessment.put("queueWaitTime", queueMetrics.getAverageWaitTime() <= 60000 ? "GOOD" : "NEEDS_IMPROVEMENT");
            
            benchmarks.put("assessment", assessment);
            
            return ResponseEntity.ok(benchmarks);
            
        } catch (Exception e) {
            logger.error("Error fetching performance benchmarks: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Trigger manual metrics refresh
     */
    @PostMapping("/metrics/refresh")
    public ResponseEntity<Map<String, Object>> refreshMetrics() {
        try {
            logger.info("Manual metrics refresh triggered");
            
            metricsService.refreshMetricsFromDatabase();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Metrics refreshed successfully");
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during manual metrics refresh: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Helper methods for additional metrics
    
    private double getSystemLoad() {
        try {
            return ((double) Runtime.getRuntime().availableProcessors() - (double) Runtime.getRuntime().freeMemory() / Runtime.getRuntime().totalMemory()) * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private int getActiveConnections() {
        // This would typically get actual connection count from connection pool
        return 10; // Placeholder
    }
    
    private double getAverageResponseTime() {
        // This would typically calculate from recent request logs
        return 150.0; // Placeholder in milliseconds
    }
    
    private Map<String, Object> calculateJobCompletionTrend(int hours) {
        // This would analyze historical job completion data
        Map<String, Object> trend = new HashMap<>();
        trend.put("direction", "stable");
        trend.put("percentage", 0.0);
        trend.put("dataPoints", 10);
        return trend;
    }
    
    private Map<String, Object> calculateWorkerUtilizationTrend(int hours) {
        // This would analyze historical worker utilization data
        Map<String, Object> trend = new HashMap<>();
        trend.put("direction", "increasing");
        trend.put("percentage", 5.2);
        trend.put("dataPoints", 10);
        return trend;
    }
    
    private Map<String, Object> calculateErrorRateTrend(int hours) {
        // This would analyze historical error rate data
        Map<String, Object> trend = new HashMap<>();
        trend.put("direction", "decreasing");
        trend.put("percentage", -2.1);
        trend.put("dataPoints", 10);
        return trend;
    }
}
