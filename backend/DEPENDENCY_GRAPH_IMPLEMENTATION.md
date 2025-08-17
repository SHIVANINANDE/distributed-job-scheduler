# Step 6: Dependency Graph Management Implementation

## Overview

This implementation provides a comprehensive dependency graph management system for job scheduling using adjacency lists, topological sorting, and efficient database queries. The system ensures that jobs are executed only when their dependencies are satisfied, preventing race conditions and maintaining execution order integrity.

## Architecture Components

### 1. Graph Representation

#### Adjacency List Structure
- **Forward Adjacency List**: Maps parent jobs to their dependent children
- **Reverse Adjacency List**: Maps child jobs to their dependencies (parents)
- **In-Degree Tracking**: Maintains count of unsatisfied dependencies for each job

```java
// In-memory graph representation
private final Map<Long, Set<Long>> adjacencyList = new ConcurrentHashMap<>();
private final Map<Long, Integer> inDegreeMap = new ConcurrentHashMap<>();
private final Map<Long, Set<Long>> reverseAdjacencyList = new ConcurrentHashMap<>();
```

#### Database Storage
- **job_dependencies table**: Persistent storage with PostgreSQL optimization
- **Indexes**: Efficient queries on job_id, dependency_job_id, satisfied status
- **Constraints**: Unique dependency pairs, foreign key relationships

### 2. Topological Sort Implementation

#### Kahn's Algorithm
The system uses Kahn's Algorithm for dependency resolution:

1. **Initialize**: Find all jobs with in-degree 0 (no dependencies)
2. **Process**: Remove jobs from queue, decrease in-degree of dependent jobs
3. **Repeat**: Add newly available jobs (in-degree 0) to queue
4. **Validate**: Ensure all jobs processed (no cycles)

```java
public List<Long> topologicalSort() {
    List<Long> result = new ArrayList<>();
    Queue<Long> queue = new LinkedList<>();
    
    // Add all nodes with in-degree 0
    for (Map.Entry<Long, Integer> entry : tempInDegree.entrySet()) {
        if (entry.getValue() == 0) {
            queue.offer(entry.getKey());
        }
    }
    
    // Process queue using Kahn's Algorithm
    while (!queue.isEmpty()) {
        Long currentJob = queue.poll();
        result.add(currentJob);
        
        // Update in-degrees of dependent jobs
        Set<Long> dependentJobs = adjacencyList.getOrDefault(currentJob, new HashSet<>());
        for (Long dependentJob : dependentJobs) {
            int newInDegree = tempInDegree.get(dependentJob) - 1;
            tempInDegree.put(dependentJob, newInDegree);
            
            if (newInDegree == 0) {
                queue.offer(dependentJob);
            }
        }
    }
    
    return result;
}
```

### 3. Core Services

#### DependencyGraphService
Main service for dependency management:

- **Graph Construction**: Build from database, maintain in-memory cache
- **Dependency Operations**: Add/remove dependencies with cycle detection
- **Execution Planning**: Generate batched execution plans
- **Status Updates**: Handle job completion and unlock dependent jobs
- **Validation**: Ensure graph integrity and detect issues

#### Key Methods:
- `buildDependencyGraph()`: Construct graph from database
- `addDependency(childJobId, parentJobId)`: Add dependency with cycle check
- `getJobsReadyForExecution()`: Find jobs with no unsatisfied dependencies
- `topologicalSort()`: Get complete execution order
- `updateJobCompletion(jobId)`: Process completion and unlock dependents
- `validateDependencyGraph()`: Check integrity and detect issues

### 4. Database Integration

#### JobDependencyRepository
Comprehensive repository with optimized queries:

