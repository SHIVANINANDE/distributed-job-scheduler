# Enhanced Worker and Dependency Models Documentation

## Overview

This document describes the enhanced Worker Model and Dependency Model for the distributed job scheduler. These models now provide comprehensive support for worker capacity management, current job assignment tracking, and sophisticated dependency constraints with parent-child relationships.

## Enhanced Worker Model

### Core Worker Information

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `id` | Long | Unique worker identifier | Auto-generated | - |
| `workerId` | String | Unique worker ID string | Yes | - |
| `name` | String | Worker display name | Yes | - |
| `status` | WorkerStatus | Current worker status | Yes | INACTIVE |
| `lastHeartbeat` | LocalDateTime | Last heartbeat timestamp | Auto-updated | - |

### Network & Connection Information

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `hostName` | String | Worker hostname | No | - |
| `hostAddress` | String | Worker IP address | No | - |
| `port` | Integer | Worker port number | No | - |
| `version` | String | Worker software version | No | - |

### Enhanced Capacity Management

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `maxConcurrentJobs` | Integer | Maximum concurrent jobs | Yes | 1 |
| `currentJobCount` | Integer | Current active job count | Auto-managed | 0 |
| `currentJobIds` | String | JSON array of current job IDs | Auto-managed | "[]" |
| `availableCapacity` | Integer | Calculated available capacity | Auto-calculated | - |
| `reservedCapacity` | Integer | Capacity reserved for high-priority jobs | No | 0 |
| `processingCapacity` | Integer | Current processing capacity in use | Auto-calculated | - |
| `queueCapacity` | Integer | Number of jobs in worker's queue | Auto-managed | 0 |

### Worker Configuration & Tuning

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `priorityThreshold` | Integer | Minimum priority for job acceptance | No | 100 |
| `workerLoadFactor` | Double | Load adjustment factor (0.1-2.0) | No | 1.0 |
| `capabilities` | String | JSON capabilities string | No | - |
| `tags` | String | JSON tags for job matching | No | - |

### Statistics & Performance Tracking

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `totalJobsProcessed` | Long | Total jobs processed | Auto-managed | 0 |
| `totalJobsSuccessful` | Long | Total successful jobs | Auto-managed | 0 |
| `totalJobsFailed` | Long | Total failed jobs | Auto-managed | 0 |
| `lastJobCompleted` | LocalDateTime | Last job completion time | Auto-updated | - |

### Worker Status Enum

```java
public enum WorkerStatus {
    ACTIVE("Active and available for jobs"),
    INACTIVE("Inactive or offline"),
    BUSY("At maximum capacity"),
    ERROR("Error state"),
    MAINTENANCE("In maintenance mode");
}
```

### Enhanced Worker Methods

#### Capacity Management
```java
// Check worker availability
boolean isAvailable = worker.isAvailable();
boolean canAcceptJob = worker.canAcceptJob();
boolean canAcceptHighPriority = worker.canAcceptJob(500);

// Capacity calculations
worker.updateAvailableCapacity();
int effectiveCapacity = worker.getEffectiveCapacity();
double loadPercentage = worker.getLoadPercentage();
boolean isOverloaded = worker.isOverloaded();
```

#### Job Assignment
```java
// Assign job to worker
worker.assignJob(jobId);

// Unassign job from worker
worker.unassignJob(jobId);

// Check job assignments
String currentJobs = worker.getCurrentJobIds(); // JSON array
```

#### Performance Monitoring
```java
// Success rate calculation
double successRate = worker.getSuccessRate();

// Load factor calculations
double effectiveLoad = worker.getEffectiveLoadFactor();
boolean underUtilized = worker.isUnderUtilized();

// Heartbeat management
worker.updateHeartbeat();
```

## Enhanced Dependency Model

### Core Dependency Information

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `id` | Long | Unique dependency identifier | Auto-generated | - |
| `jobId` / `childJobId` | Long | Job that has the dependency | Yes | - |
| `dependencyJobId` / `parentJobId` | Long | Job that must be satisfied | Yes | - |
| `dependencyType` | DependencyType | Type of dependency | Yes | MUST_COMPLETE |
| `createdAt` | LocalDateTime | Dependency creation time | Auto-generated | - |

### Dependency Satisfaction Tracking

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `isSatisfied` | Boolean | Whether dependency is satisfied | Auto-managed | false |
| `satisfiedAt` | LocalDateTime | When dependency was satisfied | Auto-set | - |
| `conditionMet` | Boolean | For conditional dependencies | Auto-managed | false |

