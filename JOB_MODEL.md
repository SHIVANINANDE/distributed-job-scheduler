# Enhanced Job Model Documentation

## Overview

This document describes the enhanced Job model implemented for the distributed job scheduler. The model now includes comprehensive support for job priorities, dependencies, estimated duration, and worker assignment information.

## Job Model Structure

### Core Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `id` | Long | Unique job identifier | Auto-generated | - |
| `name` | String | Job name | Yes | - |
| `description` | String | Job description | No | - |
| `status` | JobStatus | Current job status | Yes | PENDING |
| `priority` | Integer | Job priority (1-1000) | Yes | 100 |
| `createdAt` | LocalDateTime | Job creation timestamp | Auto-generated | - |
| `updatedAt` | LocalDateTime | Last update timestamp | Auto-generated | - |

### Scheduling Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `scheduledAt` | LocalDateTime | When job should start | No | - |
| `startedAt` | LocalDateTime | When job execution began | Auto-set | - |
| `completedAt` | LocalDateTime | When job finished | Auto-set | - |

### Duration Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `estimatedDurationMinutes` | Long | Expected execution time in minutes | No | - |
| `actualDurationMinutes` | Long | Actual execution time in minutes | Auto-calculated | - |

### Dependency Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `dependencyJobIds` | List<Long> | List of jobs this job depends on | No | Empty list |

### Worker Assignment Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `assignedWorkerId` | String | ID of assigned worker | Auto-assigned | - |
| `assignedWorkerName` | String | Name of assigned worker | Auto-assigned | - |
| `workerAssignedAt` | LocalDateTime | When worker was assigned | Auto-set | - |
| `workerStartedAt` | LocalDateTime | When worker started processing | Auto-set | - |
| `workerHost` | String | Worker host address | Auto-assigned | - |
| `workerPort` | Integer | Worker port number | Auto-assigned | - |

### Error Handling Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `errorMessage` | String | Error message if job failed | Auto-set | - |
| `retryCount` | Integer | Number of retry attempts | Auto-managed | 0 |
| `maxRetries` | Integer | Maximum retry attempts | No | 3 |

### Configuration Fields

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `jobType` | String | Type/category of job | No | - |
| `parameters` | String | JSON parameters for job execution | No | - |

## Job Status Enum

```java
public enum JobStatus {
    PENDING,    // Job is waiting to be executed
    RUNNING,    // Job is currently being executed
    COMPLETED,  // Job finished successfully
    FAILED,     // Job failed with error
    CANCELLED,  // Job was cancelled
    SCHEDULED   // Job is scheduled for future execution
}
```

## Key Features

### 1. Priority-Based Scheduling

Jobs are assigned priorities (1-1000, higher number = higher priority):

- **Critical/High Priority**: 200-1000
- **Normal Priority**: 100-199
- **Low Priority**: 1-99

Priority calculation considers:
- Base priority set by user
- Job age (prevents starvation)
- Retry count (lowers priority for retries)
- Job type bonuses

### 2. Dependency Management

Jobs can depend on other jobs with the following features:

- **Multiple Dependencies**: A job can depend on multiple other jobs
- **Dependency Validation**: Prevents circular dependencies
- **Automatic Resolution**: Dependencies are automatically checked
- **Blocking Behavior**: Jobs wait until all dependencies are satisfied

#### Dependency Methods:

```java
// Add a dependency
job.addDependency(dependentJobId);

// Remove a dependency
job.removeDependency(dependentJobId);

// Check if job has dependencies
boolean hasDeps = job.hasDependencies();

// Get all dependencies
List<Long> deps = job.getDependencyJobIds();
```

### 3. Worker Assignment

Jobs are automatically assigned to available workers:

#### Assignment Methods:

```java
// Assign job to worker
job.assignToWorker("worker-1", "Worker Node 1", "192.168.1.100", 8080);

// Check if job is assigned
boolean isAssigned = job.isAssignedToWorker();

// Unassign job
job.unassignFromWorker();
```

