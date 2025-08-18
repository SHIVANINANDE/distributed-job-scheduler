package com.jobscheduler.service;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import com.jobscheduler.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WorkerService {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkerService.class);
    
    @Autowired
    private WorkerRepository workerRepository;
    
    @Autowired
    private RedisCacheService cacheService;
    
    // Create a new worker
    public Worker createWorker(Worker worker) {
        logger.info("Creating new worker: {}", worker.getWorkerId());
        
        // Set initial values
        if (worker.getStatus() == null) {
            worker.setStatus(WorkerStatus.INACTIVE);
        }
        if (worker.getCurrentJobCount() == null) {
            worker.setCurrentJobCount(0);
        }
        if (worker.getMaxConcurrentJobs() == null) {
            worker.setMaxConcurrentJobs(1);
        }
        
        worker.updateAvailableCapacity();
        Worker savedWorker = workerRepository.save(worker);
        
        // Cache the worker (non-blocking operation)
        try {
            cacheService.cacheWorker(savedWorker.getWorkerId(), (Object) savedWorker, 300); // Cache for 5 minutes
        } catch (Exception e) {
            logger.warn("Failed to cache worker {}: {}", savedWorker.getWorkerId(), e.getMessage());
        }
        
        logger.info("Created worker: {}", savedWorker.getWorkerId());
        return savedWorker;
    }
    
    // Get all workers with pagination
    public Page<Worker> getAllWorkers(Pageable pageable) {
        return workerRepository.findAll(pageable);
    }
    
    // Get worker by ID
    public Optional<Worker> getWorkerById(Long id) {
        return workerRepository.findById(id);
    }
    
    // Get worker by worker ID
    public Optional<Worker> getWorkerByWorkerId(String workerId) {
        // First try to get from cache
        Worker cachedWorker = cacheService.getCachedWorker(workerId);
        if (cachedWorker != null) {
            logger.debug("Retrieved worker {} from cache", workerId);
            return Optional.of(cachedWorker);
        }
        
        // If not in cache, get from database and cache it
        Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            cacheService.cacheWorker(workerId, (Object) worker, 300); // Cache for 5 minutes
            logger.debug("Retrieved worker {} from database and cached it", workerId);
        }
        
        return workerOpt;
    }
    
    // Update worker
    public Worker updateWorker(Worker worker) {
        logger.info("Updating worker: {}", worker.getWorkerId());
        
        worker.updateAvailableCapacity();
        Worker updatedWorker = workerRepository.save(worker);
        
        // Update cache
        cacheService.evictWorker(worker.getWorkerId());
        cacheService.cacheWorker(worker.getWorkerId(), (Object) updatedWorker, 300);
        
        return updatedWorker;
    }
    
    // Overloaded update worker method (for backward compatibility)
    public Worker updateWorker(Long workerId, Worker worker) {
        worker.setId(workerId);
        return updateWorker(worker);
    }
    
    // Delete worker
    public void deleteWorker(Long id) {
        logger.info("Deleting worker with ID: {}", id);
        
        Optional<Worker> workerOpt = workerRepository.findById(id);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            
            // Remove from cache
            cacheService.evictWorkerFromCache(worker.getWorkerId());
            
            // Delete from database
            workerRepository.deleteById(id);
            
            logger.info("Deleted worker: {}", worker.getWorkerId());
        }
    }
    
    // Delete worker by worker ID
    public void deleteWorkerByWorkerId(String workerId) {
        logger.info("Deleting worker: {}", workerId);
        
        Optional<Worker> workerOpt = workerRepository.findByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            
            // Remove from cache
            cacheService.evictWorkerFromCache(workerId);
            
            // Delete from database
            workerRepository.delete(worker);
            
            logger.info("Deleted worker: {}", workerId);
        }
    }
    
    // Register/Update worker heartbeat
    public Worker updateHeartbeat(String workerId) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            worker.updateHeartbeat();
            
            Worker updatedWorker = workerRepository.save(worker);
            
            // Update cache
            cacheService.cacheWorker(workerId, (Object) updatedWorker, 300);
            
            logger.debug("Updated heartbeat for worker: {}", workerId);
            return updatedWorker;
        } else {
            throw new RuntimeException("Worker not found: " + workerId);
        }
    }
    
    // Get available workers
    public List<Worker> getAvailableWorkers() {
        return workerRepository.findAvailableWorkers();
    }
    
    // Get available workers ordered by load
    public List<Worker> getAvailableWorkersOrderByLoad() {
        return workerRepository.findAvailableWorkersOrderByLoad();
    }
    
    // Get workers by status
    public List<Worker> getWorkersByStatus(WorkerStatus status) {
        return workerRepository.findByStatus(status);
    }
    
    // Get workers with recent heartbeat
    public List<Worker> getWorkersWithRecentHeartbeat(int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return workerRepository.findWorkersWithRecentHeartbeat(since);
    }
    
    // Get potentially dead workers
    public List<Worker> getPotentiallyDeadWorkers(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return workerRepository.findPotentiallyDeadWorkers(threshold);
    }
    
    // Assign job to worker
    public Worker assignJobToWorker(String workerId, Long jobId) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            
            if (!worker.canAcceptJob()) {
                throw new IllegalStateException("Worker " + workerId + " cannot accept more jobs");
            }
            
            worker.assignJob(jobId);
            Worker updatedWorker = workerRepository.save(worker);
            
            // Update cache
            cacheService.cacheWorker(workerId, (Object) updatedWorker, 300);
            
            logger.info("Assigned job {} to worker {}", jobId, workerId);
            return updatedWorker;
        } else {
            throw new RuntimeException("Worker not found: " + workerId);
        }
    }
    
    // Unassign job from worker
    public Worker unassignJobFromWorker(String workerId, Long jobId) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            worker.unassignJob(jobId);
            
            Worker updatedWorker = workerRepository.save(worker);
            
            // Update cache
            cacheService.cacheWorker(workerId, (Object) updatedWorker, 300);
            
            logger.info("Unassigned job {} from worker {}", jobId, workerId);
            return updatedWorker;
        } else {
            throw new RuntimeException("Worker not found: " + workerId);
        }
    }
    
    // Record job completion for worker
    public Worker recordJobCompletion(String workerId, Long jobId, boolean successful) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            worker.recordJobCompletion(successful);
            worker.unassignJob(jobId);
            
            Worker updatedWorker = workerRepository.save(worker);
            
            // Update cache
            cacheService.cacheWorker(workerId, (Object) updatedWorker, 300);
            
            logger.info("Recorded job completion for worker {}: job {} - success: {}", 
                       workerId, jobId, successful);
            return updatedWorker;
        } else {
            throw new RuntimeException("Worker not found: " + workerId);
        }
    }
    
    // Get worker statistics
    public WorkerStatistics getWorkerStatistics() {
        long totalWorkers = workerRepository.count();
        long activeWorkers = workerRepository.countAvailableWorkers();
        
        List<Worker> recentWorkers = getWorkersWithRecentHeartbeat(5); // 5 minutes
        long workersWithRecentHeartbeat = recentWorkers.size();
        
        List<Worker> deadWorkers = getPotentiallyDeadWorkers(10); // 10 minutes
        long potentiallyDeadWorkers = deadWorkers.size();
        
        Object[] stats = workerRepository.getWorkerStatistics();
        double averageLoad = stats != null && stats.length > 0 ? (Double) stats[0] : 0.0;
        long totalJobsProcessed = stats != null && stats.length > 1 ? (Long) stats[1] : 0L;
        
        return new WorkerStatistics(
            totalWorkers, 
            activeWorkers, 
            workersWithRecentHeartbeat, 
            potentiallyDeadWorkers,
            averageLoad,
            totalJobsProcessed
        );
    }
    
    // Find best worker for job
    public Optional<Worker> findBestWorkerForJob(int jobPriority) {
        List<Worker> availableWorkers = getAvailableWorkersOrderByLoad();
        
        return availableWorkers.stream()
            .filter(w -> w.canAcceptJob(jobPriority))
            .filter(w -> !w.isOverloaded())
            .findFirst();
    }
    
    // Update worker status
    public Worker updateWorkerStatus(String workerId, WorkerStatus status) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            worker.setStatus(status);
            
            Worker updatedWorker = workerRepository.save(worker);
            
            // Update cache
            cacheService.cacheWorker(workerId, (Object) updatedWorker, 300);
            
            logger.info("Updated worker {} status to {}", workerId, status);
            return updatedWorker;
        } else {
            throw new RuntimeException("Worker not found: " + workerId);
        }
    }
    
    // Get workers assigned to specific jobs
    public List<Worker> getWorkersAssignedToJobs(List<Long> jobIds) {
        return workerRepository.findWorkersAssignedToJobs(jobIds);
    }
    
    // Search workers by name
    public List<Worker> searchWorkersByName(String name) {
        return workerRepository.findByNameContainingIgnoreCase(name);
    }
    
    // Clean up dead workers
    public int cleanupDeadWorkers(int minutes) {
        List<Worker> deadWorkers = getPotentiallyDeadWorkers(minutes);
        
        int cleanedUp = 0;
        for (Worker worker : deadWorkers) {
            try {
                // Mark as inactive instead of deleting
                worker.setStatus(WorkerStatus.INACTIVE);
                workerRepository.save(worker);
                
                // Remove from cache
                cacheService.evictWorkerFromCache(worker.getWorkerId());
                
                cleanedUp++;
                logger.warn("Marked dead worker as inactive: {}", worker.getWorkerId());
            } catch (Exception e) {
                logger.error("Failed to cleanup dead worker: {}", worker.getWorkerId(), e);
            }
        }
        
        return cleanedUp;
    }
    
    // Inner class for worker statistics
    public static class WorkerStatistics {
        private final long totalWorkers;
        private final long activeWorkers;
        private final long workersWithRecentHeartbeat;
        private final long potentiallyDeadWorkers;
        private final double averageLoad;
        private final long totalJobsProcessed;
        
        public WorkerStatistics(long totalWorkers, long activeWorkers, 
                               long workersWithRecentHeartbeat, long potentiallyDeadWorkers,
                               double averageLoad, long totalJobsProcessed) {
            this.totalWorkers = totalWorkers;
            this.activeWorkers = activeWorkers;
            this.workersWithRecentHeartbeat = workersWithRecentHeartbeat;
            this.potentiallyDeadWorkers = potentiallyDeadWorkers;
            this.averageLoad = averageLoad;
            this.totalJobsProcessed = totalJobsProcessed;
        }
        
        // Getters
        public long getTotalWorkers() { return totalWorkers; }
        public long getActiveWorkers() { return activeWorkers; }
        public long getWorkersWithRecentHeartbeat() { return workersWithRecentHeartbeat; }
        public long getPotentiallyDeadWorkers() { return potentiallyDeadWorkers; }
        public double getAverageLoad() { return averageLoad; }
        public long getTotalJobsProcessed() { return totalJobsProcessed; }
    }
    
    // Get unhealthy workers (no recent heartbeat)
    public List<Worker> getUnhealthyWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5); // 5 minutes threshold
        return workerRepository.findByLastHeartbeatBefore(threshold);
    }
    
    // Additional methods for job assignment service
    public Worker getWorkerByWorkerIdDirect(String workerId) {
        Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
        return workerOpt.orElse(null);
    }
    
    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }
    
    public void markWorkerAsUnhealthy(String workerId, String reason) {
        try {
            Optional<Worker> workerOpt = getWorkerByWorkerId(workerId);
            if (workerOpt.isPresent()) {
                Worker worker = workerOpt.get();
                worker.setStatus(Worker.WorkerStatus.ERROR);
                updateWorker(worker);
                logger.warn("Marked worker {} as unhealthy: {}", workerId, reason);
            }
        } catch (Exception e) {
            logger.error("Error marking worker {} as unhealthy: {}", workerId, e.getMessage(), e);
        }
    }
}