### Constraint & Validation Configuration

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `constraintExpression` | String | Custom constraint logic (JSON/expression) | No | - |
| `validationRule` | String | Validation rule for conditional dependencies | No | - |
| `dependencyPriority` | Integer | Priority of this dependency (1-10) | No | 1 |
| `dependencyGroup` | String | Group dependencies for AND/OR logic | No | - |

### Blocking & Optional Behavior

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `isBlocking` | Boolean | Whether this dependency blocks execution | No | true |
| `isOptional` | Boolean | Whether this dependency is optional | No | false |
| `failureAction` | FailureAction | What to do if dependency fails | No | BLOCK |

### Timeout & Retry Management

| Field | Type | Description | Required | Default |
|-------|------|-------------|----------|---------|
| `timeoutMinutes` | Integer | Timeout for dependency satisfaction | No | - |
| `retryCount` | Integer | Number of retry attempts | Auto-managed | 0 |
| `maxRetries` | Integer | Maximum retry attempts | No | 3 |
| `lastCheckedAt` | LocalDateTime | Last dependency check time | Auto-updated | - |
| `checkIntervalSeconds` | Integer | How often to check dependency | No | 30 |

### Enhanced Dependency Types

```java
public enum DependencyType {
    MUST_COMPLETE("Must Complete Successfully"),
    MUST_START("Must Start Execution"),
    MUST_SUCCEED("Must Succeed Without Errors"),
    CONDITIONAL("Conditional Based on Rules"),
    SOFT_DEPENDENCY("Soft Dependency (Warning Only)"),
    TIME_BASED("Time-Based Dependency"),
    RESOURCE_BASED("Resource Availability Based");
}
```

### Failure Action Options

```java
public enum FailureAction {
    BLOCK("Block job execution"),
    PROCEED("Proceed with warning"),
    WARN("Log warning and proceed"),
    RETRY("Retry dependency check"),
    SKIP("Skip this dependency"),
    ESCALATE("Escalate to admin");
}
```

### Enhanced Dependency Methods

#### Parent-Child Relationships
```java
// Clear parent-child relationship access
Long parentJobId = dependency.getParentJobId();
Long childJobId = dependency.getChildJobId();

// Relationship description
String description = dependency.getRelationshipDescription();
// Returns: "Job 123 depends on Job 456 (Must Complete Successfully)"
```

#### Dependency State Management
```java
// Satisfaction management
dependency.markAsSatisfied();
boolean isNotSatisfied = dependency.isNotSatisfied();

// Blocking and optional checks
boolean isBlocking = dependency.isBlocking();
boolean isOptional = dependency.isOptional();
```

#### Timeout & Retry Logic
```java
// Timeout checks
boolean hasTimedOut = dependency.hasTimedOut();
boolean shouldRetry = dependency.shouldRetry();
boolean needsRecheck = dependency.needsRecheck();

// Retry management
dependency.incrementRetryCount();
```

#### Priority & Failure Handling
```java
// Priority checks
boolean isHighPriority = dependency.isHighPriority(); // Priority >= 8
boolean isLowPriority = dependency.isLowPriority();   // Priority <= 3

// Failure action
boolean canProceed = dependency.canProceedOnFailure();
```

## Database Schema Enhancements

### Enhanced Workers Table
```sql
CREATE TABLE workers (
    -- Core fields
    id BIGSERIAL PRIMARY KEY,
    worker_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status worker_status NOT NULL DEFAULT 'INACTIVE',
    
    -- Network information
    host_name VARCHAR(255),
    host_address VARCHAR(255),
    port INTEGER,
    
    -- Enhanced capacity management
    max_concurrent_jobs INTEGER DEFAULT 1,
    current_job_count INTEGER DEFAULT 0,
    current_job_ids TEXT, -- JSON array of job IDs
    available_capacity INTEGER DEFAULT 1,
    reserved_capacity INTEGER DEFAULT 0,
    processing_capacity INTEGER DEFAULT 0,
    queue_capacity INTEGER DEFAULT 0,
    priority_threshold INTEGER DEFAULT 100,
    worker_load_factor DECIMAL(3,2) DEFAULT 1.0,
    
    -- Statistics and performance
    total_jobs_processed BIGINT DEFAULT 0,
    total_jobs_successful BIGINT DEFAULT 0,
    total_jobs_failed BIGINT DEFAULT 0,
    last_heartbeat TIMESTAMP,
    last_job_completed TIMESTAMP,
    
    -- Configuration
    capabilities TEXT, -- JSON capabilities
    tags TEXT, -- JSON tags
    version VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_worker_load_factor 
        CHECK (worker_load_factor >= 0.1 AND worker_load_factor <= 2.0)
);
```