```java
// Find jobs ready for execution
@Query("SELECT j.id FROM Job j WHERE j.status = 'PENDING' AND j.id NOT IN " +
       "(SELECT jd.jobId FROM JobDependency jd WHERE jd.isSatisfied = false AND jd.isBlocking = true)")
List<Long> findJobsReadyForExecution();

// Detect circular dependencies (PostgreSQL specific)
@Query(value = "WITH RECURSIVE dependency_path AS ( " +
               "SELECT job_id, dependency_job_id, ARRAY[job_id] as path " +
               "FROM job_dependencies " +
               "UNION ALL " +
               "SELECT dp.job_id, jd.dependency_job_id, path || jd.job_id " +
               "FROM job_dependencies jd " +
               "JOIN dependency_path dp ON jd.job_id = dp.dependency_job_id " +
               "WHERE NOT jd.job_id = ANY(path) " +
               ") " +
               "SELECT DISTINCT job_id FROM dependency_path " +
               "WHERE dependency_job_id = ANY(path)", 
       nativeQuery = true)
List<Long> findCircularDependencies();
```

#### Performance Optimizations:
- **Indexes**: job_id, dependency_job_id, satisfied status
- **Batch Operations**: Bulk updates for satisfaction status
- **Recursive CTEs**: PostgreSQL-specific cycle detection
- **Cached Queries**: In-memory adjacency lists for frequent operations

### 5. REST API Endpoints

#### DependencyGraphController
Complete REST API for dependency management:

```bash
# Build dependency graph
POST /api/v1/dependencies/graph/build

# Add dependency relationship
POST /api/v1/dependencies/{childJobId}/depends-on/{parentJobId}

# Remove dependency
DELETE /api/v1/dependencies/{childJobId}/depends-on/{parentJobId}

# Get jobs ready for execution
GET /api/v1/dependencies/ready-jobs

# Perform topological sort
GET /api/v1/dependencies/topological-sort

# Get execution plan (batched)
GET /api/v1/dependencies/execution-plan

# Get job dependencies and dependents
GET /api/v1/dependencies/{jobId}/dependencies

# Update job completion
POST /api/v1/dependencies/{jobId}/complete

# Get dependency statistics
GET /api/v1/dependencies/stats

# Validate graph integrity
GET /api/v1/dependencies/validate

# Batch add dependencies
POST /api/v1/dependencies/batch
```

### 6. Integration with Job Scheduler

#### Enhanced JobSchedulerService
The scheduler now includes dependency-aware processing:

```java
@Scheduled(fixedDelay = 5000)
public void processJobQueue() {
    // Get jobs ready for execution (dependency-aware)
    List<Job> readyJobs = dependencyGraphService.getJobsReadyForExecution();
    
    // Process only jobs with satisfied dependencies
    for (Worker worker : availableWorkers) {
        if (worker.hasAvailableCapacity() && processedJobs < readyJobs.size()) {
            Job job = readyJobs.get(processedJobs);
            assignJobToWorker(job, worker);
            processedJobs++;
        }
    }
}

private void handleJobSuccess(Job job, Worker worker) {
    // Update job status
    jobService.updateJobStatus(job.getId(), JobStatus.COMPLETED);
    
    // Update dependency graph and get newly available jobs
    List<Job> newlyReadyJobs = dependencyGraphService.updateJobCompletion(job.getId());
    
    // Add newly ready jobs to the priority queue
    for (Job readyJob : newlyReadyJobs) {
        queueService.addJobToQueue(readyJob);
    }
}
```

## Advanced Features

### 1. Cycle Detection
- **Path Checking**: DFS-based cycle detection before adding dependencies
- **Prevention**: Reject dependency additions that would create cycles
- **Validation**: Periodic cycle detection using recursive CTEs

### 2. Dynamic Updates
- **Real-time Updates**: In-memory graph updates synchronized with database
- **Consistency Maintenance**: Periodic rebuilds and validation
- **Concurrent Access**: Thread-safe operations using synchronized blocks

### 3. Execution Planning
- **Batch Generation**: Group jobs by dependency levels
- **Parallel Execution**: Identify jobs that can run simultaneously
- **Resource Optimization**: Consider worker availability and job priorities

### 4. Monitoring and Statistics
- **Graph Metrics**: Node count, edge count, connectivity statistics
- **Performance Tracking**: Execution times, dependency satisfaction rates
- **Health Monitoring**: Cycle detection, orphaned dependencies, consistency checks

## Usage Examples

### Basic Dependency Management

