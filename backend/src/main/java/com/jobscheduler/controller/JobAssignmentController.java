package com.jobscheduler.controller;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.Worker;
import com.jobscheduler.service.JobAssignmentService;
import com.jobscheduler.service.LoadBalancingService;
import com.jobscheduler.service.WorkerPerformanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Job Assignment and Load Balancing operations
 * Provides endpoints for assignment management, performance monitoring, and load balancing
 */
@RestController
@RequestMapping("/api/v1/assignment")
@CrossOrigin(origins = "*")
public class JobAssignmentController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobAssignmentController.class);
    
    @Autowired
    private JobAssignmentService jobAssignmentService;
    
    @Autowired
    private LoadBalancingService loadBalancingService;
    
    @Autowired
    private WorkerPerformanceService workerPerformanceService;
    
    /**
     * Manually assign a job to a worker
     */
    @PostMapping("/jobs/{jobId}/assign")
    public ResponseEntity<Map<String, Object>> assignJob(@PathVariable Long jobId) {
        try {
            logger.info("Manual job assignment request for job {}", jobId);
            
            // Note: This would typically get the job from JobService
            // For now, we'll return a structure indicating the assignment process
            
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "ASSIGNMENT_INITIATED");
            response.put("message", "Job assignment process initiated");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error assigning job {}: {}", jobId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Assignment failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("jobId", jobId);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Reassign a job from a failed worker
     */
    @PostMapping("/jobs/{jobId}/reassign")
    public ResponseEntity<Map<String, Object>> reassignJob(
            @PathVariable Long jobId,
            @RequestParam String failedWorkerId,
            @RequestParam String failureReason) {
        
        try {
            logger.info("Job reassignment request for job {} from worker {}: {}", 
                       jobId, failedWorkerId, failureReason);
            
            boolean success = jobAssignmentService.reassignJob(jobId, failedWorkerId, failureReason);
            
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("previousWorkerId", failedWorkerId);
            response.put("reassignmentSuccessful", success);
            response.put("failureReason", failureReason);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            if (success) {
                response.put("status", "REASSIGNED");
                response.put("message", "Job successfully reassigned to new worker");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "REASSIGNMENT_FAILED");
                response.put("message", "Failed to reassign job to new worker");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error reassigning job {}: {}", jobId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Reassignment failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("jobId", jobId);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get assignment statistics for a specific worker
     */
    @GetMapping("/workers/{workerId}/assignment-stats")
    public ResponseEntity<Map<String, Object>> getWorkerAssignmentStats(@PathVariable String workerId) {
        try {
            logger.debug("Getting assignment statistics for worker {}", workerId);
            
            JobAssignmentService.AssignmentStats stats = jobAssignmentService.getWorkerAssignmentStats(workerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workerId", workerId);
            response.put("totalAssignments", stats.getTotalAssignments());
            response.put("successfulAssignments", stats.getSuccessfulAssignments());
            response.put("failedAssignments", stats.getFailedAssignments());
            response.put("successRate", stats.getSuccessRate());
            response.put("lastAssignment", stats.getLastAssignment());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting assignment stats for worker {}: {}", workerId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get assignment statistics");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get all assignment statistics
     */
    @GetMapping("/stats/assignments")
    public ResponseEntity<Map<String, Object>> getAllAssignmentStats() {
        try {
            logger.debug("Getting all assignment statistics");
            
            Map<String, JobAssignmentService.AssignmentStats> allStats = 
                    jobAssignmentService.getAllAssignmentStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalWorkers", allStats.size());
            response.put("assignmentStats", allStats);
            
            // Calculate aggregate statistics
            long totalAssignments = allStats.values().stream()
                    .mapToLong(JobAssignmentService.AssignmentStats::getTotalAssignments)
                    .sum();
            
            long totalSuccessful = allStats.values().stream()
                    .mapToLong(JobAssignmentService.AssignmentStats::getSuccessfulAssignments)
                    .sum();
            
            double overallSuccessRate = totalAssignments > 0 ? 
                    (double) totalSuccessful / totalAssignments * 100.0 : 0.0;
            
            response.put("aggregateStats", Map.of(
                    "totalAssignments", totalAssignments,
                    "totalSuccessful", totalSuccessful,
                    "overallSuccessRate", overallSuccessRate
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting all assignment stats: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get assignment statistics");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get worker performance metrics
     */
    @GetMapping("/workers/{workerId}/performance")
    public ResponseEntity<Map<String, Object>> getWorkerPerformanceMetrics(@PathVariable String workerId) {
        try {
            logger.debug("Getting performance metrics for worker {}", workerId);
            
            WorkerPerformanceService.WorkerPerformanceMetrics metrics = 
                    workerPerformanceService.getWorkerPerformanceMetrics(workerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workerId", workerId);
            response.put("totalJobsProcessed", metrics.getTotalJobsProcessed());
            response.put("successfulJobs", metrics.getSuccessfulJobs());
            response.put("failedJobs", metrics.getFailedJobs());
            response.put("successRate", metrics.getSuccessRate());
            response.put("averageExecutionTime", metrics.getAverageExecutionTime());
            response.put("reliabilityScore", metrics.getReliabilityScore());
            response.put("efficiencyScore", metrics.getEfficiencyScore());
            response.put("overallPerformanceScore", metrics.getOverallPerformanceScore());
            response.put("consecutiveFailures", metrics.getConsecutiveFailures());
            response.put("consecutiveSuccesses", metrics.getConsecutiveSuccesses());
            response.put("lastUpdated", metrics.getLastUpdated());
            
            // Add efficiency rating and recommendations
            String rating = workerPerformanceService.getWorkerEfficiencyRating(workerId);
            List<String> recommendations = workerPerformanceService.getPerformanceRecommendations(workerId);
            
            response.put("efficiencyRating", rating);
            response.put("recommendations", recommendations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting performance metrics for worker {}: {}", workerId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get performance metrics");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get top performing workers
     */
    @GetMapping("/workers/top-performers")
    public ResponseEntity<Map<String, Object>> getTopPerformingWorkers(
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            logger.debug("Getting top {} performing workers", limit);
            
            List<WorkerPerformanceService.WorkerPerformanceMetrics> topPerformers = 
                    workerPerformanceService.getTopPerformingWorkers(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("topPerformers", topPerformers);
            response.put("limit", limit);
            response.put("totalCount", topPerformers.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting top performing workers: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get top performing workers");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get underperforming workers
     */
    @GetMapping("/workers/underperforming")
    public ResponseEntity<Map<String, Object>> getUnderperformingWorkers(
            @RequestParam(defaultValue = "60.0") double thresholdScore) {
        
        try {
            logger.debug("Getting underperforming workers with threshold {}", thresholdScore);
            
            List<WorkerPerformanceService.WorkerPerformanceMetrics> underperformers = 
                    workerPerformanceService.getUnderperformingWorkers(thresholdScore);
            
            Map<String, Object> response = new HashMap<>();
            response.put("underperformers", underperformers);
            response.put("thresholdScore", thresholdScore);
            response.put("totalCount", underperformers.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting underperforming workers: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get underperforming workers");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get system performance statistics
     */
    @GetMapping("/stats/system-performance")
    public ResponseEntity<Map<String, Object>> getSystemPerformanceStatistics() {
        try {
            logger.debug("Getting system performance statistics");
            
            Map<String, Object> systemStats = workerPerformanceService.getSystemPerformanceStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("systemPerformance", systemStats);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting system performance statistics: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get system performance statistics");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get load balancing queue status
     */
    @GetMapping("/load-balancing/queue-status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        try {
            logger.debug("Getting load balancing queue status");
            
            Map<String, Object> queueStatus = loadBalancingService.getQueueStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("queueStatus", queueStatus);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting queue status: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get queue status");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get load balancing metrics
     */
    @GetMapping("/load-balancing/metrics")
    public ResponseEntity<Map<String, Object>> getLoadBalancingMetrics() {
        try {
            logger.debug("Getting load balancing metrics");
            
            LoadBalancingService.LoadBalancingMetrics metrics = loadBalancingService.getLoadBalancingMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalJobsBalanced", metrics.getTotalJobsBalanced());
            response.put("successfulBalancing", metrics.getSuccessfulBalancing());
            response.put("failedBalancing", metrics.getFailedBalancing());
            response.put("successRate", metrics.getSuccessRate());
            response.put("averageBalancingTime", metrics.getAverageBalancingTime());
            response.put("lastBalancing", metrics.getLastBalancing());
            response.put("algorithmUsage", metrics.getAlgorithmUsage());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting load balancing metrics: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get load balancing metrics");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get worker load information
     */
    @GetMapping("/load-balancing/worker-loads")
    public ResponseEntity<Map<String, Object>> getWorkerLoadInfo() {
        try {
            logger.debug("Getting worker load information");
            
            Map<String, LoadBalancingService.WorkerLoadInfo> workerLoads = 
                    loadBalancingService.getWorkerLoadInfo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("workerLoads", workerLoads);
            response.put("totalWorkers", workerLoads.size());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting worker load info: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get worker load information");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Enqueue a job for load balancing
     */
    @PostMapping("/load-balancing/enqueue")
    public ResponseEntity<Map<String, Object>> enqueueJob(@RequestBody Map<String, Object> jobData) {
        try {
            logger.info("Enqueue job request received: {}", jobData);
            
            // Note: In a real implementation, you would create a Job object from the jobData
            // For demonstration, we'll create a mock response
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ENQUEUED");
            response.put("message", "Job enqueued successfully for load balancing");
            response.put("jobData", jobData);
            response.put("queuePosition", "PENDING_CALCULATION");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error enqueueing job: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to enqueue job");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Health check endpoint for assignment service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getAssignmentServiceHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("service", "JobAssignmentService");
            health.put("timestamp", java.time.LocalDateTime.now());
            health.put("version", "1.0.0");
            
            // Add some basic metrics
            health.put("features", Map.of(
                    "assignmentStrategies", "ROUND_ROBIN, CAPACITY_AWARE, PERFORMANCE_BASED, INTELLIGENT, LEAST_LOADED, PRIORITY_BASED",
                    "loadBalancingEnabled", true,
                    "performanceMonitoringEnabled", true,
                    "queueManagementEnabled", true
            ));
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Error getting assignment service health: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
