package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for handling batch job operations and bulk processing
 */
@Service
public class BatchJobService {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchJobService.class);
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private JobDependencyService jobDependencyService;
    
    @Autowired
    private JobPriorityQueueService jobPriorityQueueService;
    
    @Autowired(required = false)
    private RedisCacheService cacheService;
    
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(10);
    
    /**
     * Batch job creation request
     */
    public static class BatchJobRequest {
        private List<JobCreationRequest> jobs;
        private boolean preserveOrder;
        private boolean createDependencyChain;
        private String batchId;
        private JobPriority defaultPriority;
        private Map<String, Object> commonParameters;
        
        // Constructors, getters, setters
        public BatchJobRequest() {
            this.jobs = new ArrayList<>();
            this.preserveOrder = false;
            this.createDependencyChain = false;
            this.defaultPriority = JobPriority.MEDIUM;
            this.commonParameters = new HashMap<>();
        }
        
        public List<JobCreationRequest> getJobs() { return jobs; }
        public void setJobs(List<JobCreationRequest> jobs) { this.jobs = jobs; }
        
        public boolean isPreserveOrder() { return preserveOrder; }
        public void setPreserveOrder(boolean preserveOrder) { this.preserveOrder = preserveOrder; }
        
        public boolean isCreateDependencyChain() { return createDependencyChain; }
        public void setCreateDependencyChain(boolean createDependencyChain) { this.createDependencyChain = createDependencyChain; }
        
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public JobPriority getDefaultPriority() { return defaultPriority; }
        public void setDefaultPriority(JobPriority defaultPriority) { this.defaultPriority = defaultPriority; }
        
        public Map<String, Object> getCommonParameters() { return commonParameters; }
        public void setCommonParameters(Map<String, Object> commonParameters) { this.commonParameters = commonParameters; }
    }
    
    /**
     * Individual job creation request within a batch
     */
    public static class JobCreationRequest {
        private String name;
        private String description;
        private String jobType;
        private Map<String, Object> parameters;
        private JobPriority priority;
        private List<String> dependencies;
        private int maxRetries;
        private LocalDateTime scheduledAt;
        private List<String> tags;
        
        // Constructors, getters, setters
        public JobCreationRequest() {
            this.parameters = new HashMap<>();
            this.dependencies = new ArrayList<>();
            this.maxRetries = 3;
            this.tags = new ArrayList<>();
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public JobPriority getPriority() { return priority; }
        public void setPriority(JobPriority priority) { this.priority = priority; }
        
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
    
    /**
     * Batch job result
     */
    public static class BatchJobResult {
        private String batchId;
        private List<Job> createdJobs;
        private List<String> errors;
        private BatchJobStatistics statistics;
        private LocalDateTime createdAt;
        
        public BatchJobResult(String batchId) {
            this.batchId = batchId;
            this.createdJobs = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.createdAt = LocalDateTime.now();
        }
        
        public String getBatchId() { return batchId; }
        public List<Job> getCreatedJobs() { return createdJobs; }
        public List<String> getErrors() { return errors; }
        public BatchJobStatistics getStatistics() { return statistics; }
        public void setStatistics(BatchJobStatistics statistics) { this.statistics = statistics; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    /**
     * Batch job statistics
     */
    public static class BatchJobStatistics {
        private int totalRequested;
        private int successfullyCreated;
        private int failed;
        private int dependenciesCreated;
        private long processingTimeMs;
        
        public BatchJobStatistics(int totalRequested, int successfullyCreated, int failed, 
                                int dependenciesCreated, long processingTimeMs) {
            this.totalRequested = totalRequested;
            this.successfullyCreated = successfullyCreated;
            this.failed = failed;
            this.dependenciesCreated = dependenciesCreated;
            this.processingTimeMs = processingTimeMs;
        }
        
        public int getTotalRequested() { return totalRequested; }
        public int getSuccessfullyCreated() { return successfullyCreated; }
        public int getFailed() { return failed; }
        public int getDependenciesCreated() { return dependenciesCreated; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getSuccessRate() { 
            return totalRequested > 0 ? (double) successfullyCreated / totalRequested * 100 : 0; 
        }
    }
    
    /**
     * Submit batch of jobs with dependency management
     */
    @Transactional
    public BatchJobResult submitBatchJobs(BatchJobRequest request) {
        long startTime = System.currentTimeMillis();
        String batchId = request.getBatchId() != null ? request.getBatchId() : 
                        "batch-" + UUID.randomUUID().toString().substring(0, 8);
        
        logger.info("Starting batch job submission: batchId={}, jobCount={}", batchId, request.getJobs().size());
        
        BatchJobResult result = new BatchJobResult(batchId);
        Map<String, Job> createdJobMap = new HashMap<>();
        
        try {
            // Phase 1: Create all jobs without dependencies
            for (int i = 0; i < request.getJobs().size(); i++) {
                JobCreationRequest jobRequest = request.getJobs().get(i);
                
                try {
                    Job job = createJobFromRequest(jobRequest, request, i);
                    Job savedJob = jobService.createJob(job);
                    
                    createdJobMap.put(savedJob.getJobId(), savedJob);
                    result.getCreatedJobs().add(savedJob);
                    
                    logger.debug("Created job {} in batch {}", savedJob.getJobId(), batchId);
                    
                } catch (Exception e) {
                    String error = String.format("Failed to create job %d: %s", i, e.getMessage());
                    result.getErrors().add(error);
                    logger.error("Error creating job in batch {}: {}", batchId, error, e);
                }
            }
            
            // Phase 2: Create dependencies
            int dependenciesCreated = 0;
            if (request.isCreateDependencyChain() && result.getCreatedJobs().size() > 1) {
                dependenciesCreated += createDependencyChain(result.getCreatedJobs());
            }
            
            // Phase 3: Add custom dependencies
            dependenciesCreated += createCustomDependencies(request, createdJobMap, result);
            
            // Phase 4: Queue jobs that have no dependencies
            queueIndependentJobs(result.getCreatedJobs());
            
            // Update statistics
            long processingTime = System.currentTimeMillis() - startTime;
            result.setStatistics(new BatchJobStatistics(
                request.getJobs().size(),
                result.getCreatedJobs().size(),
                result.getErrors().size(),
                dependenciesCreated,
                processingTime
            ));
            
            // Cache batch result for monitoring
            if (cacheService != null) {
                cacheService.cacheJobBatch(batchId, result, 3600); // Cache for 1 hour
            }
            
            logger.info("Completed batch job submission: batchId={}, created={}, failed={}, dependencies={}, time={}ms",
                    batchId, result.getCreatedJobs().size(), result.getErrors().size(), 
                    dependenciesCreated, processingTime);
            
        } catch (Exception e) {
            logger.error("Critical error in batch job submission: batchId={}", batchId, e);
            result.getErrors().add("Critical batch processing error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Create job from batch request
     */
    private Job createJobFromRequest(JobCreationRequest jobRequest, BatchJobRequest batchRequest, int index) {
        Job job = new Job();
        
        // Basic job properties
        job.setJobId("job-" + UUID.randomUUID().toString());
        job.setName(jobRequest.getName() != null ? jobRequest.getName() : 
                   batchRequest.getBatchId() + "-job-" + (index + 1));
        job.setDescription(jobRequest.getDescription());
        job.setJobType(jobRequest.getJobType());
        job.setStatus(JobStatus.PENDING);
        
        // Priority handling
        JobPriority priority = jobRequest.getPriority() != null ? 
                              jobRequest.getPriority() : batchRequest.getDefaultPriority();
        job.setPriority(convertPriorityToInteger(priority));
        
        // Parameters - merge common and specific
        Map<String, Object> parameters = new HashMap<>(batchRequest.getCommonParameters());
        if (jobRequest.getParameters() != null) {
            parameters.putAll(jobRequest.getParameters());
        }
        job.setParameters(parameters);
        
        // Other properties
        job.setMaxRetries(jobRequest.getMaxRetries());
        job.setScheduledAt(jobRequest.getScheduledAt());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        
        // Tags - include batch ID
        List<String> tags = new ArrayList<>(jobRequest.getTags());
        tags.add("batch:" + batchRequest.getBatchId());
        job.setTags(String.join(",", tags));
        
        return job;
    }
    
    /**
     * Create dependency chain for ordered execution
     */
    private int createDependencyChain(List<Job> jobs) {
        int dependenciesCreated = 0;
        
        for (int i = 1; i < jobs.size(); i++) {
            Job currentJob = jobs.get(i);
            Job previousJob = jobs.get(i - 1);
            
            try {
                jobDependencyService.addDependency(currentJob.getJobId(), previousJob.getJobId());
                dependenciesCreated++;
                
                logger.debug("Created dependency: {} depends on {}", 
                           currentJob.getJobId(), previousJob.getJobId());
                           
            } catch (Exception e) {
                logger.error("Failed to create dependency chain between {} and {}: {}", 
                           currentJob.getJobId(), previousJob.getJobId(), e.getMessage());
            }
        }
        
        return dependenciesCreated;
    }
    
    /**
     * Create custom dependencies specified in batch request
     */
    private int createCustomDependencies(BatchJobRequest request, Map<String, Job> createdJobMap, 
                                       BatchJobResult result) {
        int dependenciesCreated = 0;
        
        for (int i = 0; i < request.getJobs().size(); i++) {
            JobCreationRequest jobRequest = request.getJobs().get(i);
            
            if (jobRequest.getDependencies() != null && !jobRequest.getDependencies().isEmpty()) {
                Job job = result.getCreatedJobs().get(i);
                
                for (String dependencyRef : jobRequest.getDependencies()) {
                    try {
                        // Resolve dependency reference (could be job ID or index in batch)
                        String dependencyJobId = resolveDependencyReference(dependencyRef, createdJobMap, result);
                        
                        if (dependencyJobId != null) {
                            jobDependencyService.addDependency(job.getJobId(), dependencyJobId);
                            dependenciesCreated++;
                            
                            logger.debug("Created custom dependency: {} depends on {}", 
                                       job.getJobId(), dependencyJobId);
                        } else {
                            result.getErrors().add(String.format("Could not resolve dependency '%s' for job %s", 
                                                                dependencyRef, job.getJobId()));
                        }
                        
                    } catch (Exception e) {
                        String error = String.format("Failed to create dependency '%s' for job %s: %s", 
                                                    dependencyRef, job.getJobId(), e.getMessage());
                        result.getErrors().add(error);
                        logger.error("Dependency creation error: {}", error);
                    }
                }
            }
        }
        
        return dependenciesCreated;
    }
    
    /**
     * Resolve dependency reference to actual job ID
     */
    private String resolveDependencyReference(String dependencyRef, Map<String, Job> createdJobMap, 
                                            BatchJobResult result) {
        // If it's already a job ID in our created jobs
        if (createdJobMap.containsKey(dependencyRef)) {
            return dependencyRef;
        }
        
        // If it's a numeric index reference (e.g., "@1" for first job in batch)
        if (dependencyRef.startsWith("@")) {
            try {
                int index = Integer.parseInt(dependencyRef.substring(1));
                if (index >= 0 && index < result.getCreatedJobs().size()) {
                    return result.getCreatedJobs().get(index).getJobId();
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid index reference: {}", dependencyRef);
            }
        }
        
        // If it's an existing job ID in the system
        try {
            Optional<Job> existingJob = jobService.getJobById(Long.parseLong(dependencyRef));
            if (existingJob.isPresent()) {
                return existingJob.get().getJobId();
            }
        } catch (NumberFormatException e) {
            // Not a numeric ID, try as job ID string
            Job existingJob = jobService.getJobByJobId(dependencyRef);
            if (existingJob != null) {
                return existingJob.getJobId();
            }
        }
        
        return null;
    }
    
    /**
     * Queue jobs that have no dependencies
     */
    private void queueIndependentJobs(List<Job> jobs) {
        for (Job job : jobs) {
            try {
                List<String> dependencies = jobDependencyService.getJobDependencies(job.getJobId());
                if (dependencies.isEmpty()) {
                    jobPriorityQueueService.addJob(job);
                    logger.debug("Queued independent job: {}", job.getJobId());
                }
            } catch (Exception e) {
                logger.error("Failed to queue job {}: {}", job.getJobId(), e.getMessage());
            }
        }
    }
    
    /**
     * Get batch job status and progress
     */
    public BatchJobStatus getBatchStatus(String batchId) {
        logger.debug("Getting batch status for batchId: {}", batchId);
        
        try {
            // Try to get from cache first
            if (cacheService != null) {
                BatchJobResult cachedResult = cacheService.getCachedJobBatch(batchId);
                if (cachedResult != null) {
                    return createBatchStatus(cachedResult);
                }
            }
            
            // Get jobs by batch tag
            List<Job> batchJobs = jobService.getJobsByTag("batch:" + batchId);
            return createBatchStatusFromJobs(batchId, batchJobs);
            
        } catch (Exception e) {
            logger.error("Error getting batch status for {}: {}", batchId, e.getMessage());
            return new BatchJobStatus(batchId, 0, 0, 0, 0, 0, 0, BatchJobStatus.BatchStatus.ERROR);
        }
    }
    
    /**
     * Batch job status information
     */
    public static class BatchJobStatus {
        public enum BatchStatus {
            PENDING, RUNNING, COMPLETED, FAILED, PARTIAL, ERROR
        }
        
        private String batchId;
        private int totalJobs;
        private int pendingJobs;
        private int runningJobs;
        private int completedJobs;
        private int failedJobs;
        private int cancelledJobs;
        private BatchStatus overallStatus;
        private double progressPercentage;
        private LocalDateTime lastUpdated;
        
        public BatchJobStatus(String batchId, int totalJobs, int pendingJobs, int runningJobs, 
                            int completedJobs, int failedJobs, int cancelledJobs, BatchStatus overallStatus) {
            this.batchId = batchId;
            this.totalJobs = totalJobs;
            this.pendingJobs = pendingJobs;
            this.runningJobs = runningJobs;
            this.completedJobs = completedJobs;
            this.failedJobs = failedJobs;
            this.cancelledJobs = cancelledJobs;
            this.overallStatus = overallStatus;
            this.progressPercentage = totalJobs > 0 ? 
                (double) (completedJobs + failedJobs + cancelledJobs) / totalJobs * 100 : 0;
            this.lastUpdated = LocalDateTime.now();
        }
        
        // Getters
        public String getBatchId() { return batchId; }
        public int getTotalJobs() { return totalJobs; }
        public int getPendingJobs() { return pendingJobs; }
        public int getRunningJobs() { return runningJobs; }
        public int getCompletedJobs() { return completedJobs; }
        public int getFailedJobs() { return failedJobs; }
        public int getCancelledJobs() { return cancelledJobs; }
        public BatchStatus getOverallStatus() { return overallStatus; }
        public double getProgressPercentage() { return progressPercentage; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
    }
    
    /**
     * Create batch status from cached result
     */
    private BatchJobStatus createBatchStatus(BatchJobResult result) {
        // Get current status of all jobs in the batch
        Map<JobStatus, Integer> statusCounts = new HashMap<>();
        
        for (Job job : result.getCreatedJobs()) {
            try {
                Optional<Job> currentJob = jobService.getJobById(job.getId());
                if (currentJob.isPresent()) {
                    JobStatus status = currentJob.get().getStatus();
                    statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
                }
            } catch (Exception e) {
                logger.warn("Could not get current status for job {}: {}", job.getId(), e.getMessage());
            }
        }
        
        return new BatchJobStatus(
            result.getBatchId(),
            result.getCreatedJobs().size(),
            statusCounts.getOrDefault(JobStatus.PENDING, 0) + statusCounts.getOrDefault(JobStatus.QUEUED, 0),
            statusCounts.getOrDefault(JobStatus.RUNNING, 0),
            statusCounts.getOrDefault(JobStatus.COMPLETED, 0),
            statusCounts.getOrDefault(JobStatus.FAILED, 0),
            statusCounts.getOrDefault(JobStatus.CANCELLED, 0),
            determineBatchStatus(statusCounts, result.getCreatedJobs().size())
        );
    }
    
    /**
     * Create batch status from job list
     */
    private BatchJobStatus createBatchStatusFromJobs(String batchId, List<Job> jobs) {
        Map<JobStatus, Integer> statusCounts = jobs.stream()
            .collect(Collectors.groupingBy(
                Job::getStatus,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
        
        return new BatchJobStatus(
            batchId,
            jobs.size(),
            statusCounts.getOrDefault(JobStatus.PENDING, 0) + statusCounts.getOrDefault(JobStatus.QUEUED, 0),
            statusCounts.getOrDefault(JobStatus.RUNNING, 0),
            statusCounts.getOrDefault(JobStatus.COMPLETED, 0),
            statusCounts.getOrDefault(JobStatus.FAILED, 0),
            statusCounts.getOrDefault(JobStatus.CANCELLED, 0),
            determineBatchStatus(statusCounts, jobs.size())
        );
    }
    
    /**
     * Determine overall batch status from individual job statuses
     */
    private BatchJobStatus.BatchStatus determineBatchStatus(Map<JobStatus, Integer> statusCounts, int totalJobs) {
        int completed = statusCounts.getOrDefault(JobStatus.COMPLETED, 0);
        int failed = statusCounts.getOrDefault(JobStatus.FAILED, 0);
        int cancelled = statusCounts.getOrDefault(JobStatus.CANCELLED, 0);
        int running = statusCounts.getOrDefault(JobStatus.RUNNING, 0);
        
        if (completed == totalJobs) {
            return BatchJobStatus.BatchStatus.COMPLETED;
        } else if (failed + cancelled == totalJobs) {
            return BatchJobStatus.BatchStatus.FAILED;
        } else if (completed + failed + cancelled == totalJobs) {
            return BatchJobStatus.BatchStatus.PARTIAL;
        } else if (running > 0) {
            return BatchJobStatus.BatchStatus.RUNNING;
        } else {
            return BatchJobStatus.BatchStatus.PENDING;
        }
    }
    
    /**
     * Cancel entire batch
     */
    @Transactional
    public BatchCancellationResult cancelBatch(String batchId, boolean forceCancel) {
        logger.info("Cancelling batch: batchId={}, force={}", batchId, forceCancel);
        
        List<Job> batchJobs = jobService.getJobsByTag("batch:" + batchId);
        BatchCancellationResult result = new BatchCancellationResult(batchId);
        
        for (Job job : batchJobs) {
            try {
                if (canCancelJob(job, forceCancel)) {
                    Job cancelledJob = jobService.cancelJob(job.getId());
                    result.getCancelledJobs().add(cancelledJob);
                } else {
                    result.getSkippedJobs().add(job);
                    result.getWarnings().add(String.format("Cannot cancel job %s in status %s", 
                                                          job.getJobId(), job.getStatus()));
                }
            } catch (Exception e) {
                result.getErrors().add(String.format("Failed to cancel job %s: %s", job.getJobId(), e.getMessage()));
                logger.error("Error cancelling job {} in batch {}: {}", job.getJobId(), batchId, e.getMessage());
            }
        }
        
        logger.info("Batch cancellation completed: batchId={}, cancelled={}, skipped={}, errors={}", 
                   batchId, result.getCancelledJobs().size(), result.getSkippedJobs().size(), 
                   result.getErrors().size());
        
        return result;
    }
    
    /**
     * Batch cancellation result
     */
    public static class BatchCancellationResult {
        private String batchId;
        private List<Job> cancelledJobs;
        private List<Job> skippedJobs;
        private List<String> errors;
        private List<String> warnings;
        
        public BatchCancellationResult(String batchId) {
            this.batchId = batchId;
            this.cancelledJobs = new ArrayList<>();
            this.skippedJobs = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public String getBatchId() { return batchId; }
        public List<Job> getCancelledJobs() { return cancelledJobs; }
        public List<Job> getSkippedJobs() { return skippedJobs; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
    
    /**
     * Check if job can be cancelled
     */
    private boolean canCancelJob(Job job, boolean forceCancel) {
        if (forceCancel) {
            return true; // Force cancel overrides status checks
        }
        
        return job.getStatus() == JobStatus.PENDING || 
               job.getStatus() == JobStatus.QUEUED ||
               job.getStatus() == JobStatus.SCHEDULED;
    }
    
    /**
     * Convert JobPriority to integer value
     */
    private Integer convertPriorityToInteger(JobPriority priority) {
        if (priority == null) return 50; // Default medium priority
        
        return switch (priority) {
            case HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 1;
        };
    }
    
    /**
     * Get batch processing statistics
     */
    public Map<String, Object> getBatchProcessingStatistics() {
        // Implementation would gather statistics about batch processing
        // This is a placeholder for comprehensive batch metrics
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_batches_processed", 0); // Would be actual count
        stats.put("average_batch_size", 0.0);
        stats.put("average_processing_time_ms", 0.0);
        stats.put("batch_success_rate", 100.0);
        stats.put("last_updated", LocalDateTime.now());
        
        return stats;
    }
}
