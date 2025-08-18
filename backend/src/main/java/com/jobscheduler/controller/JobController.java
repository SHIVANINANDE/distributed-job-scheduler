package com.jobscheduler.controller;

import com.jobscheduler.dto.JobDTOs;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobPriority;
import com.jobscheduler.service.JobService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private JobDependencyService jobDependencyService;

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
    
    // Create a new job with dependencies - Enhanced Implementation
    @PostMapping
    public ResponseEntity<JobDTOs.JobDetailsResponse> createJobWithDependencies(@Valid @RequestBody JobDTOs.CreateJobRequest request) {
        try {
            logger.info("Creating new job: {} with priority: {}", request.getName(), request.getPriority());
            
            // Create the main job
            Job job = new Job();
            job.setName(request.getName());
            job.setDescription(request.getDescription());
            job.setPriority(convertPriorityToInteger(request.getPriority()));
            job.setJobType(request.getJobType());
            job.setParameters(request.getParameters());
            job.setMaxRetries(request.getMaxRetries());
            job.setScheduledAt(request.getScheduledAt());
            job.setAssignedWorkerId(request.getWorkerId());
            
            // Create the job
            Job createdJob = jobService.createJob(job);
            
            // Add dependencies if specified
            if (request.getDependencies() != null && !request.getDependencies().isEmpty()) {
                for (Long dependencyJobId : request.getDependencies()) {
                    try {
                        // Add to dependency list
                        createdJob.getDependencyJobIds().add(dependencyJobId);
                        logger.debug("Added dependency: job {} depends on job {}", createdJob.getId(), dependencyJobId);
                    } catch (Exception e) {
                        logger.warn("Failed to add dependency from job {} to job {}: {}", 
                                  createdJob.getId(), dependencyJobId, e.getMessage());
                    }
                }
                jobService.updateJob(createdJob);
            }
            
            // Convert to detailed response
            JobDTOs.JobDetailsResponse response = convertToJobDetailsResponse(createdJob);
            
            logger.info("Successfully created job: {} with ID: {}", createdJob.getName(), createdJob.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Failed to create job: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Get job status and details - Enhanced Implementation  
    @GetMapping("/{id}")
    public ResponseEntity<JobDTOs.JobDetailsResponse> getJobDetailsById(@PathVariable Long id) {
        try {
            Optional<Job> job = jobService.getJobById(id);
            if (job.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            JobDTOs.JobDetailsResponse response = convertToJobDetailsResponse(job.get());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get job {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Update job priority or dependencies - Enhanced Implementation
    @PutMapping("/{id}")
    public ResponseEntity<JobDTOs.JobDetailsResponse> updateJobDetails(@PathVariable Long id, 
                                                               @Valid @RequestBody JobDTOs.UpdateJobRequest request) {
        try {
            Optional<Job> existingJob = jobService.getJobById(id);
            if (existingJob.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Job job = existingJob.get();
            
            // Check if job can be updated (not in final states)
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
                return ResponseEntity.badRequest().body(null);
            }
            
            // Update fields if provided
            if (request.getPriority() != null) {
                job.setPriority(convertPriorityToInteger(request.getPriority()));
                logger.info("Updated priority for job {} to {}", id, request.getPriority());
            }
            
            if (request.getParameters() != null) {
                job.setParameters(request.getParameters());
            }
            
            if (request.getMaxRetries() != null) {
                job.setMaxRetries(request.getMaxRetries());
            }
            
            if (request.getScheduledAt() != null) {
                job.setScheduledAt(request.getScheduledAt());
            }
            
            // Update the job
            Job updatedJob = jobService.updateJob(job);
            
            // Update dependencies if provided
            if (request.getDependencies() != null) {
                // Clear existing dependencies and add new ones
                updatedJob.getDependencyJobIds().clear();
                updatedJob.getDependencyJobIds().addAll(request.getDependencies());
                
                // Update again to save dependencies
                updatedJob = jobService.updateJob(updatedJob);
                
                logger.debug("Updated dependencies for job {}: {}", id, request.getDependencies());
            }
            
            JobDTOs.JobDetailsResponse response = convertToJobDetailsResponse(updatedJob);
            logger.info("Successfully updated job: {}", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update job {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Cancel job execution (equivalent to delete with status update)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String id) {
        try {
            Long jobId = Long.parseLong(id);
            Optional<Job> job = jobService.getJobById(jobId);
            if (job.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            // Cancel the job instead of deleting it
            Job cancelledJob = jobService.cancelJob(jobId);
            logger.info("Cancelled job execution for job: {}", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Job execution cancelled",
                "jobId", id,
                "status", cancelledJob.getStatus().toString()
            ));
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format: {}", id);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Invalid job ID format: " + id
            ));
        } catch (RuntimeException e) {
            logger.error("Failed to cancel job {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to cancel job: " + e.getMessage()
            ));
        }
    }
    
    // Get jobs by status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Job>> getJobsByStatus(@PathVariable JobStatus status) {
        List<Job> jobs = jobService.getJobsByStatus(status);
        return ResponseEntity.ok(jobs);
    }
    
    // Start a job
    @PostMapping("/{id}/start")
    public ResponseEntity<Job> startJob(@PathVariable String id) {
        try {
            Long jobId = Long.parseLong(id);
            Job job = jobService.startJob(jobId);
            return ResponseEntity.ok(job);
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format: {}", id);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logger.error("Failed to start job {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    // Complete a job
    @PostMapping("/{id}/complete")
    public ResponseEntity<Job> completeJob(@PathVariable String id) {
        try {
            Long jobId = Long.parseLong(id);
            Job job = jobService.completeJob(jobId);
            return ResponseEntity.ok(job);
        } catch (NumberFormatException e) {
            logger.error("Invalid job ID format: {}", id);
            return ResponseEntity.badRequest().build();
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
    
    /**
     * Convert Job entity to JobDetailsResponse DTO
     */
    private JobDTOs.JobDetailsResponse convertToJobDetailsResponse(Job job) {
        JobDTOs.JobDetailsResponse response = new JobDTOs.JobDetailsResponse();
        
        response.setId(job.getId());
        response.setJobId(job.getId().toString());
        response.setName(job.getName());
        response.setDescription(job.getDescription());
        response.setStatus(job.getStatus());
        response.setPriority(convertIntegerToPriority(job.getPriority()));
        response.setJobType(job.getJobType());
        response.setParameters(job.getParameters());
        response.setMaxRetries(job.getMaxRetries());
        response.setCurrentRetryCount(job.getRetryCount());
        response.setAssignedWorkerId(job.getAssignedWorkerId());
        response.setErrorMessage(job.getErrorMessage());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        response.setScheduledAt(job.getScheduledAt());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        
        // Calculate execution time if available
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            long executionTime = java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis();
            response.setExecutionTimeMs(executionTime);
        }
        
        // Set dependencies from job's dependency list
        if (job.getDependencyJobIds() != null && !job.getDependencyJobIds().isEmpty()) {
            List<JobDTOs.JobDependencyInfo> dependencies = job.getDependencyJobIds().stream()
                .map(depId -> new JobDTOs.JobDependencyInfo(depId, "Job-" + depId, JobStatus.PENDING, "DEPENDS_ON"))
                .toList();
            response.setDependencies(dependencies);
        } else {
            response.setDependencies(List.of());
        }
        
        response.setDependents(List.of()); // Simplified for now
        
        // Create execution info if job has execution details
        if (job.getAssignedWorkerId() != null || job.getStartedAt() != null) {
            JobDTOs.JobExecutionInfo executionInfo = new JobDTOs.JobExecutionInfo();
            executionInfo.setWorkerId(job.getAssignedWorkerId());
            executionInfo.setWorkerName(job.getAssignedWorkerName());
            executionInfo.setWorkerHost(job.getWorkerHost());
            executionInfo.setStartTime(job.getStartedAt());
            executionInfo.setEndTime(job.getCompletedAt());
            if (response.getExecutionTimeMs() != null) {
                executionInfo.setDurationMs(response.getExecutionTimeMs());
            }
            response.setExecutionInfo(executionInfo);
        }
        
        return response;
    }
    
    /**
     * Convert JobPriority enum to Integer
     */
    private Integer convertPriorityToInteger(JobPriority priority) {
        if (priority == null) return 50; // Default priority
        
        return switch (priority) {
            case HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 1;
        };
    }
    
    /**
     * Convert Integer to JobPriority enum
     */
    private JobPriority convertIntegerToPriority(Integer priority) {
        if (priority == null) return JobPriority.MEDIUM;
        
        if (priority >= 75) return JobPriority.HIGH;
        else if (priority >= 25) return JobPriority.MEDIUM;
        else return JobPriority.LOW;
    }
}
