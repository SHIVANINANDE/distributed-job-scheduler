package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.Worker;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Advanced Load Balancing Service for intelligent workload distribution
 * Implements multiple load balancing algorithms and queue management strategies
 */
@Service
public class LoadBalancingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancingService.class);
    
    @Autowired
    private WorkerService workerService;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private WorkerPerformanceService workerPerformanceService;
    
    @Autowired
    private JobAssignmentService jobAssignmentService;
    
    @Autowired
    private CacheService cacheService;
    
    // Configuration
    @Value("${load.balancing.enabled:true}")
    private boolean loadBalancingEnabled;
    
    @Value("${load.balancing.algorithm:INTELLIGENT}")
    private String loadBalancingAlgorithm;
    
    @Value("${load.balancing.threshold.high:85.0}")
    private double highLoadThreshold;
    
    @Value("${load.balancing.threshold.critical:95.0}")
    private double criticalLoadThreshold;
    
    @Value("${load.balancing.rebalance.interval-seconds:60}")
    private int rebalanceIntervalSeconds;
    
    @Value("${load.balancing.queue.high-priority.size:1000}")
    private int highPriorityQueueSize;
    
    @Value("${load.balancing.queue.normal-priority.size:5000}")
    private int normalPriorityQueueSize;
    
    @Value("${load.balancing.queue.low-priority.size:10000}")
    private int lowPriorityQueueSize;
    
    // Queue management
    private final Queue<Job> highPriorityQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Job> normalPriorityQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Job> lowPriorityQueue = new ConcurrentLinkedQueue<>();
    
    // Load balancing state
    private final Map<String, WorkerLoadInfo> workerLoadInfoMap = new ConcurrentHashMap<>();
    private final Map<String, LoadBalancingMetrics> loadMetrics = new ConcurrentHashMap<>();
    
    /**
     * Load Balancing Algorithms
     */
    public enum LoadBalancingAlgorithm {
        ROUND_ROBIN,
        LEAST_CONNECTIONS,
        WEIGHTED_ROUND_ROBIN,
        LEAST_RESPONSE_TIME,
        RESOURCE_BASED,
        INTELLIGENT,
        ADAPTIVE
    }
    
    /**
     * Worker Load Information
     */
    public static class WorkerLoadInfo {
        private String workerId;
        private double currentLoad;
        private int activeJobs;
        private int maxCapacity;
        private double averageResponseTime;
        private LocalDateTime lastUpdated;
        private boolean isOverloaded;
        private int queuedJobs;
        
        public WorkerLoadInfo(String workerId) {
            this.workerId = workerId;
            this.lastUpdated = LocalDateTime.now();
        }
        
        public void updateLoad(Worker worker) {
            this.currentLoad = worker.getLoadPercentage();
            this.activeJobs = worker.getCurrentJobCount();
            this.maxCapacity = worker.getMaxConcurrentJobs();
            this.averageResponseTime = worker.getAverageExecutionTime();
            this.isOverloaded = currentLoad > 90.0;
            this.lastUpdated = LocalDateTime.now();
        }
        
        public double getAvailableCapacityPercentage() {
            return maxCapacity > 0 ? ((double) (maxCapacity - activeJobs) / maxCapacity) * 100.0 : 0.0;
        }
        
        public boolean canAcceptMoreJobs() {
            return !isOverloaded && activeJobs < maxCapacity && currentLoad < 95.0;
        }
        
        // Getters and setters
        public String getWorkerId() { return workerId; }
        public double getCurrentLoad() { return currentLoad; }
        public int getActiveJobs() { return activeJobs; }
        public int getMaxCapacity() { return maxCapacity; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public boolean isOverloaded() { return isOverloaded; }
        public int getQueuedJobs() { return queuedJobs; }
        public void setQueuedJobs(int queuedJobs) { this.queuedJobs = queuedJobs; }
    }
    
    /**
     * Load Balancing Metrics
     */
    public static class LoadBalancingMetrics {
        private long totalJobsBalanced = 0;
        private long successfulBalancing = 0;
        private long failedBalancing = 0;
        private double averageBalancingTime = 0.0;
        private LocalDateTime lastBalancing;
        private Map<String, Integer> algorithmUsage = new HashMap<>();
        
        public void recordBalancing(boolean success, long timeMs, String algorithm) {
            totalJobsBalanced++;
            if (success) {
                successfulBalancing++;
            } else {
                failedBalancing++;
            }
            
            // Update average balancing time
            averageBalancingTime = ((averageBalancingTime * (totalJobsBalanced - 1)) + timeMs) / totalJobsBalanced;
            
            lastBalancing = LocalDateTime.now();
            algorithmUsage.merge(algorithm, 1, Integer::sum);
        }
        
        public double getSuccessRate() {
            return totalJobsBalanced > 0 ? (double) successfulBalancing / totalJobsBalanced * 100.0 : 0.0;
        }
        
        // Getters
        public long getTotalJobsBalanced() { return totalJobsBalanced; }
        public long getSuccessfulBalancing() { return successfulBalancing; }
        public long getFailedBalancing() { return failedBalancing; }
        public double getAverageBalancingTime() { return averageBalancingTime; }
        public LocalDateTime getLastBalancing() { return lastBalancing; }
        public Map<String, Integer> getAlgorithmUsage() { return new HashMap<>(algorithmUsage); }
    }
    
    /**
     * Add job to appropriate priority queue
     */
    public boolean enqueueJob(Job job) {
        if (!loadBalancingEnabled) {
            return false;
        }
        
        try {
            logger.debug("Enqueueing job {} with priority {}", job.getId(), job.getPriority());
            
            Queue<Job> targetQueue = selectQueueByPriority(job);
            
            // Check queue capacity
            if (isQueueFull(targetQueue, job.getPriority())) {
                logger.warn("Queue full for priority {}. Job {} cannot be enqueued", job.getPriority(), job.getId());
                return false;
            }
            
            boolean added = targetQueue.offer(job);
            if (added) {
                updateQueueMetrics();
                logger.info("Job {} enqueued successfully in {} priority queue", 
                           job.getId(), getPriorityName(job.getPriority()));
            }
            
            return added;
            
        } catch (Exception e) {
            logger.error("Error enqueueing job {}: {}", job.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process jobs from queues with intelligent load balancing
     */
    @Scheduled(fixedRateString = "${load.balancing.process.interval-ms:5000}")
    public void processJobQueues() {
        if (!loadBalancingEnabled) {
            return;
        }
        
        try {
            logger.debug("Processing job queues. High: {}, Normal: {}, Low: {}", 
                        highPriorityQueue.size(), normalPriorityQueue.size(), lowPriorityQueue.size());
            
            // Update worker load information
            updateWorkerLoadInfo();
            
            // Process high priority jobs first
            processQueue(highPriorityQueue, "HIGH");
            
            // Process normal priority jobs
            processQueue(normalPriorityQueue, "NORMAL");
            
            // Process low priority jobs if workers have capacity
            if (hasAvailableWorkers()) {
                processQueue(lowPriorityQueue, "LOW");
            }
            
            // Update metrics
            updateQueueMetrics();
            
        } catch (Exception e) {
            logger.error("Error processing job queues: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Process specific queue
     */
    private void processQueue(Queue<Job> queue, String priorityName) {
        List<Worker> availableWorkers = getAvailableWorkersForPriority(priorityName);
        
        if (availableWorkers.isEmpty()) {
            logger.debug("No available workers for {} priority jobs", priorityName);
            return;
        }
        
        int processedJobs = 0;
        while (!queue.isEmpty() && processedJobs < 50) { // Limit batch size
            Job job = queue.poll();
            if (job != null) {
                long startTime = System.currentTimeMillis();
                
                Worker assignedWorker = assignJobWithLoadBalancing(job, availableWorkers);
                
                long balancingTime = System.currentTimeMillis() - startTime;
                String algorithm = loadBalancingAlgorithm;
                
                LoadBalancingMetrics metrics = loadMetrics.computeIfAbsent("system", k -> new LoadBalancingMetrics());
                metrics.recordBalancing(assignedWorker != null, balancingTime, algorithm);
                
                if (assignedWorker != null) {
                    logger.debug("Job {} assigned to worker {} using {} algorithm", 
                               job.getId(), assignedWorker.getWorkerId(), algorithm);
                    
                    // Update worker load info
                    updateWorkerLoadAfterAssignment(assignedWorker.getWorkerId());
                    
                    // Remove worker from available list if at capacity
                    if (!assignedWorker.canAcceptMoreJobs()) {
                        availableWorkers.remove(assignedWorker);
                    }
                } else {
                    // Re-queue job if no worker available
                    queue.offer(job);
                    break; // Stop processing this queue
                }
                
                processedJobs++;
            }
        }
        
        if (processedJobs > 0) {
            logger.info("Processed {} jobs from {} priority queue", processedJobs, priorityName);
        }
    }
    
    /**
     * Assign job with load balancing algorithm
     */
    private Worker assignJobWithLoadBalancing(Job job, List<Worker> availableWorkers) {
        LoadBalancingAlgorithm algorithm = LoadBalancingAlgorithm.valueOf(loadBalancingAlgorithm);
        
        switch (algorithm) {
            case ROUND_ROBIN:
                return assignUsingRoundRobin(job, availableWorkers);
            case LEAST_CONNECTIONS:
                return assignUsingLeastConnections(job, availableWorkers);
            case WEIGHTED_ROUND_ROBIN:
                return assignUsingWeightedRoundRobin(job, availableWorkers);
            case LEAST_RESPONSE_TIME:
                return assignUsingLeastResponseTime(job, availableWorkers);
            case RESOURCE_BASED:
                return assignUsingResourceBased(job, availableWorkers);
            case INTELLIGENT:
                return assignUsingIntelligent(job, availableWorkers);
            case ADAPTIVE:
                return assignUsingAdaptive(job, availableWorkers);
            default:
                return assignUsingIntelligent(job, availableWorkers);
        }
    }
    
    /**
     * Round-robin load balancing
     */
    private Worker assignUsingRoundRobin(Job job, List<Worker> workers) {
        if (workers.isEmpty()) return null;
        
        // Simple round-robin based on job ID
        int index = (int) (job.getId() % workers.size());
        Worker selectedWorker = workers.get(index);
        
        return jobAssignmentService.assignJob(job) != null ? selectedWorker : null;
    }
    
    /**
     * Least connections load balancing
     */
    private Worker assignUsingLeastConnections(Job job, List<Worker> workers) {
        return workers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .min(Comparator.comparing(Worker::getCurrentJobCount))
                .map(worker -> jobAssignmentService.assignJob(job) != null ? worker : null)
                .orElse(null);
    }
    
    /**
     * Weighted round-robin based on worker capacity
     */
    private Worker assignUsingWeightedRoundRobin(Job job, List<Worker> workers) {
        // Calculate weights based on available capacity
        Map<Worker, Double> weights = workers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .collect(Collectors.toMap(
                        worker -> worker,
                        worker -> (double) worker.getAvailableCapacity() / worker.getMaxConcurrentJobs()
                ));
        
        if (weights.isEmpty()) return null;
        
        // Select worker based on weighted probability
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double random = Math.random() * totalWeight;
        
        double cumulativeWeight = 0.0;
        for (Map.Entry<Worker, Double> entry : weights.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (random <= cumulativeWeight) {
                Worker selectedWorker = entry.getKey();
                return jobAssignmentService.assignJob(job) != null ? selectedWorker : null;
            }
        }
        
        return null;
    }
    
    /**
     * Least response time load balancing
     */
    private Worker assignUsingLeastResponseTime(Job job, List<Worker> workers) {
        return workers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .min(Comparator.comparing(Worker::getAverageExecutionTime))
                .map(worker -> jobAssignmentService.assignJob(job) != null ? worker : null)
                .orElse(null);
    }
    
    /**
     * Resource-based load balancing
     */
    private Worker assignUsingResourceBased(Job job, List<Worker> workers) {
        return workers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .max(Comparator.comparing(worker -> calculateResourceScore(worker)))
                .map(worker -> jobAssignmentService.assignJob(job) != null ? worker : null)
                .orElse(null);
    }
    
    /**
     * Intelligent load balancing (combines multiple factors)
     */
    private Worker assignUsingIntelligent(Job job, List<Worker> workers) {
        return workers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .max(Comparator.comparing(worker -> calculateIntelligentScore(worker, job)))
                .map(worker -> jobAssignmentService.assignJob(job) != null ? worker : null)
                .orElse(null);
    }
    
    /**
     * Adaptive load balancing (changes algorithm based on system state)
     */
    private Worker assignUsingAdaptive(Job job, List<Worker> workers) {
        // Choose algorithm based on current system state
        double systemLoad = calculateSystemLoad();
        
        if (systemLoad < 50.0) {
            // Low load: use performance-based assignment
            return assignUsingLeastResponseTime(job, workers);
        } else if (systemLoad < 80.0) {
            // Medium load: use intelligent assignment
            return assignUsingIntelligent(job, workers);
        } else {
            // High load: use least connections
            return assignUsingLeastConnections(job, workers);
        }
    }
    
    /**
     * Calculate resource score for a worker
     */
    private double calculateResourceScore(Worker worker) {
        double capacityScore = (double) worker.getAvailableCapacity() / worker.getMaxConcurrentJobs();
        double loadScore = 1.0 - (worker.getLoadPercentage() / 100.0);
        double performanceScore = worker.getSuccessRate() / 100.0;
        
        return (capacityScore * 0.4) + (loadScore * 0.3) + (performanceScore * 0.3);
    }
    
    /**
     * Calculate intelligent score for load balancing
     */
    private double calculateIntelligentScore(Worker worker, Job job) {
        WorkerPerformanceService.WorkerPerformanceMetrics performance = 
                workerPerformanceService.getWorkerPerformanceMetrics(worker.getWorkerId());
        
        double capacityScore = (double) worker.getAvailableCapacity() / worker.getMaxConcurrentJobs();
        double loadScore = 1.0 - (worker.getLoadPercentage() / 100.0);
        double performanceScore = performance.getOverallPerformanceScore() / 100.0;
        double responseTimeScore = calculateResponseTimeScore(worker.getAverageExecutionTime());
        
        // Priority bonus for high-priority jobs
        double priorityBonus = 1.0;
        if (job.getPriority() != null && job.getPriority() >= 500) {
            priorityBonus = worker.getSuccessRate() >= 95.0 ? 1.3 : 1.1;
        }
        
        return ((capacityScore * 0.25) + (loadScore * 0.25) + (performanceScore * 0.25) + (responseTimeScore * 0.25)) * priorityBonus;
    }
    
    /**
     * Calculate response time score (lower is better)
     */
    private double calculateResponseTimeScore(double avgResponseTime) {
        if (avgResponseTime <= 1000) return 1.0; // 1 second or less
        if (avgResponseTime <= 5000) return 0.8; // 5 seconds or less
        if (avgResponseTime <= 10000) return 0.6; // 10 seconds or less
        if (avgResponseTime <= 30000) return 0.4; // 30 seconds or less
        return 0.2; // More than 30 seconds
    }
    
    /**
     * Update worker load information
     */
    private void updateWorkerLoadInfo() {
        List<Worker> allWorkers = workerService.getAllWorkers();
        
        for (Worker worker : allWorkers) {
            WorkerLoadInfo loadInfo = workerLoadInfoMap.computeIfAbsent(
                    worker.getWorkerId(), WorkerLoadInfo::new);
            loadInfo.updateLoad(worker);
        }
        
        // Remove load info for inactive workers
        Set<String> activeWorkerIds = allWorkers.stream()
                .map(Worker::getWorkerId)
                .collect(Collectors.toSet());
        
        workerLoadInfoMap.entrySet().removeIf(entry -> 
                !activeWorkerIds.contains(entry.getKey()));
    }
    
    /**
     * Update worker load after job assignment
     */
    private void updateWorkerLoadAfterAssignment(String workerId) {
        WorkerLoadInfo loadInfo = workerLoadInfoMap.get(workerId);
        if (loadInfo != null) {
            loadInfo.setQueuedJobs(loadInfo.getQueuedJobs() + 1);
        }
    }
    
    /**
     * Get available workers for specific priority
     */
    private List<Worker> getAvailableWorkersForPriority(String priority) {
        List<Worker> availableWorkers = workerService.getAvailableWorkers();
        
        // Filter based on priority requirements
        if ("HIGH".equals(priority)) {
            return availableWorkers.stream()
                    .filter(worker -> worker.getSuccessRate() >= 90.0)
                    .filter(worker -> worker.getLoadPercentage() < criticalLoadThreshold)
                    .collect(Collectors.toList());
        } else if ("NORMAL".equals(priority)) {
            return availableWorkers.stream()
                    .filter(worker -> worker.getLoadPercentage() < highLoadThreshold)
                    .collect(Collectors.toList());
        } else {
            return availableWorkers.stream()
                    .filter(worker -> worker.getLoadPercentage() < criticalLoadThreshold)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Check if there are available workers
     */
    private boolean hasAvailableWorkers() {
        return workerService.getAvailableWorkers().stream()
                .anyMatch(worker -> worker.getLoadPercentage() < criticalLoadThreshold);
    }
    
    /**
     * Select queue based on job priority
     */
    private Queue<Job> selectQueueByPriority(Job job) {
        if (job.getPriority() == null) {
            return normalPriorityQueue;
        }
        
        if (job.getPriority() >= 500) {
            return highPriorityQueue;
        } else if (job.getPriority() >= 100) {
            return normalPriorityQueue;
        } else {
            return lowPriorityQueue;
        }
    }
    
    /**
     * Check if queue is full
     */
    private boolean isQueueFull(Queue<Job> queue, Integer priority) {
        if (priority == null) priority = 100;
        
        if (priority >= 500) {
            return queue.size() >= highPriorityQueueSize;
        } else if (priority >= 100) {
            return queue.size() >= normalPriorityQueueSize;
        } else {
            return queue.size() >= lowPriorityQueueSize;
        }
    }
    
    /**
     * Get priority name from priority value
     */
    private String getPriorityName(Integer priority) {
        if (priority == null) return "NORMAL";
        if (priority >= 500) return "HIGH";
        if (priority >= 100) return "NORMAL";
        return "LOW";
    }
    
    /**
     * Calculate overall system load
     */
    private double calculateSystemLoad() {
        List<Worker> allWorkers = workerService.getAllWorkers();
        if (allWorkers.isEmpty()) {
            return 0.0;
        }
        
        return allWorkers.stream()
                .mapToDouble(Worker::getLoadPercentage)
                .average()
                .orElse(0.0);
    }
    
    /**
     * Update queue metrics
     */
    private void updateQueueMetrics() {
        try {
            Map<String, Object> queueMetrics = new HashMap<>();
            queueMetrics.put("highPriorityQueueSize", highPriorityQueue.size());
            queueMetrics.put("normalPriorityQueueSize", normalPriorityQueue.size());
            queueMetrics.put("lowPriorityQueueSize", lowPriorityQueue.size());
            queueMetrics.put("totalQueuedJobs", getTotalQueuedJobs());
            queueMetrics.put("lastUpdated", LocalDateTime.now());
            
            cacheService.put("load:balancing:queue:metrics", queueMetrics, 300); // 5 minutes TTL
        } catch (Exception e) {
            logger.debug("Error updating queue metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Get total queued jobs
     */
    public int getTotalQueuedJobs() {
        return highPriorityQueue.size() + normalPriorityQueue.size() + lowPriorityQueue.size();
    }
    
    /**
     * Get queue status
     */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("highPriorityQueue", highPriorityQueue.size());
        status.put("normalPriorityQueue", normalPriorityQueue.size());
        status.put("lowPriorityQueue", lowPriorityQueue.size());
        status.put("totalQueued", getTotalQueuedJobs());
        status.put("systemLoad", calculateSystemLoad());
        status.put("availableWorkers", workerService.getAvailableWorkers().size());
        status.put("loadBalancingEnabled", loadBalancingEnabled);
        status.put("currentAlgorithm", loadBalancingAlgorithm);
        
        return status;
    }
    
    /**
     * Get load balancing metrics
     */
    public LoadBalancingMetrics getLoadBalancingMetrics() {
        return loadMetrics.getOrDefault("system", new LoadBalancingMetrics());
    }
    
    /**
     * Get worker load information
     */
    public Map<String, WorkerLoadInfo> getWorkerLoadInfo() {
        return new HashMap<>(workerLoadInfoMap);
    }
    
    /**
     * Rebalance worker loads
     */
    @Scheduled(fixedRateString = "${load.balancing.rebalance.interval-ms:60000}")
    public void rebalanceWorkerLoads() {
        if (!loadBalancingEnabled) {
            return;
        }
        
        try {
            logger.debug("Starting load rebalancing process");
            
            List<Worker> overloadedWorkers = getOverloadedWorkers();
            List<Worker> underloadedWorkers = getUnderloadedWorkers();
            
            if (!overloadedWorkers.isEmpty() && !underloadedWorkers.isEmpty()) {
                performLoadRebalancing(overloadedWorkers, underloadedWorkers);
            }
            
        } catch (Exception e) {
            logger.error("Error during load rebalancing: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get overloaded workers
     */
    private List<Worker> getOverloadedWorkers() {
        return workerService.getAllWorkers().stream()
                .filter(worker -> worker.getLoadPercentage() > highLoadThreshold)
                .filter(worker -> worker.getCurrentJobCount() > 0)
                .sorted(Comparator.comparing(Worker::getLoadPercentage).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get underloaded workers
     */
    private List<Worker> getUnderloadedWorkers() {
        return workerService.getAllWorkers().stream()
                .filter(worker -> worker.getLoadPercentage() < (highLoadThreshold - 20))
                .filter(Worker::isAvailable)
                .filter(Worker::canAcceptMoreJobs)
                .sorted(Comparator.comparing(Worker::getLoadPercentage))
                .collect(Collectors.toList());
    }
    
    /**
     * Perform load rebalancing between workers
     */
    private void performLoadRebalancing(List<Worker> overloadedWorkers, List<Worker> underloadedWorkers) {
        logger.info("Performing load rebalancing: {} overloaded workers, {} underloaded workers", 
                   overloadedWorkers.size(), underloadedWorkers.size());
        
        for (Worker overloadedWorker : overloadedWorkers) {
            if (underloadedWorkers.isEmpty()) {
                break;
            }
            
            // Find jobs that can be migrated
            List<Job> migratableJobs = findMigratableJobs(overloadedWorker);
            
            for (Job job : migratableJobs) {
                Worker targetWorker = findBestTargetWorker(underloadedWorkers, job);
                
                if (targetWorker != null) {
                    boolean migrated = migrateJob(job, overloadedWorker, targetWorker);
                    if (migrated) {
                        logger.info("Migrated job {} from worker {} to worker {}", 
                                   job.getId(), overloadedWorker.getWorkerId(), targetWorker.getWorkerId());
                        
                        // Remove target worker if it's now at capacity
                        if (!targetWorker.canAcceptMoreJobs()) {
                            underloadedWorkers.remove(targetWorker);
                        }
                        
                        // Check if source worker is no longer overloaded
                        if (overloadedWorker.getLoadPercentage() <= highLoadThreshold) {
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Find jobs that can be migrated from an overloaded worker
     */
    private List<Job> findMigratableJobs(Worker worker) {
        return jobService.getJobsByWorker(worker.getWorkerId()).stream()
                .filter(job -> job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.QUEUED)
                .filter(job -> job.getPriority() == null || job.getPriority() < 500) // Don't migrate high-priority jobs
                .limit(5) // Limit migration to avoid excessive movement
                .collect(Collectors.toList());
    }
    
    /**
     * Find best target worker for job migration
     */
    private Worker findBestTargetWorker(List<Worker> underloadedWorkers, Job job) {
        return underloadedWorkers.stream()
                .filter(Worker::canAcceptMoreJobs)
                .min(Comparator.comparing(Worker::getCurrentJobCount))
                .orElse(null);
    }
    
    /**
     * Migrate job from one worker to another
     */
    private boolean migrateJob(Job job, Worker sourceWorker, Worker targetWorker) {
        try {
            // Unassign from source worker
            job.unassignFromWorker();
            sourceWorker.unassignJob(job.getId());
            
            // Assign to target worker
            job.assignToWorker(targetWorker.getWorkerId(), targetWorker.getName(), 
                              targetWorker.getHostAddress(), targetWorker.getPort());
            targetWorker.assignJob(job.getId());
            
            // Update both workers and job
            workerService.updateWorker(sourceWorker);
            workerService.updateWorker(targetWorker);
            jobService.updateJob(job);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error migrating job {} from worker {} to worker {}: {}", 
                        job.getId(), sourceWorker.getWorkerId(), targetWorker.getWorkerId(), e.getMessage(), e);
            return false;
        }
    }
}
