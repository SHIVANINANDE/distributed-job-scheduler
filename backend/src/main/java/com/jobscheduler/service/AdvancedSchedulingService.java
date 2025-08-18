package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Advanced scheduling policies and time-based scheduling service
 */
@Service
public class AdvancedSchedulingService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedSchedulingService.class);
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private JobDependencyService jobDependencyService;
    
    @Autowired
    private JobPriorityQueueService jobPriorityQueueService;
    
    @Autowired(required = false)
    private WorkerService workerService;
    
    @Autowired(required = false)
    private RedisCacheService cacheService;
    
    // Cache for cron schedules
    private final Map<String, CronSchedule> cronScheduleCache = new ConcurrentHashMap<>();
    
    // Resource constraints tracker
    private final Map<String, ResourceConstraint> resourceConstraints = new ConcurrentHashMap<>();
    
    /**
     * Cron-like schedule configuration
     */
    public static class CronSchedule {
        private String expression;
        private String timezone;
        private LocalDateTime nextRun;
        private LocalDateTime lastRun;
        private boolean enabled;
        private String jobTemplate;
        private Map<String, Object> parameters;
        
        public CronSchedule(String expression, String timezone) {
            this.expression = expression;
            this.timezone = timezone != null ? timezone : "UTC";
            this.enabled = true;
            this.parameters = new HashMap<>();
        }
        
        // Getters and setters
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        
        public LocalDateTime getNextRun() { return nextRun; }
        public void setNextRun(LocalDateTime nextRun) { this.nextRun = nextRun; }
        
        public LocalDateTime getLastRun() { return lastRun; }
        public void setLastRun(LocalDateTime lastRun) { this.lastRun = lastRun; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getJobTemplate() { return jobTemplate; }
        public void setJobTemplate(String jobTemplate) { this.jobTemplate = jobTemplate; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    /**
     * Resource-based scheduling constraint
     */
    public static class ResourceConstraint {
        private String resourceType;
        private int maxConcurrent;
        private int currentUsage;
        private List<String> queuedJobs;
        private Map<String, Object> metadata;
        
        public ResourceConstraint(String resourceType, int maxConcurrent) {
            this.resourceType = resourceType;
            this.maxConcurrent = maxConcurrent;
            this.currentUsage = 0;
            this.queuedJobs = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
        
        // Getters and setters
        public String getResourceType() { return resourceType; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        
        public int getCurrentUsage() { return currentUsage; }
        public void setCurrentUsage(int currentUsage) { this.currentUsage = currentUsage; }
        
        public List<String> getQueuedJobs() { return queuedJobs; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        public boolean canAllocate() {
            return currentUsage < maxConcurrent;
        }
        
        public void allocate() {
            if (canAllocate()) {
                currentUsage++;
            }
        }
        
        public void release() {
            if (currentUsage > 0) {
                currentUsage--;
            }
        }
    }
    
    /**
     * Priority inheritance configuration
     */
    public static class PriorityInheritancePolicy {
        private boolean enabled;
        private InheritanceStrategy strategy;
        private int maxDepth;
        private double inheritanceDecay;
        
        public enum InheritanceStrategy {
            MAX_PRIORITY,      // Use highest priority from dependencies
            AVERAGE_PRIORITY,  // Use average priority from dependencies
            WEIGHTED_AVERAGE,  // Weighted average with decay
            PROPAGATION       // Propagate priority changes upward
        }
        
        public PriorityInheritancePolicy() {
            this.enabled = true;
            this.strategy = InheritanceStrategy.MAX_PRIORITY;
            this.maxDepth = 5;
            this.inheritanceDecay = 0.8;
        }
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public InheritanceStrategy getStrategy() { return strategy; }
        public void setStrategy(InheritanceStrategy strategy) { this.strategy = strategy; }
        
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
        
        public double getInheritanceDecay() { return inheritanceDecay; }
        public void setInheritanceDecay(double inheritanceDecay) { this.inheritanceDecay = inheritanceDecay; }
    }
    
    private PriorityInheritancePolicy priorityInheritancePolicy = new PriorityInheritancePolicy();
    
    /**
     * Schedule job with cron-like expression
     */
    @Transactional
    public String scheduleJobWithCron(String scheduleId, String cronExpression, String timezone, 
                                     String jobTemplate, Map<String, Object> parameters) {
        logger.info("Creating cron schedule: id={}, expression={}, timezone={}", 
                   scheduleId, cronExpression, timezone);
        
        try {
            // Validate cron expression
            if (!isValidCronExpression(cronExpression)) {
                throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
            }
            
            CronSchedule schedule = new CronSchedule(cronExpression, timezone);
            schedule.setJobTemplate(jobTemplate);
            schedule.setParameters(parameters != null ? parameters : new HashMap<>());
            
            // Calculate next run time
            LocalDateTime nextRun = calculateNextRunTime(cronExpression, timezone);
            schedule.setNextRun(nextRun);
            
            cronScheduleCache.put(scheduleId, schedule);
            
            logger.info("Cron schedule created successfully: id={}, nextRun={}", scheduleId, nextRun);
            return scheduleId;
            
        } catch (Exception e) {
            logger.error("Failed to create cron schedule: id={}, expression={}", scheduleId, cronExpression, e);
            throw new RuntimeException("Failed to create cron schedule: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add resource constraint
     */
    public void addResourceConstraint(String resourceType, int maxConcurrent, Map<String, Object> metadata) {
        logger.info("Adding resource constraint: type={}, maxConcurrent={}", resourceType, maxConcurrent);
        
        ResourceConstraint constraint = new ResourceConstraint(resourceType, maxConcurrent);
        if (metadata != null) {
            constraint.getMetadata().putAll(metadata);
        }
        
        resourceConstraints.put(resourceType, constraint);
    }
    
    /**
     * Apply priority inheritance to job dependencies
     */
    @Transactional
    public void applyPriorityInheritance(String jobId) {
        if (!priorityInheritancePolicy.isEnabled()) {
            return;
        }
        
        logger.debug("Applying priority inheritance for job: {}", jobId);
        
        try {
            Optional<Job> jobOpt = jobService.getJobById(Long.parseLong(jobId));
            if (jobOpt.isEmpty()) {
                logger.warn("Job not found for priority inheritance: {}", jobId);
                return;
            }
            
            Job job = jobOpt.get();
            applyPriorityInheritanceRecursive(job, 0);
            
        } catch (Exception e) {
            logger.error("Error applying priority inheritance for job {}: {}", jobId, e.getMessage(), e);
        }
    }
    
    /**
     * Recursive priority inheritance application
     */
    private void applyPriorityInheritanceRecursive(Job job, int depth) {
        if (depth >= priorityInheritancePolicy.getMaxDepth()) {
            return;
        }
        
        try {
            List<String> dependencies = jobDependencyService.getJobDependencies(job.getJobId());
            if (dependencies.isEmpty()) {
                return;
            }
            
            List<Integer> dependencyPriorities = new ArrayList<>();
            
            for (String depJobId : dependencies) {
                Optional<Job> depJobOpt = jobService.getJobById(Long.parseLong(depJobId));
                if (depJobOpt.isPresent()) {
                    Job depJob = depJobOpt.get();
                    dependencyPriorities.add(depJob.getPriority());
                    
                    // Recursively apply to dependencies
                    applyPriorityInheritanceRecursive(depJob, depth + 1);
                }
            }
            
            if (!dependencyPriorities.isEmpty()) {
                int inheritedPriority = calculateInheritedPriority(job.getPriority(), dependencyPriorities, depth);
                
                if (inheritedPriority != job.getPriority()) {
                    job.setPriority(inheritedPriority);
                    jobService.updateJob(job);
                    
                    logger.debug("Updated priority for job {} from dependencies: newPriority={}", 
                               job.getJobId(), inheritedPriority);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in recursive priority inheritance for job {}: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Calculate inherited priority based on strategy
     */
    private int calculateInheritedPriority(int currentPriority, List<Integer> dependencyPriorities, int depth) {
        switch (priorityInheritancePolicy.getStrategy()) {
            case MAX_PRIORITY:
                return Math.max(currentPriority, dependencyPriorities.stream().mapToInt(Integer::intValue).max().orElse(currentPriority));
                
            case AVERAGE_PRIORITY:
                double average = dependencyPriorities.stream().mapToInt(Integer::intValue).average().orElse(currentPriority);
                return Math.max(currentPriority, (int) Math.round(average));
                
            case WEIGHTED_AVERAGE:
                double decay = Math.pow(priorityInheritancePolicy.getInheritanceDecay(), depth);
                double weightedSum = dependencyPriorities.stream().mapToInt(Integer::intValue).sum() * decay;
                double weightedAverage = weightedSum / dependencyPriorities.size();
                return Math.max(currentPriority, (int) Math.round(weightedAverage));
                
            case PROPAGATION:
                // For propagation, we use the maximum but also consider current priority
                int maxDependencyPriority = dependencyPriorities.stream().mapToInt(Integer::intValue).max().orElse(0);
                return Math.max(currentPriority, (int) (maxDependencyPriority * Math.pow(priorityInheritancePolicy.getInheritanceDecay(), depth)));
                
            default:
                return currentPriority;
        }
    }
    
    /**
     * Check resource constraints before job scheduling
     */
    public boolean checkResourceConstraints(Job job) {
        String resourceType = extractResourceType(job);
        if (resourceType == null) {
            return true; // No resource constraint
        }
        
        ResourceConstraint constraint = resourceConstraints.get(resourceType);
        if (constraint == null) {
            return true; // No constraint defined for this resource type
        }
        
        boolean canAllocate = constraint.canAllocate();
        
        if (!canAllocate) {
            logger.debug("Resource constraint violation for job {}: resourceType={}, current={}, max={}", 
                        job.getJobId(), resourceType, constraint.getCurrentUsage(), constraint.getMaxConcurrent());
            
            // Add to queue for this resource
            if (!constraint.getQueuedJobs().contains(job.getJobId())) {
                constraint.getQueuedJobs().add(job.getJobId());
            }
        }
        
        return canAllocate;
    }
    
    /**
     * Allocate resource for job execution
     */
    public void allocateResource(Job job) {
        String resourceType = extractResourceType(job);
        if (resourceType != null) {
            ResourceConstraint constraint = resourceConstraints.get(resourceType);
            if (constraint != null) {
                constraint.allocate();
                constraint.getQueuedJobs().remove(job.getJobId());
                
                logger.debug("Allocated resource for job {}: resourceType={}, usage={}/{}", 
                           job.getJobId(), resourceType, constraint.getCurrentUsage(), constraint.getMaxConcurrent());
            }
        }
    }
    
    /**
     * Release resource after job completion
     */
    public void releaseResource(Job job) {
        String resourceType = extractResourceType(job);
        if (resourceType != null) {
            ResourceConstraint constraint = resourceConstraints.get(resourceType);
            if (constraint != null) {
                constraint.release();
                
                logger.debug("Released resource for job {}: resourceType={}, usage={}/{}", 
                           job.getJobId(), resourceType, constraint.getCurrentUsage(), constraint.getMaxConcurrent());
                
                // Try to schedule queued jobs for this resource
                scheduleQueuedJobsForResource(resourceType, constraint);
            }
        }
    }
    
    /**
     * Schedule queued jobs when resource becomes available
     */
    private void scheduleQueuedJobsForResource(String resourceType, ResourceConstraint constraint) {
        if (constraint.getQueuedJobs().isEmpty() || !constraint.canAllocate()) {
            return;
        }
        
        // Get the highest priority queued job
        String nextJobId = constraint.getQueuedJobs().remove(0);
        
        try {
            Optional<Job> jobOpt = jobService.getJobById(Long.parseLong(nextJobId));
            if (jobOpt.isPresent()) {
                Job job = jobOpt.get();
                if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.QUEUED) {
                    constraint.allocate();
                    jobPriorityQueueService.addJob(job);
                    
                    logger.info("Scheduled queued job {} for resource {}", nextJobId, resourceType);
                }
            }
        } catch (Exception e) {
            logger.error("Error scheduling queued job {} for resource {}: {}", nextJobId, resourceType, e.getMessage());
        }
    }
    
    /**
     * Extract resource type from job
     */
    private String extractResourceType(Job job) {
        // Check job parameters for resource type
        if (job.getParameters() != null && job.getParameters().containsKey("resourceType")) {
            return job.getParameters().get("resourceType").toString();
        }
        
        // Check job type as resource type
        if (job.getJobType() != null) {
            return job.getJobType();
        }
        
        // Check tags for resource type
        if (job.getTags() != null) {
            String[] tags = job.getTags().split(",");
            for (String tag : tags) {
                if (tag.startsWith("resource:")) {
                    return tag.substring("resource:".length());
                }
            }
        }
        
        return null;
    }
    
    /**
     * Scheduled task to process cron schedules
     */
    @Scheduled(fixedDelay = 60000) // Run every minute
    public void processCronSchedules() {
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, CronSchedule> entry : cronScheduleCache.entrySet()) {
            String scheduleId = entry.getKey();
            CronSchedule schedule = entry.getValue();
            
            if (!schedule.isEnabled()) {
                continue;
            }
            
            if (schedule.getNextRun() != null && now.isAfter(schedule.getNextRun())) {
                try {
                    // Create job from template
                    Job job = createJobFromTemplate(schedule, scheduleId);
                    Job createdJob = jobService.createJob(job);
                    
                    // Apply scheduling policies
                    applySchedulingPolicies(createdJob);
                    
                    // Update schedule
                    schedule.setLastRun(now);
                    schedule.setNextRun(calculateNextRunTime(schedule.getExpression(), schedule.getTimezone()));
                    
                    logger.info("Created scheduled job {} from cron schedule {}", createdJob.getJobId(), scheduleId);
                    
                } catch (Exception e) {
                    logger.error("Error processing cron schedule {}: {}", scheduleId, e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Create job from cron schedule template
     */
    private Job createJobFromTemplate(CronSchedule schedule, String scheduleId) {
        Job job = new Job();
        
        job.setJobId("scheduled-" + UUID.randomUUID().toString());
        job.setName("Scheduled Job - " + scheduleId);
        job.setDescription("Job created from cron schedule: " + scheduleId);
        job.setJobType(schedule.getJobTemplate());
        job.setStatus(JobStatus.PENDING);
        job.setPriority(50); // Default medium priority
        job.setParameters(new HashMap<>(schedule.getParameters()));
        job.setMaxRetries(3);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        
        // Add schedule tag
        List<String> tags = new ArrayList<>();
        tags.add("scheduled");
        tags.add("cron:" + scheduleId);
        job.setTags(String.join(",", tags));
        
        return job;
    }
    
    /**
     * Apply all scheduling policies to a job
     */
    private void applySchedulingPolicies(Job job) {
        try {
            // Apply priority inheritance
            applyPriorityInheritance(job.getJobId());
            
            // Check resource constraints
            if (checkResourceConstraints(job)) {
                // Job can be queued immediately
                allocateResource(job);
                jobPriorityQueueService.addJob(job);
            }
            // If resource constraints are violated, job is automatically queued in the constraint
            
        } catch (Exception e) {
            logger.error("Error applying scheduling policies to job {}: {}", job.getJobId(), e.getMessage());
        }
    }
    
    /**
     * Simple cron expression validation
     */
    private boolean isValidCronExpression(String cronExpression) {
        // Basic validation for cron-like expressions
        // Format: "minute hour day month dayOfWeek"
        String[] parts = cronExpression.trim().split("\\s+");
        
        if (parts.length != 5) {
            return false;
        }
        
        // More sophisticated validation could be added here
        return true;
    }
    
    /**
     * Calculate next run time from cron expression
     */
    private LocalDateTime calculateNextRunTime(String cronExpression, String timezone) {
        // Simplified cron calculation - in production, use a proper cron library
        LocalDateTime now = LocalDateTime.now();
        
        try {
            String[] parts = cronExpression.split("\\s+");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Invalid cron format");
            }
            
            // For demonstration, add 1 hour to current time
            // In production, implement proper cron parsing
            return now.plusHours(1);
            
        } catch (Exception e) {
            logger.error("Error calculating next run time for cron expression {}: {}", cronExpression, e.getMessage());
            return now.plusHours(1); // Fallback
        }
    }
    
    /**
     * Get scheduling statistics
     */
    public Map<String, Object> getSchedulingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Cron schedules statistics
        long activeCronSchedules = cronScheduleCache.values().stream()
            .mapToLong(schedule -> schedule.isEnabled() ? 1 : 0)
            .sum();
        
        stats.put("active_cron_schedules", activeCronSchedules);
        stats.put("total_cron_schedules", cronScheduleCache.size());
        
        // Resource constraints statistics
        Map<String, Object> resourceStats = new HashMap<>();
        for (Map.Entry<String, ResourceConstraint> entry : resourceConstraints.entrySet()) {
            ResourceConstraint constraint = entry.getValue();
            resourceStats.put(entry.getKey(), Map.of(
                "max_concurrent", constraint.getMaxConcurrent(),
                "current_usage", constraint.getCurrentUsage(),
                "queued_jobs", constraint.getQueuedJobs().size(),
                "utilization_percentage", (double) constraint.getCurrentUsage() / constraint.getMaxConcurrent() * 100
            ));
        }
        stats.put("resource_constraints", resourceStats);
        
        // Priority inheritance statistics
        stats.put("priority_inheritance_enabled", priorityInheritancePolicy.isEnabled());
        stats.put("priority_inheritance_strategy", priorityInheritancePolicy.getStrategy().toString());
        stats.put("priority_inheritance_max_depth", priorityInheritancePolicy.getMaxDepth());
        
        stats.put("last_updated", LocalDateTime.now());
        
        return stats;
    }
    
    /**
     * Update priority inheritance policy
     */
    public void updatePriorityInheritancePolicy(PriorityInheritancePolicy policy) {
        this.priorityInheritancePolicy = policy;
        logger.info("Updated priority inheritance policy: enabled={}, strategy={}, maxDepth={}", 
                   policy.isEnabled(), policy.getStrategy(), policy.getMaxDepth());
    }
    
    /**
     * Remove cron schedule
     */
    public boolean removeCronSchedule(String scheduleId) {
        CronSchedule removed = cronScheduleCache.remove(scheduleId);
        if (removed != null) {
            logger.info("Removed cron schedule: {}", scheduleId);
            return true;
        }
        return false;
    }
    
    /**
     * Update cron schedule
     */
    public boolean updateCronSchedule(String scheduleId, String cronExpression, String timezone, 
                                     Map<String, Object> parameters) {
        CronSchedule schedule = cronScheduleCache.get(scheduleId);
        if (schedule != null) {
            if (cronExpression != null && isValidCronExpression(cronExpression)) {
                schedule.setExpression(cronExpression);
                schedule.setNextRun(calculateNextRunTime(cronExpression, timezone));
            }
            if (timezone != null) {
                schedule.setTimezone(timezone);
            }
            if (parameters != null) {
                schedule.setParameters(parameters);
            }
            
            logger.info("Updated cron schedule: {}", scheduleId);
            return true;
        }
        return false;
    }
    
    /**
     * Get resource constraint information
     */
    public Map<String, ResourceConstraint> getResourceConstraints() {
        return new HashMap<>(resourceConstraints);
    }
    
    /**
     * Get cron schedules information
     */
    public Map<String, CronSchedule> getCronSchedules() {
        return new HashMap<>(cronScheduleCache);
    }
}
