package com.jobscheduler.repository;

import com.jobscheduler.model.JobDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    Optional<JobDependency> findByJobIdAndDependencyJobId(Long jobId, Long dependencyJobId);
    
    /**
     * Check if a dependency exists between two jobs
     */
    boolean existsByJobIdAndDependencyJobId(Long jobId, Long dependencyJobId);
    
    /**
     * Find dependencies by satisfaction status for a specific parent job
     */
    List<JobDependency> findByDependencyJobIdAndIsSatisfied(Long dependencyJobId, Boolean isSatisfied);
    
    /**
     * Delete all dependencies for a job
     */
    @Modifying
    @Transactional
    void deleteByJobId(Long jobId);
    
    /**
     * Delete all dependencies that depend on a specific job
     */
    @Modifying
    @Transactional
    void deleteByDependencyJobId(Long dependencyJobId);
    
    /**
     * Delete specific dependency between two jobs
     */
    @Modifying
    @Transactional
    void deleteByJobIdAndDependencyJobId(Long jobId, Long dependencyJobId);
    
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
    
    /**
     * Find circular dependencies in the dependency graph
     */
    @Query(value = "WITH RECURSIVE dependency_path AS (" +
                   "  SELECT job_id, dependency_job_id, ARRAY[job_id] as path, 1 as level " +
                   "  FROM job_dependencies " +
                   "  UNION ALL " +
                   "  SELECT jd.job_id, jd.dependency_job_id, " +
                   "         path || jd.job_id, dp.level + 1 " +
                   "  FROM job_dependencies jd " +
                   "  JOIN dependency_path dp ON jd.job_id = dp.dependency_job_id " +
                   "  WHERE jd.job_id <> ALL(path) AND dp.level < 20" +
                   ") " +
                   "SELECT DISTINCT path[1] as job_id, dependency_job_id " +
                   "FROM dependency_path " +
                   "WHERE dependency_job_id = ANY(path)",
           nativeQuery = true)
    List<Object[]> findCircularDependencies();
}
