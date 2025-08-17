package com.jobscheduler.repository;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    
    // Find jobs by status
    List<Job> findByStatus(JobStatus status);
    
    // Find jobs by status with pagination
    Page<Job> findByStatus(JobStatus status, Pageable pageable);
    
    // Find jobs created between dates
    List<Job> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Find jobs scheduled before a certain time
    List<Job> findByScheduledAtBeforeAndStatus(LocalDateTime dateTime, JobStatus status);
    
    // Count jobs by status
    long countByStatus(JobStatus status);
    
    // Find failed jobs that can be retried
    @Query("SELECT j FROM Job j WHERE j.status = 'FAILED' AND j.retryCount < j.maxRetries")
    List<Job> findRetryableJobs();
    
    // Find jobs by job type
    List<Job> findByJobType(String jobType);
    
    // Find jobs by name containing (case insensitive)
    List<Job> findByNameContainingIgnoreCase(String name);
    
    // Custom query to find long running jobs
    @Query("SELECT j FROM Job j WHERE j.status = 'RUNNING' AND j.startedAt < :threshold")
    List<Job> findLongRunningJobs(@Param("threshold") LocalDateTime threshold);
    
    // Find recent jobs (last 24 hours)
    @Query("SELECT j FROM Job j WHERE j.createdAt >= :since ORDER BY j.createdAt DESC")
    List<Job> findRecentJobs(@Param("since") LocalDateTime since);
    
    // New methods for enhanced job model
    
    // Find jobs by priority range
    List<Job> findByPriorityBetweenOrderByPriorityDesc(Integer minPriority, Integer maxPriority);
    
    // Find jobs by priority (ordered by priority descending)
    List<Job> findByStatusOrderByPriorityDesc(JobStatus status);
    
    // Find jobs assigned to a specific worker
    List<Job> findByAssignedWorkerId(String workerId);
    
    // Find jobs assigned to a specific worker with status
    List<Job> findByAssignedWorkerIdAndStatus(String workerId, JobStatus status);
    
    // Find unassigned jobs (no worker assigned)
    List<Job> findByAssignedWorkerIdIsNull();
    
    // Find unassigned jobs with specific status
    List<Job> findByAssignedWorkerIdIsNullAndStatus(JobStatus status);
    
    // Find jobs with dependencies
    @Query("SELECT j FROM Job j WHERE SIZE(j.dependencyJobIds) > 0")
    List<Job> findJobsWithDependencies();
    
    // Find jobs without dependencies
    @Query("SELECT j FROM Job j WHERE SIZE(j.dependencyJobIds) = 0")
    List<Job> findJobsWithoutDependencies();
    
    // Find jobs by estimated duration range
    List<Job> findByEstimatedDurationMinutesBetween(Long minDuration, Long maxDuration);
    
    // Find jobs that exceeded estimated duration
    @Query("SELECT j FROM Job j WHERE j.status = 'COMPLETED' AND j.actualDurationMinutes > j.estimatedDurationMinutes")
    List<Job> findJobsExceededEstimatedDuration();
    
    // Find jobs that completed faster than estimated
    @Query("SELECT j FROM Job j WHERE j.status = 'COMPLETED' AND j.actualDurationMinutes < j.estimatedDurationMinutes")
    List<Job> findJobsCompletedFasterThanEstimated();
    
    // Find high priority jobs (priority > threshold)
    @Query("SELECT j FROM Job j WHERE j.priority > :threshold ORDER BY j.priority DESC")
    List<Job> findHighPriorityJobs(@Param("threshold") Integer threshold);
    
    // Find jobs ready to execute (no unsatisfied dependencies)
    @Query("SELECT j FROM Job j WHERE j.status = 'PENDING' AND " +
           "(SIZE(j.dependencyJobIds) = 0 OR " +
           "NOT EXISTS (SELECT jd FROM JobDependency jd WHERE jd.jobId = j.id AND jd.isSatisfied = false))")
    List<Job> findJobsReadyToExecute();
    
    // Find jobs waiting for dependencies
    @Query("SELECT j FROM Job j WHERE j.status = 'PENDING' AND " +
           "EXISTS (SELECT jd FROM JobDependency jd WHERE jd.jobId = j.id AND jd.isSatisfied = false)")
    List<Job> findJobsWaitingForDependencies();
    
    // Count jobs by worker
    @Query("SELECT j.assignedWorkerId, COUNT(j) FROM Job j WHERE j.assignedWorkerId IS NOT NULL GROUP BY j.assignedWorkerId")
    List<Object[]> countJobsByWorker();
    
    // Get average execution time by job type
    @Query("SELECT j.jobType, AVG(j.actualDurationMinutes) FROM Job j WHERE j.status = 'COMPLETED' AND j.actualDurationMinutes IS NOT NULL GROUP BY j.jobType")
    List<Object[]> getAverageExecutionTimeByJobType();
    
    // Find jobs that need priority adjustment (old pending jobs)
    @Query("SELECT j FROM Job j WHERE j.status = 'PENDING' AND j.createdAt < :threshold ORDER BY j.createdAt ASC")
    List<Job> findJobsNeedingPriorityAdjustment(@Param("threshold") LocalDateTime threshold);
    
    // Find stale assigned jobs (assigned but not started)
    @Query("SELECT j FROM Job j WHERE j.assignedWorkerId IS NOT NULL AND j.status = 'PENDING' AND j.workerAssignedAt < :threshold")
    List<Job> findStaleAssignedJobs(@Param("threshold") LocalDateTime threshold);
    
    // Get job statistics by status and priority
    @Query("SELECT j.status, j.priority, COUNT(j) FROM Job j GROUP BY j.status, j.priority ORDER BY j.status, j.priority")
    List<Object[]> getJobStatisticsByStatusAndPriority();
    
    // Find jobs by worker host
    List<Job> findByWorkerHost(String workerHost);
    
    // Count jobs by status for a specific worker
    @Query("SELECT j.status, COUNT(j) FROM Job j WHERE j.assignedWorkerId = :workerId GROUP BY j.status")
    List<Object[]> countJobsByStatusForWorker(@Param("workerId") String workerId);
}
