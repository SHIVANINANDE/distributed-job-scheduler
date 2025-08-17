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
}
