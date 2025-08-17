# Priority Queue Implementation - Step 5

## Overview

This implementation provides a sophisticated Redis-based priority queue system for the distributed job scheduler. It uses Redis Sorted Sets (ZADD/ZPOPMIN) to efficiently manage job priorities and provides comprehensive queue operations.

## 🏗️ Architecture Components

### 1. **JobPriorityQueueService**
- **Purpose**: Core service managing Redis-based priority queues
- **Location**: `com.jobscheduler.service.JobPriorityQueueService`
- **Key Features**:
  - Redis Sorted Sets for priority management
  - Multiple queue types (priority, processing, failed, completed)
  - Batch operations for efficiency
  - Automatic priority score calculation
  - Queue statistics and monitoring

### 2. **JobQueueController**
- **Purpose**: REST API endpoints for queue operations
- **Location**: `com.jobscheduler.controller.JobQueueController`
- **Endpoints**:
  - `POST /api/v1/queue/add/{jobId}` - Add job to queue
  - `POST /api/v1/queue/pop` - Pop highest priority job
  - `PUT /api/v1/queue/priority/{jobId}` - Update job priority
  - `GET /api/v1/queue/jobs` - Get queued jobs with pagination
  - `POST /api/v1/queue/batch/add` - Batch add jobs
  - `POST /api/v1/queue/batch/pop` - Batch pop jobs
  - `GET /api/v1/queue/stats` - Get queue statistics

### 3. **JobSchedulerService**
- **Purpose**: Automated job processing and worker assignment
- **Location**: `com.jobscheduler.service.JobSchedulerService`
- **Features**:
  - Scheduled job queue processing
  - Worker assignment logic
  - Job execution simulation
  - Health monitoring
  - Retry mechanism

## 🚀 Key Features

### Redis Sorted Sets Implementation

1. **Priority Scoring Algorithm**:
   ```java
   // Lower score = higher priority
   - HIGH priority: 0-999
   - MEDIUM priority: 1000-1999  
   - LOW priority: 2000+
   - Time factor: +1 per minute since creation
   - Urgency factor: Overdue jobs get negative bonus
   - Retry penalty: +100 per retry attempt
   ```

2. **Queue Types**:
   - **Priority Queue**: `job:priority:queue` - Jobs waiting to be processed
   - **Processing Queue**: `job:processing:queue` - Jobs currently being executed
   - **Failed Queue**: `job:failed:queue` - Failed jobs for analysis
   - **Completed Queue**: `job:completed:queue` - Successfully completed jobs

### Batch Operations

1. **Batch Add**:
   - Add multiple jobs to queue in single Redis operation
   - Atomic transaction ensures consistency
   - Bulk status updates

2. **Batch Pop**:
   - Pop multiple jobs for parallel processing
   - Optimal for worker pools
   - Load balancing support

### Queue Management

1. **Dynamic Priority Updates**:
   - Update job priority while in queue
   - Real-time priority adjustment
   - Maintains queue order

2. **Automatic Cleanup**:
   - Remove old completed/failed jobs
   - Configurable retention periods
   - Prevents Redis memory bloat

## 📊 Priority Calculation

### Base Priority Scoring
```java
switch (job.getPriority()) {
    case HIGH:   baseScore = 0;    // Highest priority
    case MEDIUM: baseScore = 1000; // Medium priority  
    case LOW:    baseScore = 2000; // Lowest priority
}
```

### Time-Based Adjustments
```java
// Older jobs get higher priority (prevent starvation)
long timeScore = (now - createdAt) / 60; // minutes since creation
baseScore += timeScore;

// Overdue scheduled jobs get priority boost
if (scheduledAt < now) {
    baseScore -= Math.abs((scheduledAt - now) / 60);
}
```

### Retry Penalty
```java
// Lower priority for retried jobs
baseScore += retryCount * 100;
```

## 🔄 Scheduled Operations

### 1. **Job Queue Processing** (Every 5 seconds)
```java
@Scheduled(fixedDelay = 5000)
public void processJobQueue()
```
- Finds available workers
- Pops highest priority jobs
- Assigns jobs to workers
- Updates job/worker status

### 2. **Scheduled Job Processing** (Every 30 seconds)
```java
@Scheduled(fixedDelay = 30000)
public void processScheduledJobs()
```
- Checks for jobs due for execution
- Adds due jobs to priority queue
- Handles scheduled job activation

### 3. **Worker Health Monitoring** (Every minute)
```java
@Scheduled(fixedDelay = 60000)
public void monitorWorkerHealth()
```
- Detects unhealthy workers
- Reassigns jobs from failed workers
- Maintains system reliability

### 4. **Queue Cleanup** (Every hour)
```java
@Scheduled(fixedDelay = 3600000)
public void cleanupOldJobs()
```
- Removes old completed/failed jobs
- Configurable retention periods
- Memory optimization

## 🛠️ API Usage Examples

### Add Job to Queue
```bash
curl -X POST http://localhost:8080/api/v1/queue/add/123
```

### Pop Highest Priority Job
```bash
curl -X POST http://localhost:8080/api/v1/queue/pop
```

### Update Job Priority
```bash
curl -X PUT "http://localhost:8080/api/v1/queue/priority/123?priority=HIGH"
```

### Batch Add Jobs
```bash
curl -X POST http://localhost:8080/api/v1/queue/batch/add \
  -H "Content-Type: application/json" \
  -d '[123, 124, 125]'
```

### Get Queue Statistics
```bash
curl -X GET http://localhost:8080/api/v1/queue/stats
```

## 📈 Queue Statistics

The system provides comprehensive queue statistics:

```json
{
  "success": true,
  "statistics": {
    "priorityQueueSize": 45,
    "processingQueueSize": 12,
    "failedQueueSize": 3,
    "completedQueueSize": 156,
    "priorityDistribution": {
      "HIGH": 5,
      "MEDIUM": 25,
      "LOW": 15
    },
    "timestamp": "2025-08-17T21:45:00"
  }
}
```

## 🔧 Configuration

### Redis Configuration
```properties
# Redis settings for priority queue
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000ms
spring.redis.database=0
```

### Async Configuration
```java
@Bean(name = "jobExecutor")
public Executor jobExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(500);
    return executor;
}
```

## 🎯 Performance Benefits

1. **Redis Sorted Sets**: O(log N) insertion and retrieval
2. **Batch Operations**: Reduced Redis round trips
3. **Parallel Processing**: Async job execution
4. **Memory Efficient**: Automatic cleanup of old jobs
5. **Scalable**: Supports multiple worker instances

## 🔄 Integration Points

### JobService Integration
- Automatic queue addition on job creation
- Status updates trigger queue movements
- Cache synchronization

### WorkerService Integration
- Worker availability checking
- Load balancing based on capacity
- Health monitoring integration

### Security Integration
- API endpoints secured via Spring Security
- CORS support for frontend integration
- Input validation and sanitization

## 🚨 Error Handling

1. **Redis Connection Issues**: Graceful degradation with logging
2. **Job Execution Failures**: Automatic retry mechanism
3. **Worker Failures**: Job reassignment to healthy workers
4. **Queue Inconsistencies**: Automatic reconciliation

## 📝 Monitoring & Logging

- Comprehensive logging at INFO/DEBUG levels
- Queue operation metrics
- Performance monitoring
- Error tracking and alerting

This implementation provides a robust, scalable, and efficient priority queue system that forms the core of the distributed job scheduler's job management capabilities.
