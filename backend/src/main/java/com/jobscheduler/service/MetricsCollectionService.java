package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Worker;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive metrics collection service for job scheduler monitoring
 */
@Service
public class MetricsCollectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionService.class);
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private WorkerRepository workerRepository;
    
    @Autowired
    private RedisCacheService cacheService;
    
    // Real-time metrics storage
    private final Map<String, AtomicLong> jobMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> workerMetrics = new ConcurrentHashMap<>();
    private final Map<String, Double> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastMetricsUpdate = new ConcurrentHashMap<>();
    
    // Metrics keys
    private static final String JOBS_SUBMITTED_TOTAL = "jobs.submitted.total";
    private static final String JOBS_COMPLETED_TOTAL = "jobs.completed.total";
    private static final String JOBS_FAILED_TOTAL = "jobs.failed.total";
    private static final String JOBS_CANCELLED_TOTAL = "jobs.cancelled.total";
    private static final String JOBS_RUNNING_CURRENT = "jobs.running.current";
    private static final String JOBS_PENDING_CURRENT = "jobs.pending.current";
    private static final String WORKERS_ACTIVE_CURRENT = "workers.active.current";
    private static final String WORKERS_TOTAL = "workers.total";
    private static final String QUEUE_DEPTH_CURRENT = "queue.depth.current";
    
    /**
     * Initialize metrics on startup
     */
    public void initializeMetrics() {
        logger.info("Initializing metrics collection service");
        
        // Initialize counters
        jobMetrics.put(JOBS_SUBMITTED_TOTAL, new AtomicLong(0));
        jobMetrics.put(JOBS_COMPLETED_TOTAL, new AtomicLong(0));
        jobMetrics.put(JOBS_FAILED_TOTAL, new AtomicLong(0));
        jobMetrics.put(JOBS_CANCELLED_TOTAL, new AtomicLong(0));
        jobMetrics.put(JOBS_RUNNING_CURRENT, new AtomicLong(0));
        jobMetrics.put(JOBS_PENDING_CURRENT, new AtomicLong(0));
        workerMetrics.put(WORKERS_ACTIVE_CURRENT, new AtomicLong(0));
        workerMetrics.put(WORKERS_TOTAL, new AtomicLong(0));
        jobMetrics.put(QUEUE_DEPTH_CURRENT, new AtomicLong(0));
        
        // Load initial values from database
        refreshMetricsFromDatabase();
        
        logger.info("Metrics collection service initialized");
    }
    
    /**
     * Record job submission
     */
    public void recordJobSubmission(Job job) {
        jobMetrics.get(JOBS_SUBMITTED_TOTAL).incrementAndGet();
        jobMetrics.get(JOBS_PENDING_CURRENT).incrementAndGet();
        
        logger.debug("Recorded job submission for job {}", job.getId());
    }
    
    /**
     * Record job completion
     */
    public void recordJobCompletion(Job job) {
        jobMetrics.get(JOBS_COMPLETED_TOTAL).incrementAndGet();
        jobMetrics.get(JOBS_RUNNING_CURRENT).decrementAndGet();
        
        // Calculate and record execution time
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            Duration executionTime = Duration.between(job.getStartedAt(), job.getCompletedAt());
            recordJobExecutionTime(job.getId().toString(), executionTime.toMillis());
        }
        
        logger.debug("Recorded job completion for job {}", job.getId());
    }
    
    /**
     * Record job failure
     */
    public void recordJobFailure(Job job, String errorMessage) {
        jobMetrics.get(JOBS_FAILED_TOTAL).incrementAndGet();
        jobMetrics.get(JOBS_RUNNING_CURRENT).decrementAndGet();
        
        // Record failure details for analysis
        Map<String, Object> failureDetails = new HashMap<>();
        failureDetails.put("jobId", job.getId());
        failureDetails.put("jobName", job.getName());
        failureDetails.put("errorMessage", errorMessage);
        failureDetails.put("timestamp", LocalDateTime.now());
        failureDetails.put("workerId", job.getAssignedWorkerId());
        
        cacheService.setCacheWithTTL("job.failure:" + job.getId(), failureDetails, 86400); // 24 hours
        
        logger.debug("Recorded job failure for job {}: {}", job.getId(), errorMessage);
    }
    
    /**
     * Record job cancellation
     */
    public void recordJobCancellation(Job job) {
        jobMetrics.get(JOBS_CANCELLED_TOTAL).incrementAndGet();
        
        // Decrement from appropriate current counter
        if (job.getStatus() == JobStatus.RUNNING) {
            jobMetrics.get(JOBS_RUNNING_CURRENT).decrementAndGet();
        } else if (job.getStatus() == JobStatus.PENDING) {
            jobMetrics.get(JOBS_PENDING_CURRENT).decrementAndGet();
        }
        
        logger.debug("Recorded job cancellation for job {}", job.getId());
    }
    
    /**
     * Record job start
     */
    public void recordJobStart(Job job) {
        jobMetrics.get(JOBS_RUNNING_CURRENT).incrementAndGet();
        jobMetrics.get(JOBS_PENDING_CURRENT).decrementAndGet();
        
        logger.debug("Recorded job start for job {}", job.getId());
    }
    
    /**
     * Record job execution time
     */
    public void recordJobExecutionTime(String jobId, long executionTimeMs) {
        // Update running average execution time
        String avgKey = "job.execution.time.avg";
        Double currentAvg = performanceMetrics.getOrDefault(avgKey, 0.0);
        long totalJobs = jobMetrics.get(JOBS_COMPLETED_TOTAL).get();
        
        if (totalJobs > 0) {
            double newAvg = ((currentAvg * (totalJobs - 1)) + executionTimeMs) / totalJobs;
            performanceMetrics.put(avgKey, newAvg);
        }
        
        // Store individual execution time
        cacheService.setCacheWithTTL("job.execution.time:" + jobId, executionTimeMs, 86400);
        
        logger.debug("Recorded execution time for job {}: {}ms", jobId, executionTimeMs);
    }
    
    /**
     * Record worker registration
     */
    public void recordWorkerRegistration(Worker worker) {
        workerMetrics.get(WORKERS_TOTAL).incrementAndGet();
        workerMetrics.get(WORKERS_ACTIVE_CURRENT).incrementAndGet();
        
        logger.debug("Recorded worker registration for worker {}", worker.getWorkerId());
    }
    
    /**
     * Record worker deregistration
     */
    public void recordWorkerDeregistration(Worker worker) {
        workerMetrics.get(WORKERS_ACTIVE_CURRENT).decrementAndGet();
        
        logger.debug("Recorded worker deregistration for worker {}", worker.getWorkerId());
    }
    
    /**
     * Record worker heartbeat
     */
    public void recordWorkerHeartbeat(Worker worker) {
        // Update worker utilization metrics
        Map<String, Object> heartbeatDetails = new HashMap<>();
        heartbeatDetails.put("workerId", worker.getWorkerId());
        heartbeatDetails.put("status", worker.getStatus());
        heartbeatDetails.put("currentJobs", worker.getCurrentJobCount());
        heartbeatDetails.put("maxCapacity", worker.getMaxConcurrentJobs());
        if (worker.getMaxConcurrentJobs() > 0) {
            heartbeatDetails.put("utilization", worker.getCurrentJobCount() * 100.0 / worker.getMaxConcurrentJobs());
        }
        heartbeatDetails.put("timestamp", LocalDateTime.now());
        
        cacheService.setCacheWithTTL("worker.heartbeat:" + worker.getWorkerId(), heartbeatDetails, 300); // 5 minutes
        
        logger.debug("Recorded worker heartbeat for worker {}", worker.getWorkerId());
    }
    
    /**
     * Update worker utilization metrics
     */
    public void updateWorkerUtilization(String workerId, double cpuUsage, double memoryUsage, int activeJobs) {
        Map<String, Object> utilizationData = new HashMap<>();
        utilizationData.put("cpuUsage", cpuUsage);
        utilizationData.put("memoryUsage", memoryUsage);
        utilizationData.put("activeJobs", activeJobs);
        utilizationData.put("timestamp", LocalDateTime.now());
        
        cacheService.setCacheWithTTL("worker.utilization:" + workerId, utilizationData, 300); // 5 minutes
        
        // Calculate overall utilization
        calculateOverallWorkerUtilization();
        
        logger.debug("Updated utilization metrics for worker {}: CPU={}%, Memory={}%, ActiveJobs={}", 
                    workerId, cpuUsage, memoryUsage, activeJobs);
    }
    
    /**
     * Update queue depth metrics
     */
    public void updateQueueDepth(long queueDepth) {
        jobMetrics.get(QUEUE_DEPTH_CURRENT).set(queueDepth);
        
        // Track queue depth history
        cacheService.setCacheWithTTL("queue.depth.history:" + System.currentTimeMillis(), queueDepth, 3600);
        
        logger.debug("Updated queue depth: {}", queueDepth);
    }
    
    /**
     * Get comprehensive job metrics
     */
    public JobMetricsSnapshot getJobMetrics() {
        return new JobMetricsSnapshot(
            jobMetrics.get(JOBS_SUBMITTED_TOTAL).get(),
            jobMetrics.get(JOBS_COMPLETED_TOTAL).get(),
            jobMetrics.get(JOBS_FAILED_TOTAL).get(),
            jobMetrics.get(JOBS_CANCELLED_TOTAL).get(),
            jobMetrics.get(JOBS_RUNNING_CURRENT).get(),
            jobMetrics.get(JOBS_PENDING_CURRENT).get(),
            jobMetrics.get(QUEUE_DEPTH_CURRENT).get(),
            calculateJobCompletionRate(),
            performanceMetrics.getOrDefault("job.execution.time.avg", 0.0),
            calculateJobSuccessRate()
        );
    }
    
    /**
     * Get worker utilization metrics
     */
    public WorkerUtilizationSnapshot getWorkerUtilization() {
        return new WorkerUtilizationSnapshot(
            workerMetrics.get(WORKERS_TOTAL).get(),
            workerMetrics.get(WORKERS_ACTIVE_CURRENT).get(),
            performanceMetrics.getOrDefault("worker.cpu.avg", 0.0),
            performanceMetrics.getOrDefault("worker.memory.avg", 0.0),
            calculateWorkerEfficiency()
        );
    }
    
    /**
     * Get queue processing metrics
     */
    public QueueMetricsSnapshot getQueueMetrics() {
        return new QueueMetricsSnapshot(
            jobMetrics.get(QUEUE_DEPTH_CURRENT).get(),
            calculateAverageProcessingTime(),
            calculateQueueThroughput(),
            calculateQueueWaitTime()
        );
    }
    
    /**
     * Scheduled metrics refresh from database
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void refreshMetricsFromDatabase() {
        try {
            logger.debug("Refreshing metrics from database");
            
            // Refresh job counts
            long totalJobs = jobRepository.count();
            long runningJobs = jobRepository.countByStatus(JobStatus.RUNNING);
            long pendingJobs = jobRepository.countByStatus(JobStatus.PENDING);
            long completedJobs = jobRepository.countByStatus(JobStatus.COMPLETED);
            long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);
            long cancelledJobs = jobRepository.countByStatus(JobStatus.CANCELLED);
            
            jobMetrics.get(JOBS_RUNNING_CURRENT).set(runningJobs);
            jobMetrics.get(JOBS_PENDING_CURRENT).set(pendingJobs);
            jobMetrics.get(JOBS_COMPLETED_TOTAL).set(completedJobs);
            jobMetrics.get(JOBS_FAILED_TOTAL).set(failedJobs);
            jobMetrics.get(JOBS_CANCELLED_TOTAL).set(cancelledJobs);
            jobMetrics.get(JOBS_SUBMITTED_TOTAL).set(totalJobs);
            
            // Refresh worker counts
            long totalWorkers = workerRepository.count();
            long activeWorkers = workerRepository.countByStatus("ACTIVE");
            
            workerMetrics.get(WORKERS_TOTAL).set(totalWorkers);
            workerMetrics.get(WORKERS_ACTIVE_CURRENT).set(activeWorkers);
            
            lastMetricsUpdate.put("database_refresh", LocalDateTime.now());
            
            logger.debug("Metrics refreshed from database");
            
        } catch (Exception e) {
            logger.error("Error refreshing metrics from database: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Calculate job completion rate (jobs/hour)
     */
    private double calculateJobCompletionRate() {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentCompletions = jobRepository.countByStatusAndCompletedAtAfter(JobStatus.COMPLETED, oneHourAgo);
            return recentCompletions; // Jobs completed in the last hour
        } catch (Exception e) {
            logger.error("Error calculating job completion rate: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate job success rate
     */
    private double calculateJobSuccessRate() {
        long totalCompleted = jobMetrics.get(JOBS_COMPLETED_TOTAL).get();
        long totalFailed = jobMetrics.get(JOBS_FAILED_TOTAL).get();
        long totalFinished = totalCompleted + totalFailed;
        
        if (totalFinished == 0) return 100.0;
        
        return (double) totalCompleted / totalFinished * 100.0;
    }
    
    /**
     * Calculate overall worker utilization
     */
    private void calculateOverallWorkerUtilization() {
        try {
            List<Worker> activeWorkers = workerRepository.findByStatus("ACTIVE");
            if (activeWorkers.isEmpty()) return;
            
            double totalCpu = 0.0;
            double totalMemory = 0.0;
            int validWorkers = 0;
            
            for (Worker worker : activeWorkers) {
                Object utilizationData = cacheService.getCache("worker.utilization:" + worker.getWorkerId());
                if (utilizationData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) utilizationData;
                    totalCpu += (Double) data.getOrDefault("cpuUsage", 0.0);
                    totalMemory += (Double) data.getOrDefault("memoryUsage", 0.0);
                    validWorkers++;
                }
            }
            
            if (validWorkers > 0) {
                performanceMetrics.put("worker.cpu.avg", totalCpu / validWorkers);
                performanceMetrics.put("worker.memory.avg", totalMemory / validWorkers);
            }
            
        } catch (Exception e) {
            logger.error("Error calculating overall worker utilization: {}", e.getMessage());
        }
    }
    
    /**
     * Calculate worker efficiency
     */
    private double calculateWorkerEfficiency() {
        long activeWorkers = workerMetrics.get(WORKERS_ACTIVE_CURRENT).get();
        long runningJobs = jobMetrics.get(JOBS_RUNNING_CURRENT).get();
        
        if (activeWorkers == 0) return 0.0;
        
        return (double) runningJobs / activeWorkers * 100.0;
    }
    
    /**
     * Calculate average processing time
     */
    private double calculateAverageProcessingTime() {
        return performanceMetrics.getOrDefault("job.execution.time.avg", 0.0);
    }
    
    /**
     * Calculate queue throughput (jobs/hour)
     */
    private double calculateQueueThroughput() {
        return calculateJobCompletionRate();
    }
    
    /**
     * Calculate average queue wait time
     */
    private double calculateQueueWaitTime() {
        try {
            // Calculate average time between submission and start
            List<Job> recentJobs = jobRepository.findByCreatedAtAfter(LocalDateTime.now().minusHours(1));
            
            double totalWaitTime = 0.0;
            int validJobs = 0;
            
            for (Job job : recentJobs) {
                if (job.getStartedAt() != null && job.getCreatedAt() != null) {
                    Duration waitTime = Duration.between(job.getCreatedAt(), job.getStartedAt());
                    totalWaitTime += waitTime.toMillis();
                    validJobs++;
                }
            }
            
            return validJobs > 0 ? totalWaitTime / validJobs : 0.0;
            
        } catch (Exception e) {
            logger.error("Error calculating queue wait time: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Get all metrics as a comprehensive report
     */
    public MetricsReport getComprehensiveMetrics() {
        return new MetricsReport(
            getJobMetrics(),
            getWorkerUtilization(),
            getQueueMetrics(),
            LocalDateTime.now()
        );
    }
    
    // Inner classes for metrics snapshots
    
    public static class JobMetricsSnapshot {
        private final long totalSubmitted;
        private final long totalCompleted;
        private final long totalFailed;
        private final long totalCancelled;
        private final long currentRunning;
        private final long currentPending;
        private final long currentQueueDepth;
        private final double completionRate;
        private final double averageExecutionTime;
        private final double successRate;
        
        public JobMetricsSnapshot(long totalSubmitted, long totalCompleted, long totalFailed,
                                 long totalCancelled, long currentRunning, long currentPending,
                                 long currentQueueDepth, double completionRate,
                                 double averageExecutionTime, double successRate) {
            this.totalSubmitted = totalSubmitted;
            this.totalCompleted = totalCompleted;
            this.totalFailed = totalFailed;
            this.totalCancelled = totalCancelled;
            this.currentRunning = currentRunning;
            this.currentPending = currentPending;
            this.currentQueueDepth = currentQueueDepth;
            this.completionRate = completionRate;
            this.averageExecutionTime = averageExecutionTime;
            this.successRate = successRate;
        }
        
        // Getters
        public long getTotalSubmitted() { return totalSubmitted; }
        public long getTotalCompleted() { return totalCompleted; }
        public long getTotalFailed() { return totalFailed; }
        public long getTotalCancelled() { return totalCancelled; }
        public long getCurrentRunning() { return currentRunning; }
        public long getCurrentPending() { return currentPending; }
        public long getCurrentQueueDepth() { return currentQueueDepth; }
        public double getCompletionRate() { return completionRate; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public double getSuccessRate() { return successRate; }
    }
    
    public static class WorkerUtilizationSnapshot {
        private final long totalWorkers;
        private final long activeWorkers;
        private final double averageCpuUsage;
        private final double averageMemoryUsage;
        private final double efficiency;
        
        public WorkerUtilizationSnapshot(long totalWorkers, long activeWorkers,
                                       double averageCpuUsage, double averageMemoryUsage,
                                       double efficiency) {
            this.totalWorkers = totalWorkers;
            this.activeWorkers = activeWorkers;
            this.averageCpuUsage = averageCpuUsage;
            this.averageMemoryUsage = averageMemoryUsage;
            this.efficiency = efficiency;
        }
        
        // Getters
        public long getTotalWorkers() { return totalWorkers; }
        public long getActiveWorkers() { return activeWorkers; }
        public double getAverageCpuUsage() { return averageCpuUsage; }
        public double getAverageMemoryUsage() { return averageMemoryUsage; }
        public double getEfficiency() { return efficiency; }
    }
    
    public static class QueueMetricsSnapshot {
        private final long currentDepth;
        private final double averageProcessingTime;
        private final double throughput;
        private final double averageWaitTime;
        
        public QueueMetricsSnapshot(long currentDepth, double averageProcessingTime,
                                  double throughput, double averageWaitTime) {
            this.currentDepth = currentDepth;
            this.averageProcessingTime = averageProcessingTime;
            this.throughput = throughput;
            this.averageWaitTime = averageWaitTime;
        }
        
        // Getters
        public long getCurrentDepth() { return currentDepth; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getThroughput() { return throughput; }
        public double getAverageWaitTime() { return averageWaitTime; }
    }
    
    public static class MetricsReport {
        private final JobMetricsSnapshot jobMetrics;
        private final WorkerUtilizationSnapshot workerMetrics;
        private final QueueMetricsSnapshot queueMetrics;
        private final LocalDateTime timestamp;
        
        public MetricsReport(JobMetricsSnapshot jobMetrics, WorkerUtilizationSnapshot workerMetrics,
                           QueueMetricsSnapshot queueMetrics, LocalDateTime timestamp) {
            this.jobMetrics = jobMetrics;
            this.workerMetrics = workerMetrics;
            this.queueMetrics = queueMetrics;
            this.timestamp = timestamp;
        }
        
        // Getters
        public JobMetricsSnapshot getJobMetrics() { return jobMetrics; }
        public WorkerUtilizationSnapshot getWorkerMetrics() { return workerMetrics; }
        public QueueMetricsSnapshot getQueueMetrics() { return queueMetrics; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