### Enhanced Job Dependencies Table
```sql
CREATE TABLE job_dependency_tracking (
    -- Core relationship
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL, -- Child job
    dependency_job_id BIGINT NOT NULL, -- Parent job
    parent_job_id BIGINT NOT NULL, -- Same as dependency_job_id
    child_job_id BIGINT NOT NULL, -- Same as job_id
    
    -- Dependency configuration
    dependency_type dependency_type NOT NULL DEFAULT 'MUST_COMPLETE',
    dependency_priority INTEGER DEFAULT 1,
    dependency_group VARCHAR(100),
    
    -- Constraint and validation
    constraint_expression TEXT,
    validation_rule TEXT,
    
    -- Behavior configuration
    is_blocking BOOLEAN DEFAULT TRUE,
    is_optional BOOLEAN DEFAULT FALSE,
    failure_action failure_action DEFAULT 'BLOCK',
    
    -- Satisfaction tracking
    is_satisfied BOOLEAN DEFAULT FALSE,
    satisfied_at TIMESTAMP,
    condition_met BOOLEAN DEFAULT FALSE,
    
    -- Timeout and retry management
    timeout_minutes INTEGER,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    last_checked_at TIMESTAMP,
    check_interval_seconds INTEGER DEFAULT 30,
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints and indexes
    UNIQUE(job_id, dependency_job_id),
    CONSTRAINT chk_dependency_priority 
        CHECK (dependency_priority >= 1 AND dependency_priority <= 10),
    CONSTRAINT chk_check_interval 
        CHECK (check_interval_seconds >= 5)
);
```

## Usage Examples

### Enhanced Worker Management

#### Creating a Worker with Capacity Configuration
```java
Worker worker = new Worker("worker-001", "Primary Worker Node");
worker.setHostAddress("192.168.1.100");
worker.setPort(8080);
worker.setMaxConcurrentJobs(10);
worker.setReservedCapacity(2); // Reserve 2 slots for high-priority jobs
worker.setPriorityThreshold(50); // Accept jobs with priority >= 50
worker.setWorkerLoadFactor(1.2); // Can handle 20% more load
worker.setCapabilities("{\"types\":[\"data-processing\",\"image-resize\"]}");
worker.setTags("{\"environment\":\"production\",\"region\":\"us-east\"}");

workerRepository.save(worker);
```

#### Intelligent Worker Selection
```java
// Find best worker for a high-priority job
List<Worker> availableWorkers = workerRepository.findAvailableWorkersOrderByLoad();
Worker bestWorker = availableWorkers.stream()
    .filter(w -> w.canAcceptJob(priority))
    .filter(w -> !w.isOverloaded())
    .min(Comparator.comparing(Worker::getEffectiveLoadFactor))
    .orElse(null);

if (bestWorker != null) {
    job.assignToWorker(
        bestWorker.getWorkerId(),
        bestWorker.getName(),
        bestWorker.getHostAddress(),
        bestWorker.getPort()
    );
    bestWorker.assignJob(job.getId());
}
```

### Enhanced Dependency Management

#### Creating Complex Dependencies
```java
// Create a blocking dependency with timeout
JobDependency criticalDep = new JobDependency(
    mainJobId, 
    preparationJobId, 
    DependencyType.MUST_COMPLETE
);
criticalDep.setDependencyPriority(9); // High priority
criticalDep.setTimeoutMinutes(30);
criticalDep.setIsBlocking(true);
criticalDep.setFailureAction(FailureAction.BLOCK);

// Create an optional dependency with custom validation
JobDependency optionalDep = new JobDependency(
    mainJobId, 
    optimizationJobId, 
    DependencyType.CONDITIONAL,
    true // isOptional
);
optionalDep.setValidationRule("{\"condition\":\"success_rate > 0.8\"}");
optionalDep.setFailureAction(FailureAction.WARN);
optionalDep.setCheckIntervalSeconds(60);

// Group dependencies for OR logic
JobDependency dep1 = new JobDependency(jobId, parentJob1Id);
dep1.setDependencyGroup("data-sources");
JobDependency dep2 = new JobDependency(jobId, parentJob2Id);
dep2.setDependencyGroup("data-sources");
// Job can proceed when either dep1 OR dep2 is satisfied
```

