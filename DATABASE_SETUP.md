# Database Setup Documentation

## Overview

This document describes the database setup for the Distributed Job Scheduler, including PostgreSQL and Redis configurations, connection pooling, and schema design.

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │    │   PostgreSQL    │    │     Redis       │
│   Application   │────│   (Primary DB)  │    │   (Cache/Queue) │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                       │                       │
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                    ┌─────────────────┐
                    │  Adminer/GUI    │
                    │  (Port 8081)    │
                    └─────────────────┘
```

## Database Schema

### Core Tables

#### 1. Jobs Table
- **Purpose**: Main table storing job information
- **Key Features**:
  - ENUM types for status consistency
  - JSON parameters for flexible job configuration
  - Priority-based execution
  - Timeout and retry mechanisms
  - Tags for categorization

#### 2. Worker Nodes Table
- **Purpose**: Track available worker nodes and their capabilities
- **Key Features**:
  - Resource tracking (CPU, memory)
  - Capability matching
  - Health monitoring
  - Load balancing support

#### 3. Job Dependencies Table
- **Purpose**: Define job execution order and dependencies
- **Key Features**:
  - Sequential, parallel, and conditional dependencies
  - Circular dependency prevention
  - DAG (Directed Acyclic Graph) support

#### 4. Job Execution History Table
- **Purpose**: Audit trail and performance metrics
- **Key Features**:
  - Performance monitoring
  - Resource usage tracking
  - Execution logs

#### 5. Job Queue Table
- **Purpose**: Priority-based job queue management
- **Key Features**:
  - Priority scheduling
  - Capability-based routing
  - Estimated duration tracking

#### 6. Job Schedules Table
- **Purpose**: Recurring job management
- **Key Features**:
  - Cron-based scheduling
  - Template-based job creation
  - Enable/disable functionality

## Docker Compose Configuration

### Services

1. **PostgreSQL Database**
   - Image: postgres:15-alpine
   - Port: 5432
   - Volume: Persistent data storage
   - Health checks: Automatic readiness detection

2. **Redis Cache**
   - Image: redis:7-alpine
   - Port: 6379
   - Configuration: Custom redis.conf
   - Health checks: Redis ping

3. **Adminer** (Database GUI)
   - Port: 8081
   - Web-based database management

4. **Redis Commander** (Redis GUI)
   - Port: 8082
   - Web-based Redis management

### Quick Start

```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs postgres
docker-compose logs redis

# Stop services
docker-compose down
```

## Connection Pooling

### HikariCP Configuration (PostgreSQL)

```properties
# Production Settings
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.leak-detection-threshold=60000
```

### Benefits
- **Connection Reuse**: Reduces connection overhead
- **Resource Management**: Prevents connection exhaustion
- **Performance**: Optimized for high-throughput applications
- **Monitoring**: Built-in leak detection

### Redis Connection Pool (Lettuce)

```properties
# Production Settings
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=10
spring.data.redis.lettuce.pool.min-idle=5
spring.data.redis.lettuce.pool.max-wait=-1ms
```

## Performance Optimizations

### PostgreSQL Optimizations

1. **Indexing Strategy**:
   - Status-based queries: `idx_jobs_status`
   - Time-based queries: `idx_jobs_created_at`, `idx_jobs_scheduled_at`
   - Priority queries: `idx_jobs_priority`
   - Tag searches: GIN index on `tags` array

2. **Query Optimizations**:
   - Batch operations for bulk inserts/updates
   - Connection pooling for concurrent access
   - Read replicas for reporting queries

3. **Configuration Tuning**:
   - Shared buffers: 256MB
   - Work memory: 4MB
   - Max connections: 200
   - WAL optimization for write performance

### Redis Optimizations

1. **Memory Management**:
   - LRU eviction policy
   - 512MB memory limit
   - Key expiration for cache cleanup

2. **Persistence**:
   - RDB snapshots for data safety
   - AOF logging for durability
   - Configurable persistence intervals

3. **Performance**:
   - Pipelining for batch operations
   - Lazy freeing for large keys
   - Optimized data structures

## Monitoring and Health Checks

### Database Health
- **PostgreSQL**: `pg_isready` checks
- **Redis**: PING command verification
- **Connection Pools**: Active/idle connection monitoring

### Metrics Available
- Connection pool statistics
- Query performance metrics
- Redis memory usage
- Cache hit/miss ratios
- Job execution statistics

### Endpoints
- `/actuator/health` - Overall health status
- `/actuator/metrics` - Detailed metrics
- `/actuator/prometheus` - Prometheus-format metrics

## Environment-Specific Configurations

### Development
- H2 in-memory database for rapid development
- Redis localhost connection
- Detailed SQL logging
- All actuator endpoints exposed

### Production
- PostgreSQL with connection pooling
- Redis cluster support
- Minimal logging
- Restricted actuator endpoints
- Environment variable configuration

## Security Considerations

### Database Security
- Strong passwords via environment variables
- Network isolation via Docker networks
- Regular security updates
- Connection encryption (TLS)

### Redis Security
- Password protection (configurable)
- Network isolation
- Protected mode disabled only in development
- Regular security updates

## Backup and Recovery

### PostgreSQL Backup
```bash
# Database backup
docker exec postgres pg_dump -U postgres distributed_job_scheduler > backup.sql

# Restore
docker exec -i postgres psql -U postgres distributed_job_scheduler < backup.sql
```

### Redis Backup
```bash
# RDB snapshot
docker exec redis redis-cli BGSAVE

# Copy backup file
docker cp redis:/data/dump.rdb ./redis-backup.rdb
```

## Troubleshooting

### Common Issues

1. **Connection Pool Exhaustion**
   - Increase `maximum-pool-size`
   - Check for connection leaks
   - Monitor `leak-detection-threshold`

2. **Redis Memory Issues**
   - Adjust `maxmemory` setting
   - Review eviction policy
   - Monitor key expiration

3. **Performance Issues**
   - Analyze slow query logs
   - Check index usage
   - Monitor connection pool metrics

### Debug Commands

```bash
# Check PostgreSQL connections
docker exec postgres psql -U postgres -c "SELECT * FROM pg_stat_activity;"

# Redis connection info
docker exec redis redis-cli info clients

# Docker service logs
docker-compose logs -f [service-name]
```

## Migration and Schema Evolution

### Schema Versioning
- Flyway integration for database migrations
- Version-controlled schema changes
- Rollback capabilities
- Environment-specific migrations

### Best Practices
- Test migrations in development first
- Backup before production migrations
- Use reversible migrations when possible
- Monitor performance impact

## Future Enhancements

### Planned Features
1. **Read Replicas**: For reporting and analytics
2. **Sharding**: For horizontal scaling
3. **Multi-tenancy**: Tenant-specific data isolation
4. **Data Archiving**: Historical data management
5. **Real-time Analytics**: Stream processing with Redis Streams

### Scalability Considerations
- Horizontal scaling strategies
- Connection pool tuning
- Cache partitioning
- Database federation
