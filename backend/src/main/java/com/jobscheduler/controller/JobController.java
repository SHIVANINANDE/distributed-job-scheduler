package com.jobscheduler.controller;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.service.JobService;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = "http://localhost:3000")
public class JobController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);
    
    @Autowired
    private JobService jobService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Job Scheduler API is running!");
    }
    
    // Get all jobs with pagination
    @GetMapping
    public ResponseEntity<Page<Job>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Job> jobs = jobService.getAllJobs(pageable);
        return ResponseEntity.ok(jobs);
    }
    
    // Get job by ID
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable Long id) {
        Optional<Job> job = jobService.getJobById(id);
        return job.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    // Create a new job
    @PostMapping
    public ResponseEntity<Job> createJob(@Valid @RequestBody Job job) {
        logger.info("Creating new job: {}", job.getName());
        Job createdJob = jobService.createJob(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdJob);
    }
    
    // Update a job
    @PutMapping("/{id}")
    public ResponseEntity<Job> updateJob(@PathVariable Long id, @Valid @RequestBody Job job) {
        Optional<Job> existingJob = jobService.getJobById(id);
        if (existingJob.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        job.setId(id);
        Job updatedJob = jobService.updateJob(job);
        return ResponseEntity.ok(updatedJob);
    }
    
    // Delete a job
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        Optional<Job> job = jobService.getJobById(id);
        if (job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
    
    // Get jobs by status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Job>> getJobsByStatus(@PathVariable JobStatus status) {
        List<Job> jobs = jobService.getJobsByStatus(status);
        return ResponseEntity.ok(jobs);
    }
    
    // Start a job
    @PostMapping("/{id}/start")
    public ResponseEntity<Job> startJob(@PathVariable Long id) {
        try {
            Job job = jobService.startJob(id);
            return ResponseEntity.ok(job);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Complete a job
    @PostMapping("/{id}/complete")
    public ResponseEntity<Job> completeJob(@PathVariable Long id) {
        try {
            Job job = jobService.completeJob(id);
            return ResponseEntity.ok(job);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Fail a job
    @PostMapping("/{id}/fail")
    public ResponseEntity<Job> failJob(@PathVariable Long id, @RequestBody String errorMessage) {
        try {
            Job job = jobService.failJob(id, errorMessage);
            return ResponseEntity.ok(job);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Cancel a job
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Job> cancelJob(@PathVariable Long id) {
        try {
            Job job = jobService.cancelJob(id);
            return ResponseEntity.ok(job);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Get job statistics
    @GetMapping("/statistics")
    public ResponseEntity<JobService.JobStatistics> getJobStatistics() {
        JobService.JobStatistics stats = jobService.getJobStatistics();
        return ResponseEntity.ok(stats);
    }
    
    // Search jobs by name
    @GetMapping("/search")
    public ResponseEntity<List<Job>> searchJobs(@RequestParam String name) {
        List<Job> jobs = jobService.searchJobsByName(name);
        return ResponseEntity.ok(jobs);
    }
    
    // Get recent jobs
    @GetMapping("/recent")
    public ResponseEntity<List<Job>> getRecentJobs() {
        List<Job> jobs = jobService.getRecentJobs();
        return ResponseEntity.ok(jobs);
    }
}