### 4. Duration Tracking

Jobs track both estimated and actual execution times:

```java
// Set estimated duration
job.setEstimatedDuration(Duration.ofMinutes(30));

// Get estimated duration
Duration estimated = job.getEstimatedDuration();

// Actual duration is calculated automatically
job.calculateActualDuration();
Duration actual = job.getActualDuration();
```

## Job Dependency Model

### JobDependency Entity

```java
@Entity
public class JobDependency {
    private Long id;
    private Long jobId;                    // Job that has the dependency
    private Long dependencyJobId;          // Job that must complete first
    private DependencyType dependencyType; // Type of dependency
    private Boolean isSatisfied;           // Whether dependency is satisfied
    private LocalDateTime satisfiedAt;     // When dependency was satisfied
    private LocalDateTime createdAt;       // When dependency was created
}
```

### Dependency Types

```java
public enum DependencyType {
    MUST_COMPLETE,  // Dependency job must complete successfully
    MUST_START,     // Dependency job must start (any status)
    MUST_SUCCEED,   // Dependency job must succeed (no failure)
    CONDITIONAL     // Custom condition-based dependency
}
```

## Worker Model

### Worker Entity

```java
@Entity
public class Worker {
    private Long id;
    private String workerId;              // Unique worker identifier
    private String name;                  // Worker display name
    private WorkerStatus status;          // Current worker status
    private String hostName;              // Worker hostname
    private String hostAddress;           // Worker IP address
    private Integer port;                 // Worker port
    private Integer maxConcurrentJobs;    // Maximum concurrent jobs
    private Integer currentJobCount;      // Current job count
    private Long totalJobsProcessed;      // Total jobs processed
    private Long totalJobsSuccessful;     // Total successful jobs
    private Long totalJobsFailed;         // Total failed jobs
    private LocalDateTime lastHeartbeat;  // Last heartbeat timestamp
    private String capabilities;          // JSON capabilities
    private String tags;                  // JSON tags
}
```

### Worker Status Enum

```java
public enum WorkerStatus {
    ACTIVE,      // Worker is active and available
    INACTIVE,    // Worker is inactive
    BUSY,        // Worker is at capacity
    ERROR,       // Worker has errors
    MAINTENANCE  // Worker is in maintenance mode
}
```

## Repository Methods

### Enhanced JobRepository

```java
// Priority-based queries
List<Job> findByStatusOrderByPriorityDesc(JobStatus status);
List<Job> findHighPriorityJobs(Integer threshold);

// Worker assignment queries
List<Job> findByAssignedWorkerId(String workerId);
List<Job> findUnassignedJobs();

// Dependency queries
List<Job> findJobsReadyToExecute();
List<Job> findJobsWaitingForDependencies();

// Duration queries
List<Job> findJobsExceededEstimatedDuration();
List<Job> findJobsCompletedFasterThanEstimated();

// Statistics queries
List<Object[]> countJobsByWorker();
List<Object[]> getAverageExecutionTimeByJobType();
```

### JobDependencyRepository

```java
// Dependency management
List<JobDependency> findByJobId(Long jobId);
List<JobDependency> findUnsatisfiedDependenciesByJobId(Long jobId);
boolean areAllDependenciesSatisfied(Long jobId);

// Dependency resolution
List<Long> findJobsWithSatisfiedDependencies();
List<Object[]> findDependencyChain(Long jobId);
```

### WorkerRepository

```java
// Worker management
Optional<Worker> findByWorkerId(String workerId);
List<Worker> findAvailableWorkers();
List<Worker> findAvailableWorkersOrderByLoad();

// Worker monitoring
List<Worker> findWorkersWithRecentHeartbeat(LocalDateTime since);
List<Worker> findPotentiallyDeadWorkers(LocalDateTime threshold);

// Worker statistics
long countAvailableWorkers();
Object[] getWorkerStatistics();
```

## Usage Examples

### Creating a Job with Dependencies

