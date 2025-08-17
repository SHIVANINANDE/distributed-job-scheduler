package com.jobscheduler.repository;

import com.jobscheduler.model.JobDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobDependencyRepository extends JpaRepository<JobDependency, Long> {
    
    /**
     * Find all dependencies for a specific job
     */
    List<JobDependency> findByJobId(Long jobId);
    
    /**
     * Find all jobs that depend on a specific job
     */
    List<JobDependency> findByDependencyJobId(Long dependencyJobId);
    
    /**
     * Find unsatisfied dependencies for a specific job
     */
    @Query("SELECT jd FROM JobDependency jd WHERE jd.jobId = :jobId AND jd.isSatisfied = false")
    List<JobDependency> findUnsatisfiedDependenciesByJobId(@Param("jobId") Long jobId);
    
    /**
     * Check if a job has all dependencies satisfied
     */
    @Query("SELECT COUNT(jd) = 0 FROM JobDependency jd WHERE jd.jobId = :jobId AND jd.isSatisfied = false")
    boolean areAllDependenciesSatisfied(@Param("jobId") Long jobId);
    
    /**
     * Find dependencies by job and dependency job ID
     */
    JobDependency findByJobIdAndDependencyJobId(Long jobId, Long dependencyJobId);
    
    /**
     * Delete all dependencies for a job
     */
    void deleteByJobId(Long jobId);
    
    /**
     * Delete all dependencies that depend on a specific job
     */
    void deleteByDependencyJobId(Long dependencyJobId);
    
    /**
     * Count unsatisfied dependencies for a job
     */
    @Query("SELECT COUNT(jd) FROM JobDependency jd WHERE jd.jobId = :jobId AND jd.isSatisfied = false")
    long countUnsatisfiedDependencies(@Param("jobId") Long jobId);
    
    /**
     * Find all jobs that can be executed (no unsatisfied dependencies)
     */
    @Query("SELECT DISTINCT jd.jobId FROM JobDependency jd " +
           "WHERE jd.jobId NOT IN (" +
           "  SELECT jd2.jobId FROM JobDependency jd2 WHERE jd2.isSatisfied = false" +
           ")")
    List<Long> findJobsWithSatisfiedDependencies();
    
    /**
     * Get dependency chain for a job (recursive dependencies)
     */
    @Query(value = "WITH RECURSIVE dependency_chain AS (" +
                   "  SELECT job_id, dependency_job_id, 1 as level " +
                   "  FROM job_dependencies WHERE job_id = :jobId " +
                   "  UNION ALL " +
                   "  SELECT jd.job_id, jd.dependency_job_id, dc.level + 1 " +
                   "  FROM job_dependencies jd " +
                   "  JOIN dependency_chain dc ON jd.job_id = dc.dependency_job_id " +
                   "  WHERE dc.level < 10" + // Prevent infinite recursion
                   ") SELECT * FROM dependency_chain",
           nativeQuery = true)
    List<Object[]> findDependencyChain(@Param("jobId") Long jobId);
}
