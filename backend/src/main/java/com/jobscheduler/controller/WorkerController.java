package com.jobscheduler.controller;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import com.jobscheduler.service.WorkerService;
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
@RequestMapping("/api/v1/workers")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkerController {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkerController.class);
    
    @Autowired
    private WorkerService workerService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Worker API is running!");
    }
    
    // Get all workers with pagination
    @GetMapping
    public ResponseEntity<Page<Worker>> getAllWorkers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Worker> workers = workerService.getAllWorkers(pageable);
        return ResponseEntity.ok(workers);
    }
    
    // Get worker by ID
    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorkerById(@PathVariable Long id) {
        Optional<Worker> worker = workerService.getWorkerById(id);
        return worker.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    // Get worker by worker ID
    @GetMapping("/worker/{workerId}")
    public ResponseEntity<Worker> getWorkerByWorkerId(@PathVariable String workerId) {
        Optional<Worker> worker = workerService.getWorkerByWorkerId(workerId);
        return worker.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    // Create/Register a new worker
    @PostMapping
    public ResponseEntity<Worker> createWorker(@Valid @RequestBody Worker worker) {
        logger.info("Registering new worker: {}", worker.getWorkerId());
        Worker createdWorker = workerService.createWorker(worker);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWorker);
    }
    
    // Update a worker
    @PutMapping("/{id}")
    public ResponseEntity<Worker> updateWorker(@PathVariable Long id, @Valid @RequestBody Worker worker) {
        Optional<Worker> existingWorker = workerService.getWorkerById(id);
        if (existingWorker.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        worker.setId(id);
        Worker updatedWorker = workerService.updateWorker(worker);
        return ResponseEntity.ok(updatedWorker);
    }
    
    // Update worker by worker ID
    @PutMapping("/worker/{workerId}")
    public ResponseEntity<Worker> updateWorkerByWorkerId(@PathVariable String workerId, @Valid @RequestBody Worker worker) {
        Optional<Worker> existingWorker = workerService.getWorkerByWorkerId(workerId);
        if (existingWorker.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        worker.setWorkerId(workerId);
        worker.setId(existingWorker.get().getId());
        Worker updatedWorker = workerService.updateWorker(worker);
        return ResponseEntity.ok(updatedWorker);
    }
    
    // Delete a worker
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorker(@PathVariable Long id) {
        Optional<Worker> worker = workerService.getWorkerById(id);
        if (worker.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        workerService.deleteWorker(id);
        return ResponseEntity.noContent().build();
    }
    
    // Delete worker by worker ID
    @DeleteMapping("/worker/{workerId}")
    public ResponseEntity<Void> deleteWorkerByWorkerId(@PathVariable String workerId) {
        Optional<Worker> worker = workerService.getWorkerByWorkerId(workerId);
        if (worker.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        workerService.deleteWorkerByWorkerId(workerId);
        return ResponseEntity.noContent().build();
    }
    
    // Update worker heartbeat
    @PostMapping("/worker/{workerId}/heartbeat")
    public ResponseEntity<Worker> updateHeartbeat(@PathVariable String workerId) {
        try {
            Worker worker = workerService.updateHeartbeat(workerId);
            return ResponseEntity.ok(worker);
        } catch (RuntimeException e) {
            logger.error("Failed to update heartbeat for worker {}: {}", workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Get available workers
    @GetMapping("/available")
    public ResponseEntity<List<Worker>> getAvailableWorkers() {
        List<Worker> workers = workerService.getAvailableWorkers();
        return ResponseEntity.ok(workers);
    }
    
    // Get available workers ordered by load
    @GetMapping("/available/ordered")
    public ResponseEntity<List<Worker>> getAvailableWorkersOrderByLoad() {
        List<Worker> workers = workerService.getAvailableWorkersOrderByLoad();
        return ResponseEntity.ok(workers);
    }
    
    // Get workers by status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Worker>> getWorkersByStatus(@PathVariable WorkerStatus status) {
        List<Worker> workers = workerService.getWorkersByStatus(status);
        return ResponseEntity.ok(workers);
    }
    
    // Get workers with recent heartbeat
    @GetMapping("/heartbeat/recent")
    public ResponseEntity<List<Worker>> getWorkersWithRecentHeartbeat(
            @RequestParam(defaultValue = "5") int minutes) {
        List<Worker> workers = workerService.getWorkersWithRecentHeartbeat(minutes);
        return ResponseEntity.ok(workers);
    }
    
    // Get potentially dead workers
    @GetMapping("/dead")
    public ResponseEntity<List<Worker>> getPotentiallyDeadWorkers(
            @RequestParam(defaultValue = "10") int minutes) {
        List<Worker> workers = workerService.getPotentiallyDeadWorkers(minutes);
        return ResponseEntity.ok(workers);
    }
    
    // Assign job to worker
    @PostMapping("/worker/{workerId}/jobs/{jobId}/assign")
    public ResponseEntity<Worker> assignJobToWorker(@PathVariable String workerId, @PathVariable Long jobId) {
        try {
            Worker worker = workerService.assignJobToWorker(workerId, jobId);
            return ResponseEntity.ok(worker);
        } catch (IllegalStateException e) {
            logger.warn("Cannot assign job {} to worker {}: {}", jobId, workerId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logger.error("Failed to assign job {} to worker {}: {}", jobId, workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Unassign job from worker
    @PostMapping("/worker/{workerId}/jobs/{jobId}/unassign")
    public ResponseEntity<Worker> unassignJobFromWorker(@PathVariable String workerId, @PathVariable Long jobId) {
        try {
            Worker worker = workerService.unassignJobFromWorker(workerId, jobId);
            return ResponseEntity.ok(worker);
        } catch (RuntimeException e) {
            logger.error("Failed to unassign job {} from worker {}: {}", jobId, workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Record job completion
    @PostMapping("/worker/{workerId}/jobs/{jobId}/complete")
    public ResponseEntity<Worker> recordJobCompletion(
            @PathVariable String workerId, 
            @PathVariable Long jobId,
            @RequestParam boolean successful) {
        try {
            Worker worker = workerService.recordJobCompletion(workerId, jobId, successful);
            return ResponseEntity.ok(worker);
        } catch (RuntimeException e) {
            logger.error("Failed to record job completion for worker {}: {}", workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Update worker status
    @PostMapping("/worker/{workerId}/status")
    public ResponseEntity<Worker> updateWorkerStatus(
            @PathVariable String workerId, 
            @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            WorkerStatus status = WorkerStatus.valueOf(statusStr.toUpperCase());
            Worker worker = workerService.updateWorkerStatus(workerId, status);
            return ResponseEntity.ok(worker);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status value: {}", statusUpdate.get("status"));
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logger.error("Failed to update status for worker {}: {}", workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Get worker statistics
    @GetMapping("/statistics")
    public ResponseEntity<WorkerService.WorkerStatistics> getWorkerStatistics() {
        WorkerService.WorkerStatistics stats = workerService.getWorkerStatistics();
        return ResponseEntity.ok(stats);
    }
    
    // Find best worker for job
    @GetMapping("/best-worker")
    public ResponseEntity<Worker> findBestWorkerForJob(@RequestParam int priority) {
        Optional<Worker> worker = workerService.findBestWorkerForJob(priority);
        return worker.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    // Search workers by name
    @GetMapping("/search")
    public ResponseEntity<List<Worker>> searchWorkers(@RequestParam String name) {
        List<Worker> workers = workerService.searchWorkersByName(name);
        return ResponseEntity.ok(workers);
    }
    
    // Cleanup dead workers
    @PostMapping("/cleanup/dead")
    public ResponseEntity<Map<String, Integer>> cleanupDeadWorkers(
            @RequestParam(defaultValue = "15") int minutes) {
        int cleanedUp = workerService.cleanupDeadWorkers(minutes);
        return ResponseEntity.ok(Map.of("cleanedUp", cleanedUp));
    }
    
    // Worker registration with detailed information
    @PostMapping("/register")
    public ResponseEntity<Worker> registerWorker(@Valid @RequestBody WorkerRegistrationRequest request) {
        logger.info("Registering worker with detailed info: {}", request.getWorkerId());
        
        Worker worker = new Worker(request.getWorkerId(), request.getName());
        worker.setHostName(request.getHostName());
        worker.setHostAddress(request.getHostAddress());
        worker.setPort(request.getPort());
        worker.setMaxConcurrentJobs(request.getMaxConcurrentJobs());
        worker.setCapabilities(request.getCapabilities());
        worker.setTags(request.getTags());
        worker.setVersion(request.getVersion());
        worker.setPriorityThreshold(request.getPriorityThreshold());
        worker.setWorkerLoadFactor(request.getWorkerLoadFactor());
        
        Worker registeredWorker = workerService.createWorker(worker);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredWorker);
    }
    
    // Worker deregistration
    @PostMapping("/worker/{workerId}/deregister")
    public ResponseEntity<Void> deregisterWorker(@PathVariable String workerId) {
        try {
            // Set worker status to inactive instead of deleting
            workerService.updateWorkerStatus(workerId, WorkerStatus.INACTIVE);
            logger.info("Deregistered worker: {}", workerId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            logger.error("Failed to deregister worker {}: {}", workerId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Inner class for worker registration request
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
}
