# Redis Configuration Documentation

## Overview

This document describes the Redis configuration implemented for the distributed job scheduler application. Redis is used for priority queue management, caching, and worker coordination.

## Configuration Components

### 1. RedisConfig.java

Main configuration class that sets up Redis templates and connection settings:

- **Primary RedisTemplate**: Generic operations with JSON serialization
- **StringRedisTemplate**: Simple string operations
- **PriorityQueueTemplate**: Sorted sets for job priority management
- **JobQueueTemplate**: Job object serialization and caching
- **CacheManager**: Automatic caching with TTL settings
- **ValueOperations & ZSetOperations**: Specialized Redis operations

### 2. RedisPriorityQueueService.java

Service for managing job priority queue using Redis Sorted Sets (ZSet):

#### Key Features:
- **Priority Queue Management**: Add, remove, and poll jobs by priority
- **Job Locking**: Prevent duplicate job execution across workers
- **Priority Calculation**: Dynamic priority based on age, retries, and type
- **Queue Statistics**: Size, range queries, and monitoring

#### Key Methods:
- `addJobToPriorityQueue(jobId, priority)`: Add job with priority
- `pollHighestPriorityJob()`: Get and remove highest priority job
- `acquireJobLock(jobId, timeout)`: Lock job for processing
- `releaseJobLock(jobId)`: Release job lock
- `getQueueSize()`: Get current queue size

### 3. RedisCacheService.java

Service for caching and worker coordination:

#### Key Features:
- **Job Caching**: Cache job objects with TTL
- **Status Caching**: Quick job status lookups
- **Worker Heartbeats**: Track active workers
- **Execution Metrics**: Job execution counts and times
- **Cache Management**: Pattern-based cache clearing

#### Key Methods:
- `cacheJob(jobId, job, ttlMinutes)`: Cache job object
- `getCachedJob(jobId)`: Retrieve cached job
- `recordWorkerHeartbeat(workerId, time)`: Record worker activity
- `getActiveWorkers(timeoutMinutes)`: Get list of active workers
- `incrementJobExecutionCount(jobId)`: Track job executions

### 4. Updated JobService.java

Enhanced job service with Redis integration:

#### New Features:
- **Automatic Queue Management**: Jobs automatically added to priority queue
- **Cache Integration**: Jobs cached on creation and updates
- **Distributed Locking**: Prevent duplicate job execution
- **Retry Management**: Failed jobs re-queued with lower priority
- **Metrics Tracking**: Execution time and count tracking

## Redis Data Structures Used

### 1. Sorted Sets (ZSet)
- **Key**: `job:priority:queue`
- **Purpose**: Priority queue management
- **Score**: Job priority (higher = executed first)
- **Members**: Job IDs

### 2. Strings with TTL
- **Job Cache**: `job:cache:{jobId}` - Cached job objects
- **Job Status**: `job:status:{jobId}` - Quick status lookup
- **Job Locks**: `job:lock:{jobId}` - Processing locks
- **Worker Heartbeats**: `worker:heartbeat:{workerId}` - Worker activity
- **Metrics**: `job:metrics:{jobId}:*` - Execution statistics

## Priority Calculation Algorithm

The system uses a sophisticated priority calculation:

```java
basePriority = 100.0
+ hoursOld           // Prevent starvation
- (retryCount * 20)  // Lower priority for retries
+ jobTypeBonus       // Critical/High/Normal/Low
```

**Job Type Bonuses**:
- Critical/High: +50
- Normal: 0
- Low: -30

## API Endpoints

### Redis Management (RedisController.java)

- `GET /api/v1/redis/health` - Redis health check
- `GET /api/v1/redis/queue/stats` - Queue statistics
- `GET /api/v1/redis/workers` - Active workers list
- `DELETE /api/v1/redis/cache?pattern=*` - Clear cache by pattern
- `DELETE /api/v1/redis/queue` - Clear entire priority queue
- `POST /api/v1/redis/queue/job?jobId=X&priority=Y` - Add job to queue
- `GET /api/v1/redis/queue/job/{jobId}/priority` - Get job priority
- `PUT /api/v1/redis/queue/job/{jobId}/priority?priority=Y` - Update priority
- `DELETE /api/v1/redis/queue/job/{jobId}` - Remove job from queue

## Configuration Properties

### Development (application-dev.properties)
```properties
# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms
spring.data.redis.database=1

# Connection Pool (Lettuce)
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0
spring.data.redis.lettuce.pool.max-wait=-1ms

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=300000
spring.cache.redis.cache-null-values=false
```

### Production (application-prod.properties)
```properties
# Redis Configuration (Production)
spring.data.redis.host=redis
spring.data.redis.port=6379
spring.data.redis.timeout=5000ms
spring.data.redis.database=0
spring.data.redis.password=${REDIS_PASSWORD:}

# Connection Pool (Production)
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=10
spring.data.redis.lettuce.pool.min-idle=2
spring.data.redis.lettuce.pool.max-wait=2000ms
```

## Docker Integration

Redis is configured in `docker-compose.yml`:

```yaml
redis:
  image: redis:7-alpine
  container_name: job-scheduler-redis
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
    - ./redis.conf:/usr/local/etc/redis/redis.conf
  command: redis-server /usr/local/etc/redis/redis.conf
```

## Monitoring and Health Checks

### Health Check Endpoint
```bash
curl -X GET http://localhost:8080/api/v1/redis/health
```

Response:
```json
{
  "status": "UP",
  "available": true,
  "queueSize": 5,
  "activeWorkers": 3
}
```

### Queue Statistics
```bash
curl -X GET http://localhost:8080/api/v1/redis/queue/stats
```

Response:
```json
{
  "queueSize": 5,
  "highestPriorityJob": "123"
}
```

## Best Practices

1. **Connection Pooling**: Use Lettuce connection pooling for production
2. **TTL Settings**: Set appropriate TTL for cached data
3. **Lock Timeouts**: Use reasonable lock timeouts (5 minutes default)
4. **Priority Management**: Regularly monitor queue sizes
5. **Worker Heartbeats**: Implement heartbeat mechanism for worker health
6. **Error Handling**: Graceful fallback when Redis is unavailable
7. **Memory Management**: Monitor Redis memory usage in production

## Troubleshooting

### Common Issues:

1. **Redis Connection Failed**: Check Redis server status and network connectivity
2. **Lock Timeout**: Increase lock timeout for long-running jobs
3. **Queue Growing**: Monitor worker capacity and processing speed
4. **Memory Issues**: Implement cache eviction policies
5. **Serialization Errors**: Check Jackson configuration for custom objects

### Debug Commands:

```bash
# Check Redis connection
redis-cli ping

# Monitor Redis operations
redis-cli monitor

# Check queue size
redis-cli ZCARD job:priority:queue

# List all keys
redis-cli KEYS "*"

# Check memory usage
redis-cli INFO memory
```

## Future Enhancements

1. **Redis Cluster Support**: Horizontal scaling
2. **Redis Streams**: Event-driven job processing
3. **Advanced Metrics**: Prometheus integration
4. **Job Scheduling**: Cron-like scheduling with Redis
5. **Dead Letter Queue**: Failed job management
6. **Rate Limiting**: Redis-based rate limiting
7. **Distributed Sessions**: Session management with Redis
