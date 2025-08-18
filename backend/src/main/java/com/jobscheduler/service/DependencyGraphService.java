package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobDependency;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.JobDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class DependencyGraphService {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphService.class);
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private JobDependencyRepository dependencyRepository;
    
    @Autowired
    private DependencyGraphService dependencyGraphService;
    
    @Autowired
    private DeadlockDetectionService deadlockDetectionService;    @Autowired
    private JobService jobService;
    
    // In-memory cache for adjacency list representation
    private final Map<Long, Set<Long>> adjacencyList = new ConcurrentHashMap<>();
    private final Map<Long, Integer> inDegreeMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> reverseAdjacencyList = new ConcurrentHashMap<>();
    
    /**
     * Build the dependency graph from database
     */
    public void buildDependencyGraph() {
        logger.info("Building dependency graph from database...");
        
        synchronized (this) {
            // Clear existing graph
            adjacencyList.clear();
            inDegreeMap.clear();
            reverseAdjacencyList.clear();
            
            // Get all jobs
            List<Job> allJobs = jobRepository.findAll();
            
            // Initialize all nodes with zero in-degree
            for (Job job : allJobs) {
                adjacencyList.put(job.getId(), new HashSet<>());
                reverseAdjacencyList.put(job.getId(), new HashSet<>());
                inDegreeMap.put(job.getId(), 0);
            }
            
            // Get all dependencies and build adjacency list
            List<JobDependency> dependencies = dependencyRepository.findAll();
            
            for (JobDependency dependency : dependencies) {
                Long jobId = dependency.getJobId(); // Child job (depends on parent)
                Long dependentJobId = dependency.getDependencyJobId(); // Parent job (must complete first)
                
                // Add edge from dependent job to job (parent -> child)
                adjacencyList.computeIfAbsent(dependentJobId, k -> new HashSet<>()).add(jobId);
                reverseAdjacencyList.computeIfAbsent(jobId, k -> new HashSet<>()).add(dependentJobId);
                
                // Increment in-degree of child job
                inDegreeMap.put(jobId, inDegreeMap.getOrDefault(jobId, 0) + 1);
            }
            
            logger.info("Dependency graph built with {} nodes and {} edges", 
                       allJobs.size(), dependencies.size());
        }
    }
    
    /**
     * Add a dependency between two jobs with advanced deadlock detection
     * @param childJobId The job that depends on another (child)
     * @param parentJobId The job that this job depends on (parent)
     * @return true if dependency was added successfully
     */
    public boolean addDependency(Long childJobId, Long parentJobId) {
        logger.info("Adding dependency with deadlock validation: job {} depends on job {}", childJobId, parentJobId);
        
        try {
            // Advanced validation using deadlock detection service
            var validationResult = deadlockDetectionService.validateDependencyAddition(childJobId, parentJobId);
            
            if (!validationResult.isValid()) {
                logger.warn("Cannot add dependency: {}", validationResult.getMessage());
                return false;
            }
            
            // Log warnings if any
            if (validationResult.hasWarnings()) {
                for (String warning : validationResult.getWarnings()) {
                    logger.warn("Dependency warning: {}", warning);
                }
            }
            
            // Check if jobs exist
            if (!jobRepository.existsById(childJobId) || !jobRepository.existsById(parentJobId)) {
                logger.warn("Cannot add dependency: one or both jobs do not exist");
                return false;
            }
            
            // Check if dependency already exists
            if (dependencyRepository.existsByJobIdAndDependencyJobId(childJobId, parentJobId)) {
                logger.info("Dependency already exists between {} and {}", childJobId, parentJobId);
                return true;
            }
            
            // Create dependency in database
            JobDependency dependency = new JobDependency();
            dependency.setJobId(childJobId);
            dependency.setDependencyJobId(parentJobId);
            dependency.setCreatedAt(LocalDateTime.now());
            dependency.setIsSatisfied(false);
            dependencyRepository.save(dependency);
            
            // Update in-memory graph
            synchronized (this) {
                adjacencyList.computeIfAbsent(parentJobId, k -> new HashSet<>()).add(childJobId);
                reverseAdjacencyList.computeIfAbsent(childJobId, k -> new HashSet<>()).add(parentJobId);
                inDegreeMap.put(childJobId, inDegreeMap.getOrDefault(childJobId, 0) + 1);
            }
            
            // Perform post-addition validation
            var postValidation = deadlockDetectionService.detectDeadlocks();
            if (postValidation.hasDeadlock()) {
                logger.error("Deadlock detected after adding dependency! Rolling back...");
                // Rollback the dependency
                removeDependency(childJobId, parentJobId);
                return false;
            }
            
            logger.info("Successfully added dependency: {} -> {}", parentJobId, childJobId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error adding dependency: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove a dependency between two jobs
     * @param childJobId The child job ID
     * @param parentJobId The parent job ID
     * @return true if dependency was removed successfully
     */
    public boolean removeDependency(Long childJobId, Long parentJobId) {
        logger.info("Removing dependency: job {} depends on job {}", childJobId, parentJobId);
        
        try {
            // Remove from database
            dependencyRepository.deleteByJobIdAndDependencyJobId(childJobId, parentJobId);
            
            // Update in-memory graph
            synchronized (this) {
                Set<Long> dependents = adjacencyList.get(parentJobId);
                if (dependents != null) {
                    dependents.remove(childJobId);
                }
                
                Set<Long> dependencies = reverseAdjacencyList.get(childJobId);
                if (dependencies != null) {
                    dependencies.remove(parentJobId);
                }
                
                // Decrease in-degree
                inDegreeMap.put(childJobId, Math.max(0, inDegreeMap.getOrDefault(childJobId, 0) - 1));
            }
            
            logger.info("Successfully removed dependency: {} -> {}", parentJobId, childJobId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error removing dependency: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get jobs that are ready to execute (no unsatisfied dependencies)
     * Using Kahn's Algorithm approach
     * @return List of jobs ready for execution
     */
    public List<Job> getJobsReadyForExecution() {
        logger.debug("Finding jobs ready for execution using topological sort...");
        
        List<Job> readyJobs = new ArrayList<>();
        
        synchronized (this) {
            // Find all jobs with in-degree 0 and status PENDING
            for (Map.Entry<Long, Integer> entry : inDegreeMap.entrySet()) {
                Long jobId = entry.getKey();
                Integer inDegree = entry.getValue();
                
                if (inDegree == 0) {
                    Job job = jobService.getJobById(jobId);
                    if (job != null && job.getStatus() == JobStatus.PENDING) {
                        readyJobs.add(job);
                    }
                }
            }
        }
        
        logger.debug("Found {} jobs ready for execution", readyJobs.size());
        return readyJobs;
    }
    
    /**
     * Perform topological sort using Kahn's Algorithm
     * @return List of job IDs in topological order, or empty list if cycle detected
     */
    public List<Long> topologicalSort() {
        logger.info("Performing topological sort using Kahn's Algorithm...");
        
        List<Long> result = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        Map<Long, Integer> tempInDegree = new HashMap<>();
        
        synchronized (this) {
            // Copy in-degree map
            tempInDegree.putAll(inDegreeMap);
            
            // Add all nodes with in-degree 0 to queue
            for (Map.Entry<Long, Integer> entry : tempInDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }
        }
        
        // Process queue
        while (!queue.isEmpty()) {
            Long currentJob = queue.poll();
            result.add(currentJob);
            
            // Get all jobs that depend on current job
            Set<Long> dependentJobs = adjacencyList.getOrDefault(currentJob, new HashSet<>());
            
            for (Long dependentJob : dependentJobs) {
                // Decrease in-degree
                int newInDegree = tempInDegree.get(dependentJob) - 1;
                tempInDegree.put(dependentJob, newInDegree);
                
                // If in-degree becomes 0, add to queue
                if (newInDegree == 0) {
                    queue.offer(dependentJob);
                }
            }
        }
        
        // Check for cycles
        if (result.size() != inDegreeMap.size()) {
            logger.warn("Cycle detected in dependency graph! Cannot perform complete topological sort");
            return new ArrayList<>(); // Return empty list to indicate cycle
        }
        
        logger.info("Topological sort completed successfully with {} jobs", result.size());
        return result;
    }
    
    /**
     * Update job completion and check for newly available jobs
     * @param completedJobId The ID of the completed job
     * @return List of jobs that became ready after this completion
     */
    public List<Job> updateJobCompletion(Long completedJobId) {
        logger.info("Updating dependency graph after job {} completion", completedJobId);
        
        List<Job> newlyReadyJobs = new ArrayList<>();
        
        try {
            // Mark dependencies as satisfied in database
            List<JobDependency> dependenciesToUpdate = dependencyRepository
                .findByDependencyJobIdAndIsSatisfied(completedJobId, false);
            
            for (JobDependency dependency : dependenciesToUpdate) {
                dependency.setIsSatisfied(true);
                dependency.setSatisfiedAt(LocalDateTime.now());
                dependencyRepository.save(dependency);
            }
            
            // Update in-memory graph - decrease in-degree for dependent jobs
            synchronized (this) {
                Set<Long> dependentJobs = adjacencyList.getOrDefault(completedJobId, new HashSet<>());
                
                for (Long dependentJobId : dependentJobs) {
                    int currentInDegree = inDegreeMap.getOrDefault(dependentJobId, 0);
                    int newInDegree = Math.max(0, currentInDegree - 1);
                    inDegreeMap.put(dependentJobId, newInDegree);
                    
                    // If in-degree becomes 0, job is ready
                    if (newInDegree == 0) {
                        Job readyJob = jobService.getJobById(dependentJobId);
                        if (readyJob != null && readyJob.getStatus() == JobStatus.PENDING) {
                            newlyReadyJobs.add(readyJob);
                        }
                    }
                }
            }
            
            logger.info("Job {} completion unlocked {} new jobs for execution", 
                       completedJobId, newlyReadyJobs.size());
            
        } catch (Exception e) {
            logger.error("Error updating job completion for {}: {}", completedJobId, e.getMessage());
        }
        
        return newlyReadyJobs;
    }
    
    /**
     * Check if adding a dependency would create a cycle
     * @param jobId The job that would depend on another
     * @param dependentJobId The job that this job would depend on
     * @return true if it would create a cycle
     */
    private boolean wouldCreateCycle(Long jobId, Long dependentJobId) {
        // If there's already a path from dependentJobId to jobId, 
        // adding jobId -> dependentJobId would create a cycle
        return hasPath(dependentJobId, jobId);
    }
    
    /**
     * Check if there's a path from source to target using DFS
     * @param source Source job ID
     * @param target Target job ID
     * @return true if path exists
     */
    private boolean hasPath(Long source, Long target) {
        if (source.equals(target)) {
            return true;
        }
        
        Set<Long> visited = new HashSet<>();
        Stack<Long> stack = new Stack<>();
        stack.push(source);
        
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            
            if (visited.contains(current)) {
                continue;
            }
            
            visited.add(current);
            
            if (current.equals(target)) {
                return true;
            }
            
            // Add all jobs that current job can reach
            Set<Long> neighbors = adjacencyList.getOrDefault(current, new HashSet<>());
            for (Long neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get all dependencies for a job
     * @param jobId The job ID
     * @return Set of job IDs that this job depends on
     */
    public Set<Long> getJobDependencies(Long jobId) {
        return reverseAdjacencyList.getOrDefault(jobId, new HashSet<>());
    }
    
    /**
     * Get all jobs that depend on a given job
     * @param jobId The job ID
     * @return Set of job IDs that depend on this job
     */
    public Set<Long> getJobDependents(Long jobId) {
        return adjacencyList.getOrDefault(jobId, new HashSet<>());
    }
    
    /**
     * Get dependency graph statistics
     * @return Map containing graph statistics
     */
    public Map<String, Object> getDependencyGraphStats() {
        Map<String, Object> stats = new HashMap<>();
        
        synchronized (this) {
            stats.put("totalNodes", inDegreeMap.size());
            stats.put("totalEdges", adjacencyList.values().stream()
                .mapToInt(Set::size).sum());
            
            // Calculate nodes with no dependencies (in-degree 0)
            long nodesWithNoDependencies = inDegreeMap.values().stream()
                .mapToLong(degree -> degree == 0 ? 1 : 0).sum();
            stats.put("nodesWithNoDependencies", nodesWithNoDependencies);
            
            // Calculate nodes with no dependents (out-degree 0)
            long nodesWithNoDependents = adjacencyList.values().stream()
                .mapToLong(set -> set.isEmpty() ? 1 : 0).sum();
            stats.put("nodesWithNoDependents", nodesWithNoDependents);
            
            // Calculate average in-degree
            double avgInDegree = inDegreeMap.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0.0);
            stats.put("averageInDegree", avgInDegree);
            
            // Check if graph is acyclic
            List<Long> topSort = topologicalSort();
            stats.put("isAcyclic", !topSort.isEmpty());
            
            stats.put("timestamp", LocalDateTime.now());
        }
        
        return stats;
    }
    
    /**
     * Validate dependency graph integrity
     * @return List of validation errors
     */
    public List<String> validateDependencyGraph() {
        List<String> errors = new ArrayList<>();
        
        try {
            // Check for cycles
            List<Long> topSort = topologicalSort();
            if (topSort.isEmpty() && !inDegreeMap.isEmpty()) {
                errors.add("Cycle detected in dependency graph");
            }
            
            // Check for orphaned dependencies
            List<JobDependency> allDependencies = dependencyRepository.findAll();
            for (JobDependency dep : allDependencies) {
                if (!jobRepository.existsById(dep.getJobId())) {
                    errors.add("Orphaned dependency: job " + dep.getJobId() + " does not exist");
                }
                if (!jobRepository.existsById(dep.getDependencyJobId())) {
                    errors.add("Orphaned dependency: dependent job " + dep.getDependencyJobId() + " does not exist");
                }
            }
            
            // Check for inconsistencies between database and in-memory graph
            synchronized (this) {
                for (JobDependency dep : allDependencies) {
                    Set<Long> dependents = adjacencyList.get(dep.getDependencyJobId());
                    if (dependents == null || !dependents.contains(dep.getJobId())) {
                        errors.add("Inconsistency: dependency " + dep.getDependencyJobId() + 
                                 " -> " + dep.getJobId() + " not in memory graph");
                    }
                }
            }
            
        } catch (Exception e) {
            errors.add("Error during validation: " + e.getMessage());
        }
        
        return errors;
    }
    
    /**
     * Get execution plan for all pending jobs
     * @return List of job execution batches in dependency order
     */
    public List<List<Job>> getExecutionPlan() {
        logger.info("Generating execution plan based on dependencies...");
        
        List<List<Job>> executionPlan = new ArrayList<>();
        Map<Long, Integer> tempInDegree = new HashMap<>();
        
        synchronized (this) {
            tempInDegree.putAll(inDegreeMap);
        }
        
        while (!tempInDegree.isEmpty()) {
            // Find all jobs with in-degree 0 in current iteration
            List<Job> currentBatch = new ArrayList<>();
            List<Long> toRemove = new ArrayList<>();
            
            for (Map.Entry<Long, Integer> entry : tempInDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    Job job = jobService.getJobById(entry.getKey());
                    if (job != null && job.getStatus() == JobStatus.PENDING) {
                        currentBatch.add(job);
                    }
                    toRemove.add(entry.getKey());
                }
            }
            
            if (currentBatch.isEmpty() && !tempInDegree.isEmpty()) {
                logger.warn("Cycle detected during execution plan generation");
                break;
            }
            
            if (!currentBatch.isEmpty()) {
                executionPlan.add(currentBatch);
            }
            
            // Remove processed nodes and update in-degrees
            for (Long jobId : toRemove) {
                tempInDegree.remove(jobId);
                
                Set<Long> dependents = adjacencyList.getOrDefault(jobId, new HashSet<>());
                for (Long dependent : dependents) {
                    if (tempInDegree.containsKey(dependent)) {
                        tempInDegree.put(dependent, tempInDegree.get(dependent) - 1);
                    }
                }
            }
        }
        
        logger.info("Generated execution plan with {} batches", executionPlan.size());
        return executionPlan;
    }
}
