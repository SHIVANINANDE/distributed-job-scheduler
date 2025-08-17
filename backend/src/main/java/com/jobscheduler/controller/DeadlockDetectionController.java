package com.jobscheduler.controller;

import com.jobscheduler.service.DeadlockDetectionService;
import com.jobscheduler.service.DeadlockDetectionService.DeadlockDetectionResult;
import com.jobscheduler.service.DeadlockDetectionService.DependencyValidationResult;
import com.jobscheduler.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/deadlock")
@CrossOrigin(origins = "*")
public class DeadlockDetectionController {
    
    private static final Logger logger = LoggerFactory.getLogger(DeadlockDetectionController.class);
    
    @Autowired
    private DeadlockDetectionService deadlockService;
    
    @Autowired
    private JobService jobService;
    
    /**
     * Perform comprehensive deadlock detection
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detectDeadlocks() {
        try {
            logger.info("Starting deadlock detection scan...");
            
            DeadlockDetectionResult result = deadlockService.detectDeadlocks();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasDeadlock", result.hasDeadlock());
            response.put("cycleCount", result.getCycleCount());
            response.put("detectionTime", result.getDetectionTime());
            
            // Format cycles for API response
            List<Map<String, Object>> cyclesData = result.getCycles().stream().map(cycle -> {
                Map<String, Object> cycleData = new HashMap<>();
                cycleData.put("jobIds", cycle.getJobIds());
                cycleData.put("jobNames", cycle.getJobNames());
                cycleData.put("severity", cycle.getSeverity());
                cycleData.put("description", cycle.getDescription());
                cycleData.put("cycleLength", cycle.getCycleLength());
                cycleData.put("isHighSeverity", cycle.isHighSeverity());
                cycleData.put("dependencyIds", cycle.getDependencyIds());
                return cycleData;
            }).collect(Collectors.toList());
            
            response.put("cycles", cyclesData);
            response.put("warnings", result.getWarnings());
            response.put("statistics", result.getStatistics());
            response.put("hasWarnings", result.hasWarnings());
            
            // Determine response status based on severity
            HttpStatus status = HttpStatus.OK;
            if (result.hasDeadlock()) {
                boolean hasHighSeverity = result.getCycles().stream().anyMatch(c -> c.isHighSeverity());
                status = hasHighSeverity ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED;
            }
            
            logger.info("Deadlock detection completed. Found {} cycles with {} warnings", 
                       result.getCycleCount(), result.getWarnings().size());
            
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            logger.error("Error during deadlock detection: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Validate if adding a dependency would create a deadlock
     */
    @PostMapping("/validate-dependency")
    public ResponseEntity<Map<String, Object>> validateDependency(
            @RequestBody @Valid Map<String, Long> request) {
        
        try {
            Long childJobId = request.get("childJobId");
            Long parentJobId = request.get("parentJobId");
            
            if (childJobId == null || parentJobId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Both childJobId and parentJobId are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            logger.debug("Validating dependency: {} depends on {}", childJobId, parentJobId);
            
            DependencyValidationResult result = deadlockService.validateDependencyAddition(childJobId, parentJobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isValid", result.isValid());
            response.put("message", result.getMessage());
            response.put("severity", result.getSeverity());
            response.put("affectedJobIds", result.getAffectedJobIds());
            response.put("warnings", result.getWarnings());
            response.put("hasWarnings", result.hasWarnings());
            response.put("isHighSeverity", result.isHighSeverity());
            response.put("timestamp", LocalDateTime.now());
            
            // Add job details for context
            Map<String, Object> jobDetails = new HashMap<>();
            try {
                var childJob = jobService.getJobById(childJobId);
                var parentJob = jobService.getJobById(parentJobId);
                
                if (childJob != null) {
                    jobDetails.put("childJob", Map.of(
                        "id", childJob.getId(),
                        "name", childJob.getName(),
                        "status", childJob.getStatus()
                    ));
                }
                
                if (parentJob != null) {
                    jobDetails.put("parentJob", Map.of(
                        "id", parentJob.getId(),
                        "name", parentJob.getName(),
                        "status", parentJob.getStatus()
                    ));
                }
                
                response.put("jobDetails", jobDetails);
            } catch (Exception e) {
                logger.warn("Could not fetch job details: {}", e.getMessage());
            }
            
            HttpStatus status = result.isValid() ? HttpStatus.OK : 
                              (result.isHighSeverity() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
            
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            logger.error("Error validating dependency: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Validate multiple dependencies in batch
     */
    @PostMapping("/validate-dependencies-batch")
    public ResponseEntity<Map<String, Object>> validateDependenciesBatch(
            @RequestBody @Valid List<Map<String, Long>> dependencies) {
        
        try {
            logger.info("Batch validating {} dependencies", dependencies.size());
            
            List<Map<String, Object>> results = new ArrayList<>();
            int validCount = 0;
            int invalidCount = 0;
            int highSeverityCount = 0;
            
            for (Map<String, Long> dep : dependencies) {
                Long childJobId = dep.get("childJobId");
                Long parentJobId = dep.get("parentJobId");
                
                if (childJobId == null || parentJobId == null) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("childJobId", childJobId);
                    result.put("parentJobId", parentJobId);
                    result.put("isValid", false);
                    result.put("message", "Missing job IDs");
                    result.put("severity", 9);
                    results.add(result);
                    invalidCount++;
                    continue;
                }
                
                try {
                    DependencyValidationResult validation = deadlockService.validateDependencyAddition(childJobId, parentJobId);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("childJobId", childJobId);
                    result.put("parentJobId", parentJobId);
                    result.put("isValid", validation.isValid());
                    result.put("message", validation.getMessage());
                    result.put("severity", validation.getSeverity());
                    result.put("affectedJobIds", validation.getAffectedJobIds());
                    result.put("warnings", validation.getWarnings());
                    result.put("isHighSeverity", validation.isHighSeverity());
                    
                    results.add(result);
                    
                    if (validation.isValid()) {
                        validCount++;
                    } else {
                        invalidCount++;
                        if (validation.isHighSeverity()) {
                            highSeverityCount++;
                        }
                    }
                    
                } catch (Exception e) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("childJobId", childJobId);
                    result.put("parentJobId", parentJobId);
                    result.put("isValid", false);
                    result.put("message", "Validation error: " + e.getMessage());
                    result.put("severity", 7);
                    results.add(result);
                    invalidCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("summary", Map.of(
                "totalRequests", dependencies.size(),
                "validCount", validCount,
                "invalidCount", invalidCount,
                "highSeverityCount", highSeverityCount,
                "successRate", dependencies.size() > 0 ? (double) validCount / dependencies.size() : 0.0
            ));
            response.put("timestamp", LocalDateTime.now());
            
            HttpStatus status = invalidCount == 0 ? HttpStatus.OK : 
                              (highSeverityCount > 0 ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED);
            
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            logger.error("Error in batch dependency validation: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get deadlock detection statistics and health
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDeadlockStats() {
        try {
            Map<String, Object> cacheStats = deadlockService.getCacheStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cacheStatistics", cacheStats);
            response.put("timestamp", LocalDateTime.now());
            
            // Add system health indicators
            Map<String, Object> healthIndicators = new HashMap<>();
            healthIndicators.put("cacheHealth", cacheStats.get("validEntries"));
            healthIndicators.put("memoryUsage", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            healthIndicators.put("systemLoad", getSystemLoad());
            
            response.put("healthIndicators", healthIndicators);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting deadlock stats: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Clear deadlock detection cache
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {
            deadlockService.clearCache();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Deadlock detection cache cleared successfully");
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Deadlock detection cache cleared via API");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error clearing deadlock cache: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Advanced deadlock analysis with recommendations
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDeadlocks() {
        try {
            logger.info("Starting advanced deadlock analysis...");
            
            DeadlockDetectionResult detection = deadlockService.detectDeadlocks();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("analysis", analyzeDetectionResult(detection));
            response.put("recommendations", generateRecommendations(detection));
            response.put("riskAssessment", assessRisk(detection));
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during deadlock analysis: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Health check endpoint for deadlock detection system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            
            // Check cache health
            Map<String, Object> cacheStats = deadlockService.getCacheStatistics();
            boolean cacheHealthy = (Integer) cacheStats.get("cacheSize") < 10000; // Arbitrary threshold
            health.put("cacheHealthy", cacheHealthy);
            
            // Check memory usage
            Runtime runtime = Runtime.getRuntime();
            long memoryUsage = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) memoryUsage / maxMemory * 100;
            health.put("memoryUsagePercent", memoryUsagePercent);
            health.put("memoryHealthy", memoryUsagePercent < 80);
            
            // Overall health
            boolean overallHealthy = cacheHealthy && memoryUsagePercent < 80;
            health.put("overallHealthy", overallHealthy);
            
            HttpStatus status = overallHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(status).body(health);
            
        } catch (Exception e) {
            logger.error("Error in health check: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
    
    /**
     * Helper methods for analysis
     */
    
    private Map<String, Object> analyzeDetectionResult(DeadlockDetectionResult result) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("totalCycles", result.getCycleCount());
        analysis.put("hasDeadlock", result.hasDeadlock());
        analysis.put("hasWarnings", result.hasWarnings());
        
        if (result.hasDeadlock()) {
            List<Integer> cycleLengths = result.getCycles().stream()
                .mapToInt(c -> c.getCycleLength())
                .boxed()
                .collect(Collectors.toList());
            
            analysis.put("cycleLengths", cycleLengths);
            analysis.put("averageCycleLength", cycleLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0));
            analysis.put("maxCycleLength", cycleLengths.stream().mapToInt(Integer::intValue).max().orElse(0));
            analysis.put("minCycleLength", cycleLengths.stream().mapToInt(Integer::intValue).min().orElse(0));
            
            long highSeverityCount = result.getCycles().stream().mapToLong(c -> c.isHighSeverity() ? 1 : 0).sum();
            analysis.put("highSeverityCycles", highSeverityCount);
            analysis.put("severityDistribution", calculateSeverityDistribution(result.getCycles()));
        }
        
        analysis.put("detectionStatistics", result.getStatistics());
        
        return analysis;
    }
    
    private List<String> generateRecommendations(DeadlockDetectionResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.hasDeadlock()) {
            recommendations.add("Immediate action required: Deadlocks detected in dependency graph");
            
            for (var cycle : result.getCycles()) {
                if (cycle.isHighSeverity()) {
                    recommendations.add("High priority: Resolve cycle involving jobs " + cycle.getJobIds());
                }
            }
            
            recommendations.add("Consider breaking cycles by removing non-critical dependencies");
            recommendations.add("Review job design to minimize interdependencies");
        } else {
            recommendations.add("No deadlocks detected - dependency graph is healthy");
        }
        
        if (result.hasWarnings()) {
            recommendations.add("Review warnings for potential future issues");
        }
        
        Map<String, Object> stats = result.getStatistics();
        if (stats.containsKey("detectionTimeMs")) {
            long detectionTime = ((Number) stats.get("detectionTimeMs")).longValue();
            if (detectionTime > 5000) {
                recommendations.add("Detection time is high - consider optimizing dependency graph structure");
            }
        }
        
        return recommendations;
    }
    
    private Map<String, Object> assessRisk(DeadlockDetectionResult result) {
        Map<String, Object> risk = new HashMap<>();
        
        if (!result.hasDeadlock()) {
            risk.put("level", "LOW");
            risk.put("score", 1);
            risk.put("description", "No deadlocks detected");
        } else {
            long highSeverityCount = result.getCycles().stream().mapToLong(c -> c.isHighSeverity() ? 1 : 0).sum();
            int totalCycles = result.getCycleCount();
            
            if (highSeverityCount > 0) {
                risk.put("level", "HIGH");
                risk.put("score", 9);
                risk.put("description", "High severity deadlocks present");
            } else if (totalCycles > 5) {
                risk.put("level", "MEDIUM");
                risk.put("score", 6);
                risk.put("description", "Multiple deadlock cycles detected");
            } else {
                risk.put("level", "MEDIUM");
                risk.put("score", 4);
                risk.put("description", "Deadlock cycles present but manageable");
            }
        }
        
        risk.put("factors", Arrays.asList(
            "Number of cycles: " + result.getCycleCount(),
            "High severity cycles: " + result.getCycles().stream().mapToLong(c -> c.isHighSeverity() ? 1 : 0).sum(),
            "Warnings: " + result.getWarnings().size()
        ));
        
        return risk;
    }
    
    private Map<String, Integer> calculateSeverityDistribution(List<DeadlockDetectionService.DeadlockCycle> cycles) {
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("low", 0);
        distribution.put("medium", 0);
        distribution.put("high", 0);
        
        for (var cycle : cycles) {
            int severity = cycle.getSeverity();
            if (severity <= 3) {
                distribution.put("low", distribution.get("low") + 1);
            } else if (severity <= 7) {
                distribution.put("medium", distribution.get("medium") + 1);
            } else {
                distribution.put("high", distribution.get("high") + 1);
            }
        }
        
        return distribution;
    }
    
    private double getSystemLoad() {
        try {
            return ((com.sun.management.OperatingSystemMXBean) 
                   java.lang.management.ManagementFactory.getOperatingSystemMXBean())
                   .getCpuLoad();
        } catch (Exception e) {
            return -1.0; // Unable to determine
        }
    }
}
