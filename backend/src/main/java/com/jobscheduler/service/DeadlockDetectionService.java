package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobDependency;
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
import java.util.stream.Stream;

@Service
@Transactional
public class DeadlockDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeadlockDetectionService.class);
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private JobDependencyRepository dependencyRepository;
    
    @Autowired
    private DependencyGraphService graphService;
    
    // Cache for cycle detection results to avoid repeated computations
    private final Map<String, CycleDetectionResult> cycleCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 60000; // 1 minute
    
    /**
     * Comprehensive deadlock detection result
     */
    public static class DeadlockDetectionResult {
        private final boolean hasDeadlock;
        private final List<DeadlockCycle> cycles;
        private final List<String> warnings;
        private final Map<String, Object> statistics;
        private final LocalDateTime detectionTime;
        
        public DeadlockDetectionResult(boolean hasDeadlock, List<DeadlockCycle> cycles, 
                                     List<String> warnings, Map<String, Object> statistics) {
            this.hasDeadlock = hasDeadlock;
            this.cycles = cycles != null ? cycles : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.statistics = statistics != null ? statistics : new HashMap<>();
            this.detectionTime = LocalDateTime.now();
        }
        
        // Getters
        public boolean hasDeadlock() { return hasDeadlock; }
        public List<DeadlockCycle> getCycles() { return cycles; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Object> getStatistics() { return statistics; }
        public LocalDateTime getDetectionTime() { return detectionTime; }
        public int getCycleCount() { return cycles.size(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * Represents a detected deadlock cycle
     */
    public static class DeadlockCycle {
        private final List<Long> jobIds;
        private final List<String> jobNames;
        private final int severity;
        private final String description;
        private final List<Long> dependencyIds;
        
        public DeadlockCycle(List<Long> jobIds, List<String> jobNames, int severity, 
                           String description, List<Long> dependencyIds) {
            this.jobIds = jobIds != null ? new ArrayList<>(jobIds) : new ArrayList<>();
            this.jobNames = jobNames != null ? new ArrayList<>(jobNames) : new ArrayList<>();
            this.severity = severity;
            this.description = description;
            this.dependencyIds = dependencyIds != null ? new ArrayList<>(dependencyIds) : new ArrayList<>();
        }
        
        // Getters
        public List<Long> getJobIds() { return new ArrayList<>(jobIds); }
        public List<String> getJobNames() { return new ArrayList<>(jobNames); }
        public int getSeverity() { return severity; }
        public String getDescription() { return description; }
        public List<Long> getDependencyIds() { return new ArrayList<>(dependencyIds); }
        public int getCycleLength() { return jobIds.size(); }
        public boolean isHighSeverity() { return severity >= 8; }
        
        @Override
        public String toString() {
            return String.format("DeadlockCycle{jobs=%s, severity=%d, description='%s'}", 
                               jobIds, severity, description);
        }
    }
    
    /**
     * Internal cycle detection result for caching
     */
    private static class CycleDetectionResult {
        private final boolean hasCycle;
        private final List<Long> cyclePath;
        private final long timestamp;
        
        public CycleDetectionResult(boolean hasCycle, List<Long> cyclePath) {
            this.hasCycle = hasCycle;
            this.cyclePath = cyclePath != null ? new ArrayList<>(cyclePath) : new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS;
        }
        
        public boolean hasCycle() { return hasCycle; }
        public List<Long> getCyclePath() { return new ArrayList<>(cyclePath); }
    }
    
    /**
     * Comprehensive deadlock detection using multiple algorithms
     * @return DeadlockDetectionResult with detailed information
     */
    public DeadlockDetectionResult detectDeadlocks() {
        logger.info("Starting comprehensive deadlock detection...");
        
        long startTime = System.currentTimeMillis();
        List<DeadlockCycle> cycles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // Get all dependencies
            List<JobDependency> dependencies = dependencyRepository.findAll();
            if (dependencies.isEmpty()) {
                logger.debug("No dependencies found, no deadlocks possible");
                statistics.put("totalDependencies", 0);
                statistics.put("detectionTimeMs", System.currentTimeMillis() - startTime);
                return new DeadlockDetectionResult(false, cycles, warnings, statistics);
            }
            
            // Build adjacency list for cycle detection
            Map<Long, Set<Long>> adjacencyList = buildAdjacencyList(dependencies);
            statistics.put("totalJobs", adjacencyList.size());
            statistics.put("totalDependencies", dependencies.size());
            
            // 1. DFS-based cycle detection (primary algorithm)
            List<DeadlockCycle> dfsCycles = detectCyclesUsingDFS(adjacencyList, dependencies);
            cycles.addAll(dfsCycles);
            
            // 2. Tarjan's algorithm for strongly connected components
            List<DeadlockCycle> tarjanCycles = detectCyclesUsingTarjan(adjacencyList, dependencies);
            cycles.addAll(tarjanCycles);
            
            // 3. Database-level cycle detection (PostgreSQL specific)
            try {
                List<Object[]> dbCycleResults = dependencyRepository.findCircularDependencies();
                if (!dbCycleResults.isEmpty()) {
                    // Extract unique job IDs from the result pairs
                    List<Long> dbCycles = dbCycleResults.stream()
                        .flatMap(result -> Stream.of((Long) result[0], (Long) result[1]))
                        .distinct()
                        .collect(Collectors.toList());
                    
                    DeadlockCycle dbCycle = createCycleFromJobIds(dbCycles, dependencies, 
                                                                "Database-detected cycle", 9);
                    if (!containsCycle(cycles, dbCycle)) {
                        cycles.add(dbCycle);
                    }
                }
            } catch (Exception e) {
                warnings.add("Database cycle detection failed: " + e.getMessage());
                logger.warn("Database cycle detection failed", e);
            }
            
            // 4. Validate using topological sort
            boolean topologicalValid = validateUsingTopologicalSort(adjacencyList);
            if (!topologicalValid && cycles.isEmpty()) {
                warnings.add("Topological sort failed but no specific cycles detected");
            }
            
            // Remove duplicate cycles
            cycles = removeDuplicateCycles(cycles);
            
            // Calculate additional statistics
            statistics.put("cyclesFound", cycles.size());
            statistics.put("highSeverityCycles", cycles.stream().mapToInt(c -> c.isHighSeverity() ? 1 : 0).sum());
            statistics.put("averageCycleLength", cycles.stream().mapToInt(DeadlockCycle::getCycleLength).average().orElse(0.0));
            statistics.put("detectionTimeMs", System.currentTimeMillis() - startTime);
            statistics.put("warningCount", warnings.size());
            
            boolean hasDeadlock = !cycles.isEmpty();
            
            if (hasDeadlock) {
                logger.warn("Deadlock detected! Found {} cycles with {} warnings", 
                           cycles.size(), warnings.size());
                for (DeadlockCycle cycle : cycles) {
                    logger.warn("Deadlock cycle: {}", cycle);
                }
            } else {
                logger.info("No deadlocks detected. Graph is acyclic.");
            }
            
            return new DeadlockDetectionResult(hasDeadlock, cycles, warnings, statistics);
            
        } catch (Exception e) {
            logger.error("Error during deadlock detection: {}", e.getMessage(), e);
            warnings.add("Deadlock detection error: " + e.getMessage());
            statistics.put("detectionTimeMs", System.currentTimeMillis() - startTime);
            statistics.put("error", e.getMessage());
            
            return new DeadlockDetectionResult(false, cycles, warnings, statistics);
        }
    }
    
    /**
     * Check if adding a dependency would create a cycle
     * @param childJobId Job that would depend on parent
     * @param parentJobId Job that child would depend on
     * @return Validation result with detailed information
     */
    public DependencyValidationResult validateDependencyAddition(Long childJobId, Long parentJobId) {
        logger.debug("Validating dependency addition: {} -> {}", parentJobId, childJobId);
        
        // Check for self-dependency
        if (childJobId.equals(parentJobId)) {
            return new DependencyValidationResult(false, "Self-dependency not allowed", 
                                                Arrays.asList(childJobId), 10);
        }
        
        // Check if jobs exist
        if (!jobRepository.existsById(childJobId) || !jobRepository.existsById(parentJobId)) {
            return new DependencyValidationResult(false, "One or both jobs do not exist", 
                                                Arrays.asList(childJobId, parentJobId), 9);
        }
        
        // Check if dependency already exists
        if (dependencyRepository.existsByJobIdAndDependencyJobId(childJobId, parentJobId)) {
            return new DependencyValidationResult(true, "Dependency already exists", 
                                                Arrays.asList(childJobId, parentJobId), 1);
        }
        
        try {
            // Create temporary adjacency list with the new dependency
            List<JobDependency> existingDeps = dependencyRepository.findAll();
            Map<Long, Set<Long>> adjacencyList = buildAdjacencyList(existingDeps);
            
            // Add the proposed dependency
            adjacencyList.computeIfAbsent(parentJobId, k -> new HashSet<>()).add(childJobId);
            
            // Check for cycles using DFS
            List<Long> cyclePath = detectCycleFromNode(adjacencyList, parentJobId, childJobId);
            
            if (!cyclePath.isEmpty()) {
                String description = String.format("Adding dependency %d -> %d would create cycle: %s", 
                                                 parentJobId, childJobId, cyclePath);
                return new DependencyValidationResult(false, description, cyclePath, 8);
            }
            
            // Additional validation: check for potential deadlocks
            List<String> warnings = new ArrayList<>();
            
            // Check dependency depth
            int maxDepth = calculateMaxDependencyDepth(adjacencyList, childJobId);
            if (maxDepth > 10) {
                warnings.add("Deep dependency chain detected (depth: " + maxDepth + ")");
            }
            
            // Check for potential bottlenecks
            int dependentCount = adjacencyList.getOrDefault(parentJobId, new HashSet<>()).size();
            if (dependentCount > 20) {
                warnings.add("High fan-out detected for parent job (dependents: " + dependentCount + ")");
            }
            
            return new DependencyValidationResult(true, "Dependency is valid", 
                                                new ArrayList<>(), 0, warnings);
            
        } catch (Exception e) {
            logger.error("Error validating dependency: {}", e.getMessage(), e);
            return new DependencyValidationResult(false, "Validation error: " + e.getMessage(), 
                                                Arrays.asList(childJobId, parentJobId), 7);
        }
    }
    
    /**
     * Dependency validation result
     */
    public static class DependencyValidationResult {
        private final boolean isValid;
        private final String message;
        private final List<Long> affectedJobIds;
        private final int severity;
        private final List<String> warnings;
        
        public DependencyValidationResult(boolean isValid, String message, 
                                        List<Long> affectedJobIds, int severity) {
            this(isValid, message, affectedJobIds, severity, new ArrayList<>());
        }
        
        public DependencyValidationResult(boolean isValid, String message, 
                                        List<Long> affectedJobIds, int severity, List<String> warnings) {
            this.isValid = isValid;
            this.message = message;
            this.affectedJobIds = affectedJobIds != null ? new ArrayList<>(affectedJobIds) : new ArrayList<>();
            this.severity = severity;
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        // Getters
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
        public List<Long> getAffectedJobIds() { return new ArrayList<>(affectedJobIds); }
        public int getSeverity() { return severity; }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean isHighSeverity() { return severity >= 8; }
    }
    
    /**
     * DFS-based cycle detection
     */
    private List<DeadlockCycle> detectCyclesUsingDFS(Map<Long, Set<Long>> adjacencyList, 
                                                    List<JobDependency> dependencies) {
        List<DeadlockCycle> cycles = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();
        Map<Long, Long> parent = new HashMap<>();
        
        for (Long jobId : adjacencyList.keySet()) {
            if (!visited.contains(jobId)) {
                List<Long> cyclePath = dfsCycleDetection(adjacencyList, jobId, visited, 
                                                       recursionStack, parent, new ArrayList<>());
                if (!cyclePath.isEmpty()) {
                    DeadlockCycle cycle = createCycleFromJobIds(cyclePath, dependencies, 
                                                              "DFS-detected cycle", 8);
                    cycles.add(cycle);
                }
            }
        }
        
        return cycles;
    }
    
    /**
     * DFS cycle detection helper
     */
    private List<Long> dfsCycleDetection(Map<Long, Set<Long>> adjacencyList, Long currentNode,
                                       Set<Long> visited, Set<Long> recursionStack, 
                                       Map<Long, Long> parent, List<Long> path) {
        visited.add(currentNode);
        recursionStack.add(currentNode);
        path.add(currentNode);
        
        Set<Long> neighbors = adjacencyList.getOrDefault(currentNode, new HashSet<>());
        
        for (Long neighbor : neighbors) {
            if (recursionStack.contains(neighbor)) {
                // Cycle found - extract the cycle path
                List<Long> cyclePath = new ArrayList<>();
                int cycleStart = path.indexOf(neighbor);
                for (int i = cycleStart; i < path.size(); i++) {
                    cyclePath.add(path.get(i));
                }
                cyclePath.add(neighbor); // Close the cycle
                return cyclePath;
            }
            
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, currentNode);
                List<Long> cyclePath = dfsCycleDetection(adjacencyList, neighbor, visited, 
                                                       recursionStack, parent, new ArrayList<>(path));
                if (!cyclePath.isEmpty()) {
                    return cyclePath;
                }
            }
        }
        
        recursionStack.remove(currentNode);
        return new ArrayList<>();
    }
    
    /**
     * Tarjan's algorithm for strongly connected components
     */
    private List<DeadlockCycle> detectCyclesUsingTarjan(Map<Long, Set<Long>> adjacencyList, 
                                                       List<JobDependency> dependencies) {
        List<DeadlockCycle> cycles = new ArrayList<>();
        
        TarjanSCC tarjan = new TarjanSCC(adjacencyList);
        List<List<Long>> sccs = tarjan.findSCCs();
        
        for (List<Long> scc : sccs) {
            if (scc.size() > 1) { // SCC with more than one node indicates a cycle
                DeadlockCycle cycle = createCycleFromJobIds(scc, dependencies, 
                                                          "Tarjan SCC-detected cycle", 7);
                cycles.add(cycle);
            }
        }
        
        return cycles;
    }
    
    /**
     * Tarjan's Strongly Connected Components algorithm implementation
     */
    private static class TarjanSCC {
        private final Map<Long, Set<Long>> adjacencyList;
        private final Map<Long, Integer> indices = new HashMap<>();
        private final Map<Long, Integer> lowLinks = new HashMap<>();
        private final Set<Long> onStack = new HashSet<>();
        private final Stack<Long> stack = new Stack<>();
        private final List<List<Long>> sccs = new ArrayList<>();
        private int index = 0;
        
        public TarjanSCC(Map<Long, Set<Long>> adjacencyList) {
            this.adjacencyList = adjacencyList;
        }
        
        public List<List<Long>> findSCCs() {
            for (Long node : adjacencyList.keySet()) {
                if (!indices.containsKey(node)) {
                    strongConnect(node);
                }
            }
            return sccs;
        }
        
        private void strongConnect(Long node) {
            indices.put(node, index);
            lowLinks.put(node, index);
            index++;
            stack.push(node);
            onStack.add(node);
            
            Set<Long> neighbors = adjacencyList.getOrDefault(node, new HashSet<>());
            for (Long neighbor : neighbors) {
                if (!indices.containsKey(neighbor)) {
                    strongConnect(neighbor);
                    lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(neighbor)));
                } else if (onStack.contains(neighbor)) {
                    lowLinks.put(node, Math.min(lowLinks.get(node), indices.get(neighbor)));
                }
            }
            
            if (lowLinks.get(node).equals(indices.get(node))) {
                List<Long> scc = new ArrayList<>();
                Long current;
                do {
                    current = stack.pop();
                    onStack.remove(current);
                    scc.add(current);
                } while (!current.equals(node));
                
                sccs.add(scc);
            }
        }
    }
    
    /**
     * Detect cycle from a specific node to target
     */
    private List<Long> detectCycleFromNode(Map<Long, Set<Long>> adjacencyList, 
                                         Long startNode, Long targetNode) {
        String cacheKey = startNode + "->" + targetNode;
        
        // Check cache
        CycleDetectionResult cached = cycleCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.getCyclePath();
        }
        
        Set<Long> visited = new HashSet<>();
        List<Long> path = new ArrayList<>();
        List<Long> cyclePath = dfsPathSearch(adjacencyList, startNode, targetNode, 
                                           visited, path, new HashSet<>());
        
        // Cache result
        cycleCache.put(cacheKey, new CycleDetectionResult(!cyclePath.isEmpty(), cyclePath));
        
        return cyclePath;
    }
    
    /**
     * DFS path search for cycle detection
     */
    private List<Long> dfsPathSearch(Map<Long, Set<Long>> adjacencyList, Long current, 
                                   Long target, Set<Long> visited, List<Long> path, 
                                   Set<Long> recursionStack) {
        if (recursionStack.contains(current)) {
            return new ArrayList<>(); // Infinite loop prevention
        }
        
        visited.add(current);
        recursionStack.add(current);
        path.add(current);
        
        if (current.equals(target)) {
            return new ArrayList<>(path);
        }
        
        Set<Long> neighbors = adjacencyList.getOrDefault(current, new HashSet<>());
        for (Long neighbor : neighbors) {
            if (!visited.contains(neighbor) || neighbor.equals(target)) {
                List<Long> result = dfsPathSearch(adjacencyList, neighbor, target, 
                                                visited, new ArrayList<>(path), recursionStack);
                if (!result.isEmpty()) {
                    recursionStack.remove(current);
                    return result;
                }
            }
        }
        
        recursionStack.remove(current);
        return new ArrayList<>();
    }
    
    /**
     * Validate graph using topological sort
     */
    private boolean validateUsingTopologicalSort(Map<Long, Set<Long>> adjacencyList) {
        try {
            Map<Long, Integer> inDegree = calculateInDegrees(adjacencyList);
            Queue<Long> queue = new LinkedList<>();
            
            // Add nodes with in-degree 0
            for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }
            
            int processedNodes = 0;
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                processedNodes++;
                
                Set<Long> neighbors = adjacencyList.getOrDefault(current, new HashSet<>());
                for (Long neighbor : neighbors) {
                    int newInDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newInDegree);
                    
                    if (newInDegree == 0) {
                        queue.offer(neighbor);
                    }
                }
            }
            
            return processedNodes == adjacencyList.size();
            
        } catch (Exception e) {
            logger.error("Error in topological sort validation: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Helper methods
     */
    
    private Map<Long, Set<Long>> buildAdjacencyList(List<JobDependency> dependencies) {
        Map<Long, Set<Long>> adjacencyList = new HashMap<>();
        
        for (JobDependency dep : dependencies) {
            Long parentJob = dep.getDependencyJobId();
            Long childJob = dep.getJobId();
            
            adjacencyList.computeIfAbsent(parentJob, k -> new HashSet<>()).add(childJob);
            adjacencyList.computeIfAbsent(childJob, k -> new HashSet<>());
        }
        
        return adjacencyList;
    }
    
    private Map<Long, Integer> calculateInDegrees(Map<Long, Set<Long>> adjacencyList) {
        Map<Long, Integer> inDegree = new HashMap<>();
        
        // Initialize all nodes with in-degree 0
        for (Long node : adjacencyList.keySet()) {
            inDegree.put(node, 0);
        }
        
        // Count incoming edges
        for (Set<Long> neighbors : adjacencyList.values()) {
            for (Long neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
            }
        }
        
        return inDegree;
    }
    
    private DeadlockCycle createCycleFromJobIds(List<Long> jobIds, List<JobDependency> dependencies, 
                                              String description, int severity) {
        List<String> jobNames = new ArrayList<>();
        List<Long> dependencyIds = new ArrayList<>();
        
        for (Long jobId : jobIds) {
            try {
                Optional<Job> job = jobRepository.findById(jobId);
                jobNames.add(job.map(Job::getName).orElse("Job-" + jobId));
            } catch (Exception e) {
                jobNames.add("Job-" + jobId);
            }
        }
        
        // Find relevant dependency IDs
        for (JobDependency dep : dependencies) {
            if (jobIds.contains(dep.getJobId()) && jobIds.contains(dep.getDependencyJobId())) {
                dependencyIds.add(dep.getId());
            }
        }
        
        return new DeadlockCycle(jobIds, jobNames, severity, description, dependencyIds);
    }
    
    private boolean containsCycle(List<DeadlockCycle> cycles, DeadlockCycle newCycle) {
        Set<Long> newCycleJobs = new HashSet<>(newCycle.getJobIds());
        
        for (DeadlockCycle existingCycle : cycles) {
            Set<Long> existingJobs = new HashSet<>(existingCycle.getJobIds());
            if (existingJobs.equals(newCycleJobs)) {
                return true;
            }
        }
        
        return false;
    }
    
    private List<DeadlockCycle> removeDuplicateCycles(List<DeadlockCycle> cycles) {
        List<DeadlockCycle> uniqueCycles = new ArrayList<>();
        Set<Set<Long>> seenCycles = new HashSet<>();
        
        for (DeadlockCycle cycle : cycles) {
            Set<Long> cycleJobs = new HashSet<>(cycle.getJobIds());
            if (!seenCycles.contains(cycleJobs)) {
                seenCycles.add(cycleJobs);
                uniqueCycles.add(cycle);
            }
        }
        
        return uniqueCycles;
    }
    
    private int calculateMaxDependencyDepth(Map<Long, Set<Long>> adjacencyList, Long startNode) {
        Set<Long> visited = new HashSet<>();
        return dfsDepthCalculation(adjacencyList, startNode, visited, 0);
    }
    
    private int dfsDepthCalculation(Map<Long, Set<Long>> adjacencyList, Long node, 
                                  Set<Long> visited, int currentDepth) {
        if (visited.contains(node)) {
            return currentDepth;
        }
        
        visited.add(node);
        int maxDepth = currentDepth;
        
        Set<Long> neighbors = adjacencyList.getOrDefault(node, new HashSet<>());
        for (Long neighbor : neighbors) {
            int depth = dfsDepthCalculation(adjacencyList, neighbor, new HashSet<>(visited), currentDepth + 1);
            maxDepth = Math.max(maxDepth, depth);
        }
        
        return maxDepth;
    }
    
    /**
     * Clear cache (useful for testing and maintenance)
     */
    public void clearCache() {
        cycleCache.clear();
        lastCacheUpdate = System.currentTimeMillis();
        logger.debug("Deadlock detection cache cleared");
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", cycleCache.size());
        stats.put("lastUpdate", lastCacheUpdate);
        stats.put("validityMs", CACHE_VALIDITY_MS);
        
        long validEntries = cycleCache.values().stream()
            .mapToLong(result -> result.isValid() ? 1 : 0)
            .sum();
        stats.put("validEntries", validEntries);
        stats.put("expiredEntries", cycleCache.size() - validEntries);
        
        return stats;
    }
}