#### Advanced Dependency Checking
```java
// Check if all dependencies are satisfied
boolean canProceed = jobDependencyRepository
    .areAllDependenciesSatisfied(jobId);

// Get pending blocking dependencies
List<JobDependency> blockingDeps = jobDependencyRepository
    .findUnsatisfiedDependenciesByJobId(jobId)
    .stream()
    .filter(JobDependency::isBlocking)
    .collect(Collectors.toList());

// Check for timed-out dependencies
List<JobDependency> timedOutDeps = jobDependencyRepository
    .findByJobId(jobId)
    .stream()
    .filter(JobDependency::hasTimedOut)
    .collect(Collectors.toList());

// Handle failed dependencies
for (JobDependency dep : timedOutDeps) {
    switch (dep.getFailureAction()) {
        case BLOCK:
            // Block job execution
            job.setStatus(JobStatus.FAILED);
            break;
        case PROCEED:
            // Continue with warning
            logger.warn("Dependency timed out but proceeding: {}", 
                       dep.getRelationshipDescription());
            break;
        case RETRY:
            if (dep.shouldRetry()) {
                dep.incrementRetryCount();
                // Schedule dependency recheck
            }
            break;
    }
}
```

## Performance Considerations

### Worker Model Optimizations

1. **Capacity Calculation Caching**
   - `availableCapacity` is automatically calculated and cached
   - Updates only when job assignments change
   - Reduces computation overhead during worker selection

2. **Load Factor Tuning**
   - `workerLoadFactor` allows fine-tuning of worker capacity
   - Values > 1.0 allow oversubscription for I/O-bound jobs
   - Values < 1.0 provide buffer for CPU-intensive jobs

3. **Priority Threshold Filtering**
   - `priorityThreshold` enables pre-filtering of unsuitable workers
   - Reduces unnecessary worker evaluation during job assignment

### Dependency Model Optimizations

1. **Grouped Dependency Evaluation**
   - `dependencyGroup` enables efficient OR/AND logic evaluation
   - Reduces individual dependency checks

2. **Check Interval Optimization**
   - `checkIntervalSeconds` prevents excessive dependency polling
   - Balances responsiveness with system load

3. **Priority-Based Processing**
   - `dependencyPriority` enables prioritized dependency resolution
   - High-priority dependencies checked more frequently

## Migration Guide

### From Basic Worker Model

1. **Add capacity management fields**
   ```sql
   ALTER TABLE workers ADD COLUMN current_job_ids TEXT DEFAULT '[]';
   ALTER TABLE workers ADD COLUMN available_capacity INTEGER DEFAULT 1;
   ALTER TABLE workers ADD COLUMN reserved_capacity INTEGER DEFAULT 0;
   ALTER TABLE workers ADD COLUMN worker_load_factor DECIMAL(3,2) DEFAULT 1.0;
   ```

2. **Update worker selection logic**
   - Replace simple availability checks with capacity-aware selection
   - Implement load factor calculations
   - Add priority threshold filtering

### From Basic Dependency Model

1. **Add constraint and validation fields**
   ```sql
   ALTER TABLE job_dependency_tracking ADD COLUMN parent_job_id BIGINT;
   ALTER TABLE job_dependency_tracking ADD COLUMN child_job_id BIGINT;
   ALTER TABLE job_dependency_tracking ADD COLUMN constraint_expression TEXT;
   ALTER TABLE job_dependency_tracking ADD COLUMN is_blocking BOOLEAN DEFAULT TRUE;
   ALTER TABLE job_dependency_tracking ADD COLUMN dependency_priority INTEGER DEFAULT 1;
   ```

2. **Update dependency resolution logic**
   - Implement parent-child relationship tracking
   - Add timeout and retry mechanisms
   - Support optional dependencies and failure actions

## Best Practices

### Worker Management

1. **Capacity Planning**
   - Set `maxConcurrentJobs` based on actual worker resources
   - Use `reservedCapacity` for critical job types
   - Monitor `loadPercentage` and adjust `workerLoadFactor` accordingly

2. **Health Monitoring**
   - Implement regular heartbeat updates
   - Monitor workers with `findWorkersWithRecentHeartbeat()`
   - Handle dead workers gracefully

3. **Load Balancing**
   - Use `findAvailableWorkersOrderByLoad()` for optimal distribution
   - Consider worker capabilities and tags for job matching
   - Implement worker priority thresholds for job segregation

### Dependency Management

1. **Dependency Design**
   - Keep dependency chains shallow (< 5 levels deep)
   - Use optional dependencies for non-critical requirements
   - Group related dependencies with `dependencyGroup`

2. **Timeout Management**
   - Set reasonable timeouts based on expected job duration
   - Use progressive retry intervals
   - Implement escalation for critical dependency failures

3. **Performance Optimization**
   - Set appropriate `checkIntervalSeconds` for each dependency type
   - Use high `dependencyPriority` only for truly critical dependencies
   - Monitor and tune dependency resolution performance

This enhanced Worker and Dependency model provides a robust foundation for enterprise-grade distributed job scheduling with sophisticated capacity management and dependency resolution capabilities.