```java
// Add dependency: Job 2 depends on Job 1
dependencyGraphService.addDependency(2L, 1L);

// Get jobs ready for execution
List<Job> readyJobs = dependencyGraphService.getJobsReadyForExecution();

// Complete job and unlock dependents
List<Job> newlyReady = dependencyGraphService.updateJobCompletion(1L);
```

### REST API Usage

```bash
# Add dependency
curl -X POST http://localhost:8080/api/v1/dependencies/2/depends-on/1

# Get execution plan
curl -X GET http://localhost:8080/api/v1/dependencies/execution-plan

# Validate graph
curl -X GET http://localhost:8080/api/v1/dependencies/validate
```

### Batch Operations

```bash
# Add multiple dependencies
curl -X POST http://localhost:8080/api/v1/dependencies/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"childJobId": 2, "parentJobId": 1},
    {"childJobId": 3, "parentJobId": 1},
    {"childJobId": 4, "parentJobId": 2}
  ]'
```

## Performance Characteristics

### Time Complexity
- **Graph Construction**: O(V + E) where V = jobs, E = dependencies
- **Topological Sort**: O(V + E) using Kahn's algorithm
- **Dependency Addition**: O(V + E) worst case (cycle detection)
- **Job Completion Update**: O(D) where D = immediate dependents

### Space Complexity
- **In-Memory Graph**: O(V + E) for adjacency lists
- **Database Storage**: O(E) for dependency relationships
- **Cache Overhead**: Minimal with ConcurrentHashMap optimization

### Scalability
- **Database Indexes**: Efficient queries up to millions of dependencies
- **In-Memory Cache**: Fast lookups for frequently accessed relationships
- **Batch Processing**: Optimized for high-throughput dependency operations
- **Concurrent Access**: Thread-safe operations with minimal locking

## Configuration

### Database Configuration
```sql
-- Indexes for optimal performance
CREATE INDEX idx_job_dependencies_job_id ON job_dependencies(job_id);
CREATE INDEX idx_job_dependencies_dependency_job_id ON job_dependencies(dependency_job_id);
CREATE INDEX idx_job_dependencies_satisfied ON job_dependencies(is_satisfied);
CREATE INDEX idx_job_dependencies_created_at ON job_dependencies(created_at);

-- Unique constraint to prevent duplicate dependencies
ALTER TABLE job_dependencies ADD CONSTRAINT uk_job_dependency 
UNIQUE(job_id, dependency_job_id);
```

### Application Properties
```properties
# Scheduling intervals
jobscheduler.dependency.rebuild-interval=300000  # 5 minutes
jobscheduler.dependency.validation-interval=60000 # 1 minute

# Graph limits
jobscheduler.dependency.max-depth=10
jobscheduler.dependency.max-dependencies-per-job=100

# Performance tuning
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## Testing

### Unit Tests
- Cycle detection algorithms
- Topological sort correctness
- Dependency addition/removal operations
- Graph validation logic

### Integration Tests
- Database consistency
- REST API endpoints
- Scheduler integration
- Concurrent access scenarios

### Performance Tests
- Large graph handling (10k+ jobs, 50k+ dependencies)
- Concurrent dependency operations
- Memory usage optimization
- Query performance validation

## Error Handling

### Common Scenarios
1. **Cycle Detection**: Reject dependencies that create cycles
2. **Orphaned Dependencies**: Handle jobs with missing references
3. **Concurrent Modifications**: Thread-safe graph updates
4. **Database Inconsistencies**: Periodic validation and repair

### Recovery Mechanisms
1. **Graph Rebuild**: Reconstruct from database when inconsistencies detected
2. **Partial Recovery**: Handle individual dependency failures gracefully
3. **Rollback Operations**: Undo dependency changes on validation failures
4. **Health Monitoring**: Continuous monitoring with alerting

## Future Enhancements

1. **Distributed Graph Management**: Multi-instance synchronization
2. **Advanced Dependency Types**: Time-based, conditional, resource-based
3. **Visual Graph Explorer**: Web-based dependency visualization
4. **Machine Learning Integration**: Dependency prediction and optimization
5. **Event-Driven Updates**: Real-time dependency satisfaction notifications

This implementation provides a robust, scalable foundation for managing complex job dependencies in distributed systems while maintaining high performance and data consistency.