```java
// Create main job
Job mainJob = new Job("Process Data", "Main data processing job", JobStatus.PENDING, 150);
mainJob.setEstimatedDuration(Duration.ofMinutes(45));
mainJob.setJobType("data-processing");

// Add dependencies
mainJob.addDependency(preparationJobId);
mainJob.addDependency(validationJobId);

// Save job
Job savedJob = jobService.createJob(mainJob);
```

### Assigning Job to Worker

```java
// Find available worker
List<Worker> availableWorkers = workerRepository.findAvailableWorkersOrderByLoad();
if (!availableWorkers.isEmpty()) {
    Worker worker = availableWorkers.get(0);
    
    // Assign job to worker
    job.assignToWorker(
        worker.getWorkerId(),
        worker.getName(),
        worker.getHostAddress(),
        worker.getPort()
    );
    
    jobRepository.save(job);
}
```

### Checking Job Dependencies

```java
// Check if job can be executed
boolean canExecute = jobDependencyRepository.areAllDependenciesSatisfied(jobId);

if (canExecute) {
    // Start job execution
    jobService.startJob(jobId);
} else {
    // Job must wait for dependencies
    List<JobDependency> pending = jobDependencyRepository
        .findUnsatisfiedDependenciesByJobId(jobId);
}
```

### Priority Calculation Example

```java
private double calculateJobPriority(Job job) {
    double basePriority = job.getPriority();
    
    // Age bonus (1 point per hour)
    long hoursOld = Duration.between(job.getCreatedAt(), LocalDateTime.now()).toHours();
    basePriority += hoursOld;
    
    // Retry penalty (-20 points per retry)
    basePriority -= (job.getRetryCount() * 20);
    
    // Type bonus
    switch (job.getJobType()) {
        case "critical": basePriority += 100; break;
        case "high": basePriority += 50; break;
        case "low": basePriority -= 30; break;
    }
    
    return Math.max(basePriority, 1.0);
}
```

## Database Schema

The enhanced model uses the following tables:

1. **jobs** - Main job table with all job information
2. **workers** - Worker node information
3. **job_dependencies** - Simple job dependency mapping
4. **job_dependency_tracking** - Detailed dependency tracking
5. **worker_nodes** - Legacy worker table (backward compatibility)

## Best Practices

### 1. Priority Management
- Use priorities 1-1000 (higher = more important)
- Reserve 900+ for critical system jobs
- Use 100-200 for normal user jobs
- Use 1-99 for background/cleanup jobs

### 2. Dependency Design
- Keep dependency chains shallow (< 5 levels)
- Avoid circular dependencies
- Use meaningful dependency types
- Monitor dependency resolution times

### 3. Worker Assignment
- Consider worker capabilities when assigning
- Balance load across workers
- Monitor worker health and capacity
- Implement graceful worker failure handling

### 4. Duration Estimation
- Use historical data to improve estimates
- Account for data size and complexity
- Update estimates based on actual performance
- Set reasonable timeouts

## Migration Guide

### From Legacy Job Model

1. **Priority Field**: Default priority is now 100 (was 0)
2. **Dependencies**: Move from separate table to collection
3. **Worker Assignment**: Use new worker assignment fields
4. **Duration**: Add estimated and actual duration tracking

### Database Migration Steps

1. Add new columns to jobs table
2. Create workers table
3. Migrate worker_nodes data to workers
4. Update job_dependencies structure
5. Add new indexes for performance

## Performance Considerations

### Indexing Strategy
- Primary indexes on status, priority, created_at
- Composite index on (status, priority DESC)
- Worker assignment indexes
- Dependency tracking indexes

### Query Optimization
- Use repository methods for common queries
- Implement pagination for large result sets
- Cache frequently accessed job data
- Monitor query performance

### Scaling Considerations
- Partition jobs table by date
- Archive completed jobs regularly
- Use Redis for job queue management
- Implement worker pool management

This enhanced job model provides a robust foundation for managing complex job workflows with dependencies, priorities, and distributed worker coordination.
