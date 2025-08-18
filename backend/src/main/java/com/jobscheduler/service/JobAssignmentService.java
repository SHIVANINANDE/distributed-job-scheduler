package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Advanced Job Assignment Service with intelligent load balancing and assignment strategies
 * Implements round-robin, capacity-aware, and performance-based assignment algorithms
 */
@Service
@Transactional
public class JobAssignmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobAssignmentService.class);
    
    @Autowired
    private WorkerService workerService;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private WorkerPerformanceService workerPerformanceService;
    
    @Autowired
    private CacheService cacheService;
    
    // Configuration properties
    @Value("${job.assignment.strategy:INTELLIGENT}")
    private String defaultAssignmentStrategy;
    
    @Value("${job.assignment.round-robin.enabled:true}")
    private boolean roundRobinEnabled;
    
    @Value("${job.assignment.capacity-aware.enabled:true}")
    private boolean capacityAwareEnabled;
    
    @Value("${job.assignment.performance-based.enabled:true}")
    private boolean performanceBasedEnabled;
    
    @Value("${job.assignment.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${job.assignment.reassignment-timeout-minutes:10}")
    private int reassignmentTimeoutMinutes;
    
    @Value("${job.assignment.load-balance-threshold:0.8}")
    private double loadBalanceThreshold;
    
    // Round-robin state management
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Map<String, AtomicInteger> priorityRoundRobinIndex = new ConcurrentHashMap<>();
    
    // Assignment statistics
    private final Map<String, AssignmentStats> workerAssignmentStats = new ConcurrentHashMap<>();
    
    /**
     * Assignment strategies enumeration
     */
    public enum AssignmentStrategy {
        ROUND_ROBIN,
        CAPACITY_AWARE,
        PERFORMANCE_BASED,
        INTELLIGENT,
        LEAST_LOADED,
        PRIORITY_BASED
    }
    
    /**
     * Main job assignment method with intelligent strategy selection
     * @param job The job to assign
     * @return The assigned worker, or null if no suitable worker available
     */
    public Worker assignJob(Job job) {
        logger.info("Attempting to assign job {} using strategy: {}", job.getId(), defaultAssignmentStrategy);
        
        try {
            // Get available workers
            List<Worker> availableWorkers = getAvailableWorkers();
            
            if (availableWorkers.isEmpty()) {
                logger.warn("No available workers for job assignment: {}", job.getId());
                return null;
            }
            
            // Apply assignment strategy
            AssignmentStrategy strategy = AssignmentStrategy.valueOf(defaultAssignmentStrategy);
            Worker assignedWorker = null;
            
            switch (strategy) {
                case ROUND_ROBIN:
                    assignedWorker = assignJobRoundRobin(job, availableWorkers);
                    break;
                case CAPACITY_AWARE:
                    assignedWorker = assignJobCapacityAware(job, availableWorkers);
                    break;
                case PERFORMANCE_BASED:
                    assignedWorker = assignJobPerformanceBased(job, availableWorkers);
                    break;
                case INTELLIGENT:
                    assignedWorker = assignJobIntelligent(job, availableWorkers);
                    break;
                case LEAST_LOADED:
                    assignedWorker = assignJobLeastLoaded(job, availableWorkers);
                    break;
                case PRIORITY_BASED:
                    assignedWorker = assignJobPriorityBased(job, availableWorkers);
                    break;
                default:
                    assignedWorker = assignJobIntelligent(job, availableWorkers);
            }
            
            if (assignedWorker != null) {
                // Execute the assignment
                boolean success = executeJobAssignment(job, assignedWorker);
                if (success) {
                    updateAssignmentStats(assignedWorker.getWorkerId(), true);
                    logger.info("Successfully assigned job {} to worker {}", job.getId(), assignedWorker.getWorkerId());
                    return assignedWorker;
                } else {
                    updateAssignmentStats(assignedWorker.getWorkerId(), false);
                    logger.error("Failed to execute job assignment for job {} to worker {}", job.getId(), assignedWorker.getWorkerId());
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error during job assignment for job {}: {}", job.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Round-robin assignment strategy
     */
    private Worker assignJobRoundRobin(Job job, List<Worker> availableWorkers) {
        logger.debug("Using round-robin assignment for job {}", job.getId());
        
        if (availableWorkers.isEmpty()) {
            return null;
        }
        
        // Use priority-specific round-robin if job has high priority
        if (job.getPriority() != null && job.getPriority() >= 500) {
            String priorityKey = "high_priority";
            AtomicInteger priorityIndex = priorityRoundRobinIndex.computeIfAbsent(priorityKey, k -> new AtomicInteger(0));
            
            // Filter high-capacity workers for high-priority jobs
            List<Worker> highCapacityWorkers = availableWorkers.stream()
                    .filter(w -> w.getMaxConcurrentJobs() >= 5)
                    .filter(w -> w.getAvailableCapacity() > 0)
                    .collect(Collectors.toList());
            
            if (!highCapacityWorkers.isEmpty()) {
                int index = priorityIndex.getAndIncrement() % highCapacityWorkers.size();
                return highCapacityWorkers.get(index);
            }
        }
        
        // Standard round-robin
        int index = roundRobinIndex.getAndIncrement() % availableWorkers.size();
        return availableWorkers.get(index);
    }
    
    /**
     * Capacity-aware assignment strategy
     */
    private Worker assignJobCapacityAware(Job job, List<Worker> availableWorkers) {
        logger.debug("Using capacity-aware assignment for job {}", job.getId());
        
        return availableWorkers.stream()
                .filter(worker -> worker.getAvailableCapacity() > 0)
                .filter(worker -> canWorkerHandleJob(worker, job))
                .max(Comparator.comparing(Worker::getAvailableCapacity)
                        .thenComparing(worker -> -worker.getCurrentJobCount()))
                .orElse(null);
    }
    
    /**
     * Performance-based assignment strategy
     */
    private Worker assignJobPerformanceBased(Job job, List<Worker> availableWorkers) {
        logger.debug("Using performance-based assignment for job {}", job.getId());
        
        return availableWorkers.stream()
                .filter(worker -> worker.getAvailableCapacity() > 0)
                .filter(worker -> canWorkerHandleJob(worker, job))
                .max(Comparator.comparing(this::calculateWorkerPerformanceScore)
                        .thenComparing(Worker::getSuccessRate)
                        .thenComparing(worker -> -worker.getLoadPercentage()))
                .orElse(null);
    }
    
    /**
     * Intelligent assignment strategy (combines multiple factors)
     */
    private Worker assignJobIntelligent(Job job, List<Worker> availableWorkers) {
        logger.debug("Using intelligent assignment for job {}", job.getId());
        
        return availableWorkers.stream()
                .filter(worker -> worker.getAvailableCapacity() > 0)
                .filter(worker -> canWorkerHandleJob(worker, job))
                .max(Comparator.comparing(worker -> calculateIntelligentScore(worker, job)))
                .orElse(null);
    }
    
    /**
     * Least loaded assignment strategy
     */
    private Worker assignJobLeastLoaded(Job job, List<Worker> availableWorkers) {
        logger.debug("Using least-loaded assignment for job {}", job.getId());
        
        return availableWorkers.stream()
                .filter(worker -> worker.getAvailableCapacity() > 0)
                .filter(worker -> canWorkerHandleJob(worker, job))
                .min(Comparator.comparing(Worker::getLoadPercentage)
                        .thenComparing(Worker::getCurrentJobCount))
                .orElse(null);
    }
    
    /**
     * Priority-based assignment strategy
     */
    private Worker assignJobPriorityBased(Job job, List<Worker> availableWorkers) {
        logger.debug("Using priority-based assignment for job {}", job.getId());
        
        // For high-priority jobs, prefer workers with better performance
        if (job.getPriority() != null && job.getPriority() >= 500) {
            return availableWorkers.stream()
                    .filter(worker -> worker.getAvailableCapacity() > 0)
                    .filter(worker -> canWorkerHandleJob(worker, job))
                    .filter(worker -> worker.getSuccessRate() >= 90.0)
                    .min(Comparator.comparing(Worker::getCurrentJobCount))
                    .orElse(assignJobLeastLoaded(job, availableWorkers));
        }
        
        // For normal priority jobs, use capacity-aware assignment
        return assignJobCapacityAware(job, availableWorkers);
    }
    
    /**
     * Calculate intelligent assignment score
     */
    private double calculateIntelligentScore(Worker worker, Job job) {
        double capacityScore = (double) worker.getAvailableCapacity() / worker.getMaxConcurrentJobs();
        double performanceScore = worker.getSuccessRate() / 100.0;
        double loadScore = 1.0 - (worker.getLoadPercentage() / 100.0);
        double experienceScore = Math.min(1.0, worker.getTotalJobsProcessed() / 1000.0);
        
        // Priority bonus for high-priority jobs
        double priorityBonus = 1.0;
        if (job.getPriority() != null && job.getPriority() >= 500) {
            priorityBonus = worker.getSuccessRate() >= 95.0 ? 1.5 : 1.2;
        }
        
        return (capacityScore * 0.3 + performanceScore * 0.3 + loadScore * 0.25 + experienceScore * 0.15) * priorityBonus;
    }
    
    /**
     * Calculate worker performance score
     */
    private double calculateWorkerPerformanceScore(Worker worker) {
        double successRate = worker.getSuccessRate();
        double efficiency = 100.0 - worker.getLoadPercentage();
        long totalJobs = worker.getTotalJobsProcessed();
        
        // Experience factor
        double experienceFactor = Math.min(2.0, 1.0 + (totalJobs / 1000.0));
        
        return (successRate * 0.4 + efficiency * 0.3 + Math.min(100, totalJobs) * 0.3) * experienceFactor;
    }
    
    /**
     * Check if worker can handle the job
     */
    private boolean canWorkerHandleJob(Worker worker, Job job) {
        // Basic availability check
        if (!worker.isAvailable()) {
            return false;
        }
        
        // Capacity check
        if (worker.getAvailableCapacity() <= 0) {
            return false;
        }
        
        // Priority threshold check
        if (job.getPriority() != null && worker.getPriorityThreshold() != null) {
            if (job.getPriority() < worker.getPriorityThreshold()) {
                return false;
            }
        }
        
        // Performance check for high-priority jobs
        if (job.getPriority() != null && job.getPriority() >= 500) {
            if (worker.getSuccessRate() < 85.0) {
                return false;
            }
        }
        
        // Load check
        if (worker.getLoadPercentage() > 95.0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Execute the actual job assignment
     */
    private boolean executeJobAssignment(Job job, Worker worker) {
        try {
            // Update job with worker assignment
            job.assignToWorker(worker.getWorkerId(), worker.getName(), worker.getHostAddress(), worker.getPort());
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            
            // Update worker with job assignment
            worker.assignJob(job.getId());
            
            // Save updates
            jobService.updateJob(job);
            workerService.updateWorker(worker);
            
            // Cache the assignment
            cacheAssignment(job.getId().toString(), worker.getWorkerId());
            
            logger.info("Job {} assigned to worker {} successfully", job.getId(), worker.getWorkerId());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to execute job assignment for job {} to worker {}: {}", 
                        job.getId(), worker.getWorkerId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Handle job reassignment on worker failure
     */
    public boolean reassignJob(Long jobId, String failedWorkerId, String failureReason) {
        logger.warn("Reassigning job {} due to worker {} failure: {}", jobId, failedWorkerId, failureReason);
        
        try {
            Job job = jobService.getJobByIdDirect(jobId);
            if (job == null) {
                logger.error("Job {} not found for reassignment", jobId);
                return false;
            }
            
            // Clear current assignment
            job.unassignFromWorker();
            job.setStatus(JobStatus.PENDING);
            job.setErrorMessage("Reassigned due to worker failure: " + failureReason);
            
            // Update retry count
            job.setRetryCount((job.getRetryCount() != null ? job.getRetryCount() : 0) + 1);
            
            // Check retry limit
            if (job.getRetryCount() > maxRetryAttempts) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Max retry attempts exceeded after worker failures");
                jobService.updateJob(job);
                logger.error("Job {} failed after {} retry attempts", jobId, maxRetryAttempts);
                return false;
            }
            
            // Save job state
            jobService.updateJob(job);
            
            // Update failed worker
            updateWorkerAfterFailure(failedWorkerId, jobId);
            
            // Remove from cache
            evictAssignmentCache(jobId.toString());
            
            // Attempt reassignment with delay to avoid immediate re-failure
            Thread.sleep(1000); // 1 second delay
            
            Worker newWorker = assignJob(job);
            if (newWorker != null) {
                logger.info("Successfully reassigned job {} from worker {} to worker {}", 
                           jobId, failedWorkerId, newWorker.getWorkerId());
                return true;
            } else {
                logger.warn("Failed to find alternative worker for job {} reassignment", jobId);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during job reassignment for job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Update worker after job failure
     */
    private void updateWorkerAfterFailure(String workerId, Long jobId) {
        try {
            Worker worker = workerService.getWorkerByWorkerIdDirect(workerId);
            if (worker != null) {
                worker.unassignJob(jobId);
                worker.recordJobCompletion(false); // Record as failed
                workerService.updateWorker(worker);
                
                // Update assignment stats
                updateAssignmentStats(workerId, false);
            }
        } catch (Exception e) {
            logger.error("Error updating worker {} after failure: {}", workerId, e.getMessage(), e);
        }
    }
    
    /**
     * Get available workers with intelligent filtering
     */
    private List<Worker> getAvailableWorkers() {
        List<Worker> workers = workerService.getAvailableWorkers();
        
        // Filter out overloaded workers
        return workers.stream()
                .filter(worker -> worker.getLoadPercentage() < 100.0)
                .filter(worker -> worker.getStatus() == WorkerStatus.ACTIVE)
                .filter(worker -> !isWorkerBlacklisted(worker.getWorkerId()))
                .sorted(Comparator.comparing(Worker::getLoadPercentage))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if worker is blacklisted due to recent failures
     */
    private boolean isWorkerBlacklisted(String workerId) {
        try {
            String blacklistKey = "worker:blacklist:" + workerId;
            return cacheService.get(blacklistKey, Boolean.class).orElse(false);
        } catch (Exception e) {
            logger.debug("Error checking worker blacklist status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Update assignment statistics
     */
    private void updateAssignmentStats(String workerId, boolean success) {
        AssignmentStats stats = workerAssignmentStats.computeIfAbsent(workerId, k -> new AssignmentStats());
        stats.recordAssignment(success);
    }
    
    /**
     * Cache job assignment
     */
    private void cacheAssignment(String jobId, String workerId) {
        try {
            String assignmentKey = "job:assignment:" + jobId;
            cacheService.put(assignmentKey, workerId, 3600); // 1 hour TTL
        } catch (Exception e) {
            logger.debug("Error caching job assignment: {}", e.getMessage());
        }
    }
    
    /**
     * Evict assignment from cache
     */
    private void evictAssignmentCache(String jobId) {
        try {
            String assignmentKey = "job:assignment:" + jobId;
            cacheService.evict(assignmentKey);
        } catch (Exception e) {
            logger.debug("Error evicting assignment cache: {}", e.getMessage());
        }
    }
    
    /**
     * Get assignment statistics for a worker
     */
    public AssignmentStats getWorkerAssignmentStats(String workerId) {
        return workerAssignmentStats.getOrDefault(workerId, new AssignmentStats());
    }
    
    /**
     * Get all assignment statistics
     */
    public Map<String, AssignmentStats> getAllAssignmentStats() {
        return new HashMap<>(workerAssignmentStats);
    }
    
    /**
     * Assignment statistics inner class
     */
    public static class AssignmentStats {
        private long totalAssignments = 0;
        private long successfulAssignments = 0;
        private long failedAssignments = 0;
        private LocalDateTime lastAssignment;
        
        public void recordAssignment(boolean success) {
            totalAssignments++;
            if (success) {
                successfulAssignments++;
            } else {
                failedAssignments++;
            }
            lastAssignment = LocalDateTime.now();
        }
        
        public double getSuccessRate() {
            return totalAssignments > 0 ? (double) successfulAssignments / totalAssignments * 100.0 : 0.0;
        }
        
        // Getters
        public long getTotalAssignments() { return totalAssignments; }
        public long getSuccessfulAssignments() { return successfulAssignments; }
        public long getFailedAssignments() { return failedAssignments; }
        public LocalDateTime getLastAssignment() { return lastAssignment; }
    }
    
    /**
     * Assign job to worker (returns boolean for success/failure)
     */
    public boolean assignJobToWorker(Job job) {
        Worker assignedWorker = assignJob(job);
        return assignedWorker != null;
    }
}
