package com.jobscheduler.repository;

import com.jobscheduler.model.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {
    
    /**
     * Find worker by worker ID
     */
    Optional<Worker> findByWorkerId(String workerId);
    
    /**
     * Find all active workers
     */
    List<Worker> findByStatus(Worker.WorkerStatus status);
    
    /**
     * Find available workers (active and not at max capacity)
     */
    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs")
    List<Worker> findAvailableWorkers();
    
    /**
     * Find workers with recent heartbeat
     */
    @Query("SELECT w FROM Worker w WHERE w.lastHeartbeat > :since")
    List<Worker> findWorkersWithRecentHeartbeat(@Param("since") LocalDateTime since);
    
    /**
     * Find workers by host
     */
    List<Worker> findByHostAddress(String hostAddress);
    
    /**
     * Find workers by name containing
     */
    List<Worker> findByNameContainingIgnoreCase(String name);
    
    /**
     * Find workers ordered by load percentage (least loaded first)
     */
    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs " +
           "ORDER BY (CAST(w.currentJobCount AS double) / w.maxConcurrentJobs) ASC")
    List<Worker> findAvailableWorkersOrderByLoad();
    
    /**
     * Find workers ordered by success rate (highest success rate first)
     */
    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs " +
           "ORDER BY (CASE WHEN w.totalJobsProcessed > 0 THEN CAST(w.totalJobsSuccessful AS double) / w.totalJobsProcessed ELSE 0 END) DESC")
    List<Worker> findAvailableWorkersOrderBySuccessRate();
    
    /**
     * Count active workers
     */
    long countByStatus(Worker.WorkerStatus status);
    
    /**
     * Count available workers
     */
    @Query("SELECT COUNT(w) FROM Worker w WHERE w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs")
    long countAvailableWorkers();
    
    /**
     * Find workers that haven't sent heartbeat recently (potentially dead)
     */
    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND " +
           "(w.lastHeartbeat IS NULL OR w.lastHeartbeat < :threshold)")
    List<Worker> findPotentiallyDeadWorkers(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Get total capacity across all active workers
     */
    @Query("SELECT COALESCE(SUM(w.maxConcurrentJobs), 0) FROM Worker w WHERE w.status = 'ACTIVE'")
    long getTotalActiveCapacity();
    
    /**
     * Get current job count across all active workers
     */
    @Query("SELECT COALESCE(SUM(w.currentJobCount), 0) FROM Worker w WHERE w.status = 'ACTIVE'")
    long getTotalCurrentJobs();
    
    /**
     * Find workers with specific capabilities (JSON search)
     */
    @Query("SELECT w FROM Worker w WHERE w.capabilities LIKE %:capability%")
    List<Worker> findByCapability(@Param("capability") String capability);
    
    /**
     * Find workers with specific tags (JSON search)
     */
    @Query("SELECT w FROM Worker w WHERE w.tags LIKE %:tag%")
    List<Worker> findByTag(@Param("tag") String tag);
    
    /**
     * Get worker statistics
     */
    @Query("SELECT " +
           "COUNT(w) as totalWorkers, " +
           "COUNT(CASE WHEN w.status = 'ACTIVE' THEN 1 END) as activeWorkers, " +
           "COUNT(CASE WHEN w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs THEN 1 END) as availableWorkers, " +
           "COALESCE(SUM(w.maxConcurrentJobs), 0) as totalCapacity, " +
           "COALESCE(SUM(w.currentJobCount), 0) as currentLoad, " +
           "COALESCE(SUM(w.totalJobsProcessed), 0) as totalJobsProcessed, " +
           "COALESCE(SUM(w.totalJobsSuccessful), 0) as totalJobsSuccessful " +
           "FROM Worker w")
    Object[] getWorkerStatistics();
    
    /**
     * Find workers assigned to specific jobs
     */
    @Query("SELECT w FROM Worker w WHERE w.currentJobIds IS NOT NULL AND (" +
           "w.currentJobIds LIKE CONCAT('%[', CAST(:jobId AS string), ']%') OR " +
           "w.currentJobIds LIKE CONCAT('%[', CAST(:jobId AS string), ',%') OR " +
           "w.currentJobIds LIKE CONCAT('%,', CAST(:jobId AS string), ']%') OR " +
           "w.currentJobIds LIKE CONCAT('%,', CAST(:jobId AS string), ',%'))")
    List<Worker> findWorkersAssignedToJob(@Param("jobId") Long jobId);
    
    /**
     * Find workers assigned to any of the specified jobs
     */
    @Query("SELECT DISTINCT w FROM Worker w WHERE w.currentJobIds IS NOT NULL")
    List<Worker> findWorkersAssignedToJobs(@Param("jobIds") List<Long> jobIds);
    
    /**
     * Find available workers with capacity
     */
    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND w.currentJobCount < w.maxConcurrentJobs")
    List<Worker> findByStatusAndMaxConcurrentJobsGreaterThanCurrentJobCount(Worker.WorkerStatus status);
    
    /**
     * Find workers with no recent heartbeat
     */
    List<Worker> findByLastHeartbeatBefore(LocalDateTime threshold);
}
