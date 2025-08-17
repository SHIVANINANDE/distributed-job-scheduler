package com.jobscheduler.controller;

import com.jobscheduler.model.JobDependency;
import com.jobscheduler.model.JobDependency.DependencyType;
import com.jobscheduler.model.JobDependency.FailureAction;
import com.jobscheduler.service.JobDependencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/dependencies")
@CrossOrigin(origins = "http://localhost:3000")
public class JobDependencyController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobDependencyController.class);
    
    @Autowired
    private JobDependencyService jobDependencyService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Job Dependency API is running!");
    }
    
    // Get all dependencies with pagination
    @GetMapping
    public ResponseEntity<Page<JobDependency>> getAllDependencies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<JobDependency> dependencies = jobDependencyService.getAllDependencies(pageable);
        return ResponseEntity.ok(dependencies);
    }
    
    // Get dependency by ID
    @GetMapping("/{id}")
    public ResponseEntity<JobDependency> getDependencyById(@PathVariable Long id) {
        Optional<JobDependency> dependency = jobDependencyService.getDependencyById(id);
        return dependency.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }
    
    // Get dependencies for a specific job
    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<JobDependency>> getDependenciesForJob(@PathVariable Long jobId) {
        List<JobDependency> dependencies = jobDependencyService.getDependenciesForJob(jobId);
        return ResponseEntity.ok(dependencies);
    }
    
    // Get unsatisfied dependencies for a job
    @GetMapping("/job/{jobId}/unsatisfied")
    public ResponseEntity<List<JobDependency>> getUnsatisfiedDependencies(@PathVariable Long jobId) {
        List<JobDependency> dependencies = jobDependencyService.getUnsatisfiedDependencies(jobId);
        return ResponseEntity.ok(dependencies);
    }
    
    // Get blocking dependencies for a job
    @GetMapping("/job/{jobId}/blocking")
    public ResponseEntity<List<JobDependency>> getBlockingDependencies(@PathVariable Long jobId) {
        List<JobDependency> dependencies = jobDependencyService.getBlockingDependencies(jobId);
        return ResponseEntity.ok(dependencies);
    }
    
    // Check if all dependencies are satisfied for a job
    @GetMapping("/job/{jobId}/satisfied")
    public ResponseEntity<Map<String, Boolean>> areAllDependenciesSatisfied(@PathVariable Long jobId) {
        boolean satisfied = jobDependencyService.areAllDependenciesSatisfied(jobId);
        return ResponseEntity.ok(Map.of("allSatisfied", satisfied));
    }
    
    // Check if blocking dependencies are satisfied
    @GetMapping("/job/{jobId}/blocking-satisfied")
    public ResponseEntity<Map<String, Boolean>> areBlockingDependenciesSatisfied(@PathVariable Long jobId) {
        boolean satisfied = jobDependencyService.areBlockingDependenciesSatisfied(jobId);
        return ResponseEntity.ok(Map.of("blockingSatisfied", satisfied));
    }
    
    // Create a new job dependency
    @PostMapping
    public ResponseEntity<JobDependency> createJobDependency(@Valid @RequestBody JobDependency dependency) {
        try {
            logger.info("Creating dependency: Job {} depends on Job {}", 
                       dependency.getJobId(), dependency.getDependencyJobId());
            JobDependency createdDependency = jobDependencyService.createJobDependency(dependency);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDependency);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid dependency creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Create dependency with simple request
    @PostMapping("/simple")
    public ResponseEntity<JobDependency> createSimpleDependency(@RequestBody SimpleDependencyRequest request) {
        try {
            JobDependency dependency = new JobDependency(
                request.getJobId(), 
                request.getDependencyJobId(), 
                request.getDependencyType() != null ? request.getDependencyType() : DependencyType.MUST_COMPLETE
            );
            
            if (request.getIsOptional() != null) {
                dependency.setIsOptional(request.getIsOptional());
            }
            if (request.getDependencyPriority() != null) {
                dependency.setDependencyPriority(request.getDependencyPriority());
            }
            if (request.getTimeoutMinutes() != null) {
                dependency.setTimeoutMinutes(request.getTimeoutMinutes());
            }
            if (request.getFailureAction() != null) {
                dependency.setFailureAction(request.getFailureAction());
            }
            
            JobDependency createdDependency = jobDependencyService.createJobDependency(dependency);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDependency);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid simple dependency creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Update a dependency
    @PutMapping("/{id}")
    public ResponseEntity<JobDependency> updateDependency(@PathVariable Long id, @Valid @RequestBody JobDependency dependency) {
        Optional<JobDependency> existingDependency = jobDependencyService.getDependencyById(id);
        if (existingDependency.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        dependency.setId(id);
        JobDependency updatedDependency = jobDependencyService.updateDependency(dependency);
        return ResponseEntity.ok(updatedDependency);
    }
    
    // Delete a dependency
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDependency(@PathVariable Long id) {
        Optional<JobDependency> dependency = jobDependencyService.getDependencyById(id);
        if (dependency.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        jobDependencyService.deleteDependency(id);
        return ResponseEntity.noContent().build();
    }
    
    // Mark dependency as satisfied
    @PostMapping("/{id}/satisfy")
    public ResponseEntity<JobDependency> markDependencyAsSatisfied(@PathVariable Long id) {
        try {
            JobDependency dependency = jobDependencyService.markDependencyAsSatisfied(id);
            return ResponseEntity.ok(dependency);
        } catch (RuntimeException e) {
            logger.error("Failed to mark dependency {} as satisfied: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Get jobs ready to execute
    @GetMapping("/ready-jobs")
    public ResponseEntity<List<Long>> getJobsReadyToExecute() {
        List<Long> jobIds = jobDependencyService.getJobsReadyToExecute();
        return ResponseEntity.ok(jobIds);
    }
    
    // Get dependency chain for a job
    @GetMapping("/job/{jobId}/chain")
    public ResponseEntity<List<Object[]>> getDependencyChain(@PathVariable Long jobId) {
        List<Object[]> chain = jobDependencyService.getDependencyChain(jobId);
        return ResponseEntity.ok(chain);
    }
    
    // Check for circular dependencies
    @GetMapping("/circular-check")
    public ResponseEntity<Map<String, Boolean>> checkCircularDependency(
            @RequestParam Long jobId, 
            @RequestParam Long dependencyJobId) {
        boolean wouldCreateCircle = jobDependencyService.wouldCreateCircularDependency(jobId, dependencyJobId);
        return ResponseEntity.ok(Map.of("wouldCreateCircularDependency", wouldCreateCircle));
    }
    
    // Get dependencies needing check
    @GetMapping("/needs-check")
    public ResponseEntity<List<JobDependency>> getDependenciesNeedingCheck() {
        List<JobDependency> dependencies = jobDependencyService.getDependenciesNeedingCheck();
        return ResponseEntity.ok(dependencies);
    }
    
    // Get high priority dependencies
    @GetMapping("/high-priority")
    public ResponseEntity<List<JobDependency>> getHighPriorityDependencies() {
        List<JobDependency> dependencies = jobDependencyService.getHighPriorityDependencies();
        return ResponseEntity.ok(dependencies);
    }
    
    // Get timed out dependencies
    @GetMapping("/timed-out")
    public ResponseEntity<List<JobDependency>> getTimedOutDependencies() {
        List<JobDependency> dependencies = jobDependencyService.getTimedOutDependencies();
        return ResponseEntity.ok(dependencies);
    }
    
    // Process dependency check
    @PostMapping("/{id}/check")
    public ResponseEntity<Void> processDependencyCheck(@PathVariable Long id) {
        Optional<JobDependency> dependency = jobDependencyService.getDependencyById(id);
        if (dependency.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        jobDependencyService.processDependencyCheck(id);
        return ResponseEntity.ok().build();
    }
    
    // Get dependency statistics
    @GetMapping("/statistics")
    public ResponseEntity<JobDependencyService.DependencyStatistics> getDependencyStatistics() {
        JobDependencyService.DependencyStatistics stats = jobDependencyService.getDependencyStatistics();
        return ResponseEntity.ok(stats);
    }
    
    // Bulk create dependencies
    @PostMapping("/bulk")
    public ResponseEntity<List<JobDependency>> createMultipleDependencies(@RequestBody List<JobDependency> dependencies) {
        List<JobDependency> createdDependencies = jobDependencyService.createMultipleDependencies(dependencies);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDependencies);
    }
    
    // Bulk create simple dependencies
    @PostMapping("/bulk-simple")
    public ResponseEntity<List<JobDependency>> createMultipleSimpleDependencies(@RequestBody BulkDependencyRequest request) {
        List<JobDependency> dependencies = request.getDependencies().stream()
            .map(dep -> {
                JobDependency dependency = new JobDependency(dep.getJobId(), dep.getDependencyJobId());
                if (dep.getDependencyType() != null) {
                    dependency.setDependencyType(dep.getDependencyType());
                }
                if (dep.getIsOptional() != null) {
                    dependency.setIsOptional(dep.getIsOptional());
                }
                return dependency;
            })
            .toList();
            
        List<JobDependency> createdDependencies = jobDependencyService.createMultipleDependencies(dependencies);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDependencies);
    }
    
    // Remove all dependencies for a job
    @DeleteMapping("/job/{jobId}")
    public ResponseEntity<Void> removeAllDependenciesForJob(@PathVariable Long jobId) {
        jobDependencyService.removeAllDependenciesForJob(jobId);
        return ResponseEntity.noContent().build();
    }
    
    // Get dependency types
    @GetMapping("/types")
    public ResponseEntity<DependencyType[]> getDependencyTypes() {
        return ResponseEntity.ok(DependencyType.values());
    }
    
    // Get failure actions
    @GetMapping("/failure-actions")
    public ResponseEntity<FailureAction[]> getFailureActions() {
        return ResponseEntity.ok(FailureAction.values());
    }
    
    // Inner classes for requests
    
    public static class SimpleDependencyRequest {
        private Long jobId;
        private Long dependencyJobId;
        private DependencyType dependencyType;
        private Boolean isOptional;
        private Integer dependencyPriority;
        private Integer timeoutMinutes;
        private FailureAction failureAction;
        
        // Constructors
        public SimpleDependencyRequest() {}
        
        // Getters and Setters
        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }
        
        public Long getDependencyJobId() { return dependencyJobId; }
        public void setDependencyJobId(Long dependencyJobId) { this.dependencyJobId = dependencyJobId; }
        
        public DependencyType getDependencyType() { return dependencyType; }
        public void setDependencyType(DependencyType dependencyType) { this.dependencyType = dependencyType; }
        
        public Boolean getIsOptional() { return isOptional; }
        public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }
        
        public Integer getDependencyPriority() { return dependencyPriority; }
        public void setDependencyPriority(Integer dependencyPriority) { this.dependencyPriority = dependencyPriority; }
        
        public Integer getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        
        public FailureAction getFailureAction() { return failureAction; }
        public void setFailureAction(FailureAction failureAction) { this.failureAction = failureAction; }
    }
    
    public static class BulkDependencyRequest {
        private List<SimpleDependencyRequest> dependencies;
        
        public BulkDependencyRequest() {}
        
        public List<SimpleDependencyRequest> getDependencies() { return dependencies; }
        public void setDependencies(List<SimpleDependencyRequest> dependencies) { this.dependencies = dependencies; }
    }
}
