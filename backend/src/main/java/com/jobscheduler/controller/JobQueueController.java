package com.jobscheduler.controller;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobPriority;
import com.jobscheduler.service.JobPriorityQueueService;
import com.jobscheduler.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/queue")
@CrossOrigin(origins = "*")
public class JobQueueController {
    
    private static final Logger logger = LoggerFactory.getLogger(JobQueueController.class);
    
    @Autowired
    private JobPriorityQueueService queueService;
    
    @Autowired
    private JobService jobService;
    
    /**
     * Add a job to the priority queue
     */
    @PostMapping("/add/{jobId}")
    public ResponseEntity<?> addJobToQueue(@PathVariable Long jobId) {
        try {
            Job job = jobService.getJobById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            boolean added = queueService.addJobToQueue(job);
            if (added) {
                logger.info("Added job {} to priority queue via API", job.getJobId());
                return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Job added to priority queue",
                    "jobId", job.getJobId()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to add job to queue"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error adding job {} to queue: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Pop the highest priority job from the queue
     */
    @PostMapping("/pop")
    public ResponseEntity<?> popHighestPriorityJob() {
        try {
            Job job = queueService.popHighestPriorityJob();
            if (job != null) {
                logger.info("Popped job {} from priority queue via API", job.getJobId());
                return ResponseEntity.ok(job);
            } else {
                return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "No jobs available in queue",
                    "job", null
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error popping job from queue: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Update job priority
     */
    @PutMapping("/priority/{jobId}")
    public ResponseEntity<?> updateJobPriority(@PathVariable Long jobId, 
                                             @RequestParam JobPriority priority) {
        try {
            boolean updated = queueService.updateJobPriority(jobId, priority);
            if (updated) {
                logger.info("Updated priority for job {} to {} via API", jobId, priority);
                return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Job priority updated",
                    "jobId", jobId,
                    "newPriority", priority
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to update job priority"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error updating priority for job {}: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get queued jobs with pagination
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> getQueuedJobs(@RequestParam(defaultValue = "0") long start,
                                         @RequestParam(defaultValue = "9") long end) {
        try {
            List<Job> jobs = queueService.getQueuedJobs(start, end);
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "jobs", jobs,
                "start", start,
                "end", end,
                "count", jobs.size()
            ));
            
        } catch (Exception e) {
            logger.error("Error getting queued jobs: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Batch add multiple jobs to the queue
     */
    @PostMapping("/batch/add")
    public ResponseEntity<?> batchAddJobs(@RequestBody List<Long> jobIds) {
        try {
            List<Job> jobs = jobIds.stream()
                .map(jobService::getJobById)
                .filter(job -> job != null)
                .toList();
            
            if (jobs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No valid jobs found"
                ));
            }
            
            int added = queueService.batchAddJobs(jobs);
            logger.info("Batch added {} jobs to priority queue via API", added);
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Jobs added to priority queue",
                "requested", jobIds.size(),
                "added", added
            ));
            
        } catch (Exception e) {
            logger.error("Error batch adding jobs to queue: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Batch pop multiple jobs from the queue
     */
    @PostMapping("/batch/pop")
    public ResponseEntity<?> batchPopJobs(@RequestParam(defaultValue = "5") int count) {
        try {
            List<Job> jobs = queueService.batchPopJobs(count);
            logger.info("Batch popped {} jobs from priority queue via API", jobs.size());
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "jobs", jobs,
                "requested", count,
                "popped", jobs.size()
            ));
            
        } catch (Exception e) {
            logger.error("Error batch popping jobs from queue: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Remove a specific job from the queue
     */
    @DeleteMapping("/remove/{jobId}")
    public ResponseEntity<?> removeJobFromQueue(@PathVariable Long jobId) {
        try {
            boolean removed = queueService.removeJobFromQueue(jobId);
            if (removed) {
                logger.info("Removed job {} from priority queue via API", jobId);
                return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Job removed from queue",
                    "jobId", jobId
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to remove job from queue"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error removing job {} from queue: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get queue statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getQueueStatistics() {
        try {
            Map<String, Object> stats = queueService.getQueueStatistics();
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "statistics", stats
            ));
            
        } catch (Exception e) {
            logger.error("Error getting queue statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Move job to completed queue
     */
    @PostMapping("/complete/{jobId}")
    public ResponseEntity<?> markJobCompleted(@PathVariable Long jobId) {
        try {
            Job job = jobService.getJobById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            queueService.moveJobToCompleted(job);
            logger.info("Moved job {} to completed queue via API", job.getJobId());
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Job moved to completed queue",
                "jobId", job.getJobId()
            ));
            
        } catch (Exception e) {
            logger.error("Error marking job {} as completed: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Move job to failed queue
     */
    @PostMapping("/fail/{jobId}")
    public ResponseEntity<?> markJobFailed(@PathVariable Long jobId) {
        try {
            Job job = jobService.getJobById(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            queueService.moveJobToFailed(job);
            logger.info("Moved job {} to failed queue via API", job.getJobId());
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Job moved to failed queue",
                "jobId", job.getJobId()
            ));
            
        } catch (Exception e) {
            logger.error("Error marking job {} as failed: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Clean up old jobs
     */
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanupOldJobs(@RequestParam(defaultValue = "24") int maxAgeHours) {
        try {
            queueService.cleanupOldJobs(maxAgeHours);
            logger.info("Cleaned up jobs older than {} hours via API", maxAgeHours);
            
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Old jobs cleaned up",
                "maxAgeHours", maxAgeHours
            ));
            
        } catch (Exception e) {
            logger.error("Error cleaning up old jobs: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
}
