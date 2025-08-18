package com.jobscheduler.controller;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobDependency;
import com.jobscheduler.service.DependencyGraphService;
import com.jobscheduler.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dependencies")
@CrossOrigin(origins = "*")
public class DependencyGraphController {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphController.class);
    
    @Autowired
    private DependencyGraphService dependencyGraphService;
    
    @Autowired
    private JobService jobService;
    
    /**
     * Build/Rebuild the dependency graph from database
     */
    @PostMapping("/graph/build")
    public ResponseEntity<Map<String, Object>> buildDependencyGraph() {
        try {
            dependencyGraphService.buildDependencyGraph();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dependency graph built successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error building dependency graph: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Add a dependency between two jobs
     */
    @PostMapping("/{childJobId}/depends-on/{parentJobId}")
    public ResponseEntity<Map<String, Object>> addDependency(
            @PathVariable Long childJobId,
            @PathVariable Long parentJobId,
            @RequestBody(required = false) Map<String, Object> options) {
        
        logger.info("Adding dependency: job {} depends on job {}", childJobId, parentJobId);
        
        try {
            // Validate that jobs exist
            Job childJob = jobService.getJobById(childJobId).orElse(null);
            Job parentJob = jobService.getJobById(parentJobId).orElse(null);
            
            if (childJob == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Child job not found: " + childJobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            if (parentJob == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Parent job not found: " + parentJobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Prevent self-dependency
            if (childJobId.equals(parentJobId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Job cannot depend on itself");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            boolean success = dependencyGraphService.addDependency(childJobId, parentJobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            
            if (success) {
                response.put("message", "Dependency added successfully");
                response.put("childJob", Map.of(
                    "id", childJob.getId(),
                    "name", childJob.getName(),
                    "status", childJob.getStatus()
                ));
                response.put("parentJob", Map.of(
                    "id", parentJob.getId(),
                    "name", parentJob.getName(),
                    "status", parentJob.getStatus()
                ));
            } else {
                response.put("error", "Failed to add dependency (possible cycle detection)");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error adding dependency: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Remove a dependency between two jobs
     */
    @DeleteMapping("/{childJobId}/depends-on/{parentJobId}")
    public ResponseEntity<Map<String, Object>> removeDependency(
            @PathVariable Long childJobId,
            @PathVariable Long parentJobId) {
        
        logger.info("Removing dependency: job {} depends on job {}", childJobId, parentJobId);
        
        try {
            boolean success = dependencyGraphService.removeDependency(childJobId, parentJobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            
            if (success) {
                response.put("message", "Dependency removed successfully");
            } else {
                response.put("error", "Failed to remove dependency");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error removing dependency: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get jobs ready for execution (no unsatisfied dependencies)
     */
    @GetMapping("/ready-jobs")
    public ResponseEntity<Map<String, Object>> getJobsReadyForExecution() {
        try {
            List<Job> readyJobs = dependencyGraphService.getJobsReadyForExecution();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("readyJobs", readyJobs.stream().map(job -> Map.of(
                "id", job.getId(),
                "name", job.getName(),
                "status", job.getStatus(),
                "priority", job.getPriority(),
                "createdAt", job.getCreatedAt()
            )).toList());
            response.put("count", readyJobs.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting ready jobs: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Perform topological sort of all jobs
     */
    @GetMapping("/topological-sort")
    public ResponseEntity<Map<String, Object>> getTopologicalSort() {
        try {
            List<Long> sortedJobIds = dependencyGraphService.topologicalSort();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            
            if (sortedJobIds.isEmpty()) {
                response.put("error", "Cycle detected in dependency graph");
                response.put("sortedJobs", Collections.emptyList());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else {
                // Get job details for sorted IDs
                List<Map<String, Object>> sortedJobs = new ArrayList<>();
                for (Long jobId : sortedJobIds) {
                    Job job = jobService.getJobById(jobId).orElse(null);
                    if (job != null) {
                        sortedJobs.add(Map.of(
                            "id", job.getId(),
                            "name", job.getName(),
                            "status", job.getStatus(),
                            "priority", job.getPriority()
                        ));
                    }
                }
                
                response.put("sortedJobs", sortedJobs);
                response.put("count", sortedJobs.size());
            }
            
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error performing topological sort: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get execution plan (jobs grouped by execution batches)
     */
    @GetMapping("/execution-plan")
    public ResponseEntity<Map<String, Object>> getExecutionPlan() {
        try {
            List<List<Job>> executionPlan = dependencyGraphService.getExecutionPlan();
            
            List<Map<String, Object>> batches = new ArrayList<>();
            for (int i = 0; i < executionPlan.size(); i++) {
                List<Job> batch = executionPlan.get(i);
                Map<String, Object> batchInfo = new HashMap<>();
                batchInfo.put("batchNumber", i + 1);
                batchInfo.put("jobs", batch.stream().map(job -> Map.of(
                    "id", job.getId(),
                    "name", job.getName(),
                    "status", job.getStatus(),
                    "priority", job.getPriority()
                )).toList());
                batchInfo.put("jobCount", batch.size());
                batches.add(batchInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("executionBatches", batches);
            response.put("totalBatches", batches.size());
            response.put("totalJobs", batches.stream().mapToInt(b -> (Integer) b.get("jobCount")).sum());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting execution plan: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get dependencies for a specific job
     */
    @GetMapping("/{jobId}/dependencies")
    public ResponseEntity<Map<String, Object>> getJobDependencies(@PathVariable Long jobId) {
        try {
            Job job = jobService.getJobById(jobId).orElse(null);
            if (job == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Job not found: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Set<Long> dependencyIds = dependencyGraphService.getJobDependencies(jobId);
            Set<Long> dependentIds = dependencyGraphService.getJobDependents(jobId);
            
            // Get job details for dependencies
            List<Map<String, Object>> dependencies = new ArrayList<>();
            for (Long depId : dependencyIds) {
                Job depJob = jobService.getJobById(depId).orElse(null);
                if (depJob != null) {
                    dependencies.add(Map.of(
                        "id", depJob.getId(),
                        "name", depJob.getName(),
                        "status", depJob.getStatus(),
                        "priority", depJob.getPriority()
                    ));
                }
            }
            
            // Get job details for dependents
            List<Map<String, Object>> dependents = new ArrayList<>();
            for (Long depId : dependentIds) {
                Job depJob = jobService.getJobById(depId).orElse(null);
                if (depJob != null) {
                    dependents.add(Map.of(
                        "id", depJob.getId(),
                        "name", depJob.getName(),
                        "status", depJob.getStatus(),
                        "priority", depJob.getPriority()
                    ));
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("job", Map.of(
                "id", job.getId(),
                "name", job.getName(),
                "status", job.getStatus()
            ));
            response.put("dependencies", dependencies); // Jobs this job depends on
            response.put("dependents", dependents);     // Jobs that depend on this job
            response.put("dependencyCount", dependencies.size());
            response.put("dependentCount", dependents.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting job dependencies: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Update job completion and get newly available jobs
     */
    @PostMapping("/{jobId}/complete")
    public ResponseEntity<Map<String, Object>> updateJobCompletion(@PathVariable Long jobId) {
        try {
            Job job = jobService.getJobById(jobId).orElse(null);
            if (job == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Job not found: " + jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            List<Job> newlyReadyJobs = dependencyGraphService.updateJobCompletion(jobId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Job completion processed successfully");
            response.put("completedJob", Map.of(
                "id", job.getId(),
                "name", job.getName(),
                "status", job.getStatus()
            ));
            response.put("newlyReadyJobs", newlyReadyJobs.stream().map(readyJob -> Map.of(
                "id", readyJob.getId(),
                "name", readyJob.getName(),
                "status", readyJob.getStatus(),
                "priority", readyJob.getPriority()
            )).toList());
            response.put("newlyReadyCount", newlyReadyJobs.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating job completion: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get dependency graph statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDependencyGraphStats() {
        try {
            Map<String, Object> stats = dependencyGraphService.getDependencyGraphStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", stats);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting dependency graph stats: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Validate dependency graph integrity
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateDependencyGraph() {
        try {
            List<String> errors = dependencyGraphService.validateDependencyGraph();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", errors.isEmpty());
            response.put("valid", errors.isEmpty());
            response.put("errors", errors);
            response.put("errorCount", errors.size());
            response.put("timestamp", System.currentTimeMillis());
            
            if (!errors.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error validating dependency graph: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Batch add multiple dependencies
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchAddDependencies(
            @RequestBody @Valid List<Map<String, Long>> dependencies) {
        
        logger.info("Batch adding {} dependencies", dependencies.size());
        
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (Map<String, Long> dep : dependencies) {
                Long childJobId = dep.get("childJobId");
                Long parentJobId = dep.get("parentJobId");
                
                if (childJobId == null || parentJobId == null) {
                    results.add(Map.of(
                        "childJobId", childJobId != null ? childJobId : "null",
                        "parentJobId", parentJobId != null ? parentJobId : "null",
                        "success", false,
                        "error", "Missing job IDs"
                    ));
                    failureCount++;
                    continue;
                }
                
                boolean success = dependencyGraphService.addDependency(childJobId, parentJobId);
                results.add(Map.of(
                    "childJobId", childJobId,
                    "parentJobId", parentJobId,
                    "success", success,
                    "error", success ? "" : "Failed to add dependency (possible cycle)"
                ));
                
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", failureCount == 0);
            response.put("results", results);
            response.put("totalRequests", dependencies.size());
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error batch adding dependencies: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
