package com.jobscheduler.service;

import com.jobscheduler.model.JobDependency;
import com.jobscheduler.model.JobDependency.DependencyType;
import com.jobscheduler.model.JobDependency.FailureAction;
import com.jobscheduler.repository.JobDependencyRepository;
import com.jobscheduler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobDependencyService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobDependencyService.class);
    
    @Autowired
    private JobDependencyRepository jobDependencyRepository;
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private RedisCacheService cacheService;
    
    // Create a new job dependency
    public JobDependency createJobDependency(JobDependency dependency) {
        logger.info("Creating dependency: Job {} depends on Job {}", 
                   dependency.getChildJobId(), dependency.getParentJobId());
        
        // Validate that both jobs exist
        if (!jobRepository.existsById(dependency.getJobId()) || 
            !jobRepository.existsById(dependency.getDependencyJobId())) {
            throw new IllegalArgumentException("One or both jobs do not exist");
        }
        
        // Check for circular dependencies
        if (wouldCreateCircularDependency(dependency.getJobId(), dependency.getDependencyJobId())) {
            throw new IllegalArgumentException("This dependency would create a circular dependency");
        }
        
        // Set default values
        if (dependency.getDependencyPriority() == null) {
            dependency.setDependencyPriority(1);
        }
        if (dependency.getCheckIntervalSeconds() == null) {
            dependency.setCheckIntervalSeconds(30);
        }
        if (dependency.getMaxRetries() == null) {
            dependency.setMaxRetries(3);
        }
        
        JobDependency savedDependency = jobDependencyRepository.save(dependency);
        
        // Cache the dependency
        String cacheKey = "dependency:" + dependency.getJobId() + ":" + dependency.getDependencyJobId();
        cacheService.cacheDependency(cacheKey, savedDependency, 300); // Cache for 5 minutes
        
        logger.info("Created dependency with ID: {}", savedDependency.getId());
        return savedDependency;
    }
    
    // Get all dependencies with pagination
    public Page<JobDependency> getAllDependencies(Pageable pageable) {
        return jobDependencyRepository.findAll(pageable);
    }
    
    // Get dependency by ID
    public Optional<JobDependency> getDependencyById(Long id) {
        return jobDependencyRepository.findById(id);
    }
    
    // Get dependencies for a specific job
    public List<JobDependency> getDependenciesForJob(Long jobId) {
        String cacheKey = "job_dependencies:" + jobId;
        
        // Try to get from cache first
        List<JobDependency> cachedDependencies = cacheService.getCachedJobDependencies(cacheKey);
        if (cachedDependencies != null) {
            logger.debug("Retrieved dependencies for job {} from cache", jobId);
            return cachedDependencies;
        }
        
        // Get from database and cache
        List<JobDependency> dependencies = jobDependencyRepository.findByJobId(jobId);
        cacheService.cacheJobDependencies(cacheKey, dependencies, 300);
        
        logger.debug("Retrieved {} dependencies for job {} from database", dependencies.size(), jobId);
        return dependencies;
    }
    
    // Get unsatisfied dependencies for a job
    public List<JobDependency> getUnsatisfiedDependencies(Long jobId) {
        return jobDependencyRepository.findUnsatisfiedDependenciesByJobId(jobId);
    }
    
    // Get blocking dependencies for a job
    public List<JobDependency> getBlockingDependencies(Long jobId) {
        return getDependenciesForJob(jobId).stream()
                .filter(JobDependency::isBlocking)
                .filter(JobDependency::isNotSatisfied)
                .collect(Collectors.toList());
    }
    
    // Check if all dependencies are satisfied for a job
    public boolean areAllDependenciesSatisfied(Long jobId) {
        return jobDependencyRepository.areAllDependenciesSatisfied(jobId);
    }
    
    // Check if all blocking dependencies are satisfied
    public boolean areBlockingDependenciesSatisfied(Long jobId) {
        List<JobDependency> blockingDeps = getBlockingDependencies(jobId);
        return blockingDeps.isEmpty();
    }
    
    // Update dependency
    public JobDependency updateDependency(JobDependency dependency) {
        logger.info("Updating dependency: {}", dependency.getId());
        
        JobDependency updatedDependency = jobDependencyRepository.save(dependency);
        
        // Update cache
        String cacheKey = "dependency:" + dependency.getJobId() + ":" + dependency.getDependencyJobId();
        cacheService.evictDependencyFromCache(cacheKey);
        cacheService.cacheDependency(cacheKey, updatedDependency, 300);
        
        // Invalidate job dependencies cache
        cacheService.evictJobDependenciesFromCache("job_dependencies:" + dependency.getJobId());
        
        return updatedDependency;
    }
    
    // Delete dependency
    public void deleteDependency(Long id) {
        logger.info("Deleting dependency: {}", id);
        
        Optional<JobDependency> dependencyOpt = jobDependencyRepository.findById(id);
        if (dependencyOpt.isPresent()) {
            JobDependency dependency = dependencyOpt.get();
            
            // Remove from cache
            String cacheKey = "dependency:" + dependency.getJobId() + ":" + dependency.getDependencyJobId();
            cacheService.evictDependencyFromCache(cacheKey);
            cacheService.evictJobDependenciesFromCache("job_dependencies:" + dependency.getJobId());
            
            // Delete from database
            jobDependencyRepository.deleteById(id);
            
            logger.info("Deleted dependency: {}", id);
        }
    }
    
    // Mark dependency as satisfied
    public JobDependency markDependencyAsSatisfied(Long dependencyId) {
        Optional<JobDependency> dependencyOpt = jobDependencyRepository.findById(dependencyId);
        if (dependencyOpt.isPresent()) {
            JobDependency dependency = dependencyOpt.get();
            dependency.markAsSatisfied();
            
            JobDependency updatedDependency = jobDependencyRepository.save(dependency);
            
            // Update cache
            String cacheKey = "dependency:" + dependency.getJobId() + ":" + dependency.getDependencyJobId();
            cacheService.cacheDependency(cacheKey, updatedDependency, 300);
            cacheService.evictJobDependenciesFromCache("job_dependencies:" + dependency.getJobId());
            
            logger.info("Marked dependency {} as satisfied", dependencyId);
            return updatedDependency;
        } else {
            throw new RuntimeException("Dependency not found: " + dependencyId);
        }
    }
    
    // Get jobs that are ready to execute (all dependencies satisfied)
    public List<Long> getJobsReadyToExecute() {
        return jobDependencyRepository.findJobsWithSatisfiedDependencies();
    }
    
    // Get dependency chain for a job
    public List<Object[]> getDependencyChain(Long jobId) {
        return jobDependencyRepository.findDependencyChain(jobId);
    }
    
    // Check for circular dependencies
    public boolean wouldCreateCircularDependency(Long jobId, Long dependencyJobId) {
        // Simple check: if dependencyJobId depends on jobId, it would create a circle
        List<JobDependency> existingDeps = getDependenciesForJob(dependencyJobId);
        
        for (JobDependency dep : existingDeps) {
            if (dep.getDependencyJobId().equals(jobId)) {
                return true;
            }
            // Recursive check for deeper cycles
            if (wouldCreateCircularDependency(jobId, dep.getDependencyJobId())) {
                return true;
            }
        }
        
        return false;
    }
    
    // Get dependencies that need to be checked
    public List<JobDependency> getDependenciesNeedingCheck() {
        return jobDependencyRepository.findAll().stream()
                .filter(dep -> !dep.getIsSatisfied())
                .filter(JobDependency::needsRecheck)
                .collect(Collectors.toList());
    }
    
    // Get high priority dependencies
    public List<JobDependency> getHighPriorityDependencies() {
        return jobDependencyRepository.findAll().stream()
                .filter(JobDependency::isHighPriority)
                .filter(JobDependency::isNotSatisfied)
                .collect(Collectors.toList());
    }
    
    // Get timed out dependencies
    public List<JobDependency> getTimedOutDependencies() {
        return jobDependencyRepository.findAll().stream()
                .filter(JobDependency::hasTimedOut)
                .filter(JobDependency::isNotSatisfied)
                .collect(Collectors.toList());
    }
    
    // Process dependency check
    public void processDependencyCheck(Long dependencyId) {
        Optional<JobDependency> dependencyOpt = jobDependencyRepository.findById(dependencyId);
        if (dependencyOpt.isPresent()) {
            JobDependency dependency = dependencyOpt.get();
            
            if (dependency.getIsSatisfied()) {
                return; // Already satisfied
            }
            
            // Update last checked time
            dependency.setLastCheckedAt(LocalDateTime.now());
            
            // Check if parent job status satisfies the dependency
            boolean isSatisfied = checkDependencySatisfaction(dependency);
            
            if (isSatisfied) {
                dependency.markAsSatisfied();
                logger.info("Dependency {} satisfied", dependencyId);
            } else if (dependency.hasTimedOut()) {
                handleDependencyTimeout(dependency);
            } else if (dependency.shouldRetry()) {
                dependency.incrementRetryCount();
                logger.debug("Dependency {} check failed, retry count: {}", 
                           dependencyId, dependency.getRetryCount());
            }
            
            jobDependencyRepository.save(dependency);
            
            // Update cache
            String cacheKey = "dependency:" + dependency.getJobId() + ":" + dependency.getDependencyJobId();
            cacheService.cacheDependency(cacheKey, dependency, 300);
        }
    }
    
    // Check if a specific dependency is satisfied
    private boolean checkDependencySatisfaction(JobDependency dependency) {
        // Implementation would check the parent job's status based on dependency type
        // This is a simplified version - in production, you'd check actual job status
        
        Optional<com.jobscheduler.model.Job> parentJobOpt = jobRepository.findById(dependency.getDependencyJobId());
        if (!parentJobOpt.isPresent()) {
            return false;
        }
        
        com.jobscheduler.model.Job parentJob = parentJobOpt.get();
        
        switch (dependency.getDependencyType()) {
            case MUST_COMPLETE:
                return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
            case MUST_START:
                return parentJob.getStatus() == com.jobscheduler.model.JobStatus.RUNNING ||
                       parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
            case MUST_SUCCEED:
                return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
            case CONDITIONAL:
                return evaluateConditionalDependency(dependency, parentJob);
            case SOFT_DEPENDENCY:
                return true; // Soft dependencies don't block
            case TIME_BASED:
                return evaluateTimeDependency(dependency, parentJob);
            case RESOURCE_BASED:
                return evaluateResourceDependency(dependency, parentJob);
            default:
                return false;
        }
    }
    
    // Handle dependency timeout
    private void handleDependencyTimeout(JobDependency dependency) {
        switch (dependency.getFailureAction()) {
            case BLOCK:
                logger.warn("Dependency {} timed out and will block job execution", dependency.getId());
                break;
            case PROCEED:
            case WARN:
                dependency.markAsSatisfied(); // Allow job to proceed
                logger.warn("Dependency {} timed out but allowing job to proceed", dependency.getId());
                break;
            case RETRY:
                if (dependency.shouldRetry()) {
                    dependency.incrementRetryCount();
                    logger.info("Dependency {} timed out, retrying ({}/{})", 
                               dependency.getId(), dependency.getRetryCount(), dependency.getMaxRetries());
                }
                break;
            case SKIP:
                dependency.markAsSatisfied();
                logger.info("Dependency {} timed out and was skipped", dependency.getId());
                break;
            case ESCALATE:
                logger.error("Dependency {} timed out and requires escalation", dependency.getId());
                // In production, you'd send alerts/notifications here
                break;
        }
    }
    
    // Evaluate conditional dependency
    private boolean evaluateConditionalDependency(JobDependency dependency, com.jobscheduler.model.Job parentJob) {
        String validationRule = dependency.getValidationRule();
        if (validationRule == null || validationRule.isEmpty()) {
            return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
        }
        
        // In production, you'd implement a rule engine here
        // For now, just return completed status
        return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
    }
    
    // Evaluate time-based dependency
    private boolean evaluateTimeDependency(JobDependency dependency, com.jobscheduler.model.Job parentJob) {
        // Check if enough time has passed since parent job completion
        if (parentJob.getCompletedAt() != null && dependency.getTimeoutMinutes() != null) {
            LocalDateTime timeThreshold = parentJob.getCompletedAt().plusMinutes(dependency.getTimeoutMinutes());
            return LocalDateTime.now().isAfter(timeThreshold);
        }
        
        return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
    }
    
    // Evaluate resource-based dependency
    private boolean evaluateResourceDependency(JobDependency dependency, com.jobscheduler.model.Job parentJob) {
        // Check if required resources are available
        // This would integrate with resource management system
        return parentJob.getStatus() == com.jobscheduler.model.JobStatus.COMPLETED;
    }
    
    // Get dependency statistics
    public DependencyStatistics getDependencyStatistics() {
        long totalDependencies = jobDependencyRepository.count();
        long satisfiedDependencies = jobDependencyRepository.findAll().stream()
                .mapToLong(dep -> dep.getIsSatisfied() ? 1 : 0)
                .sum();
        long blockingDependencies = jobDependencyRepository.findAll().stream()
                .mapToLong(dep -> dep.isBlocking() && dep.isNotSatisfied() ? 1 : 0)
                .sum();
        long timedOutDependencies = jobDependencyRepository.findAll().stream()
                .mapToLong(dep -> dep.hasTimedOut() ? 1 : 0)
                .sum();
        
        return new DependencyStatistics(totalDependencies, satisfiedDependencies, 
                                       blockingDependencies, timedOutDependencies);
    }
    
    // Bulk create dependencies
    public List<JobDependency> createMultipleDependencies(List<JobDependency> dependencies) {
        List<JobDependency> savedDependencies = new ArrayList<>();
        
        for (JobDependency dependency : dependencies) {
            try {
                JobDependency saved = createJobDependency(dependency);
                savedDependencies.add(saved);
            } catch (Exception e) {
                logger.error("Failed to create dependency for job {} -> {}: {}", 
                           dependency.getJobId(), dependency.getDependencyJobId(), e.getMessage());
            }
        }
        
        return savedDependencies;
    }
    
    // Remove all dependencies for a job
    public void removeAllDependenciesForJob(Long jobId) {
        List<JobDependency> dependencies = getDependenciesForJob(jobId);
        
        for (JobDependency dependency : dependencies) {
            deleteDependency(dependency.getId());
        }
        
        logger.info("Removed {} dependencies for job {}", dependencies.size(), jobId);
    }
    
    // Inner class for dependency statistics
    public static class DependencyStatistics {
        private final long totalDependencies;
        private final long satisfiedDependencies;
        private final long blockingDependencies;
        private final long timedOutDependencies;
        
        public DependencyStatistics(long totalDependencies, long satisfiedDependencies,
                                   long blockingDependencies, long timedOutDependencies) {
            this.totalDependencies = totalDependencies;
            this.satisfiedDependencies = satisfiedDependencies;
            this.blockingDependencies = blockingDependencies;
            this.timedOutDependencies = timedOutDependencies;
        }
        
        // Getters
        public long getTotalDependencies() { return totalDependencies; }
        public long getSatisfiedDependencies() { return satisfiedDependencies; }
        public long getBlockingDependencies() { return blockingDependencies; }
        public long getTimedOutDependencies() { return timedOutDependencies; }
        public long getUnsatisfiedDependencies() { return totalDependencies - satisfiedDependencies; }
        public double getSatisfactionRate() { 
            return totalDependencies > 0 ? (double) satisfiedDependencies / totalDependencies * 100 : 0.0; 
        }
    }
}
