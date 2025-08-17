# Database Setup - Quick Start Guide

## 🚀 Quick Setup

### Prerequisites
- Docker and Docker Compose installed
- Ports 5432, 6379, 8081, 8082 available

### 1. Start Database Services

```bash
# Clone and navigate to project
git clone https://github.com/SHIVANINANDE/distributed-job-scheduler.git
cd distributed-job-scheduler

# Start all database services
docker compose up -d

# Verify services are running
docker compose ps
```

### 2. Validate Setup

```bash
# Run the validation script
./validate-database-setup.sh
```

### 3. Access Web Interfaces

- **Adminer (PostgreSQL GUI)**: http://localhost:8081
  - Server: `postgres`
  - Username: `postgres`
  - Password: `password`
  - Database: `distributed_job_scheduler`

- **Redis Commander**: http://localhost:8082

## 📊 Database Schema

### Core Tables Created:
- `jobs` - Main job information
- `worker_nodes` - Available worker nodes
- `job_dependencies` - Job execution dependencies
- `job_execution_history` - Execution audit trail
- `job_queue` - Priority-based job queue
- `job_schedules` - Recurring job schedules

## 🔧 Connection Details

### PostgreSQL
```
Host: localhost
Port: 5432
Database: distributed_job_scheduler
Username: postgres
Password: password
```

### Redis
```
Host: localhost
Port: 6379
Database: 0
```

## 🏃‍♂️ Spring Boot Integration

### Development Profile (H2 + Redis)
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production Profile (PostgreSQL + Redis)
```bash
# Ensure Docker services are running
docker compose up -d

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 📈 Monitoring

### Health Checks
- Application: http://localhost:8080/actuator/health
- PostgreSQL: `docker exec distributed-job-scheduler-db pg_isready`
- Redis: `docker exec distributed-job-scheduler-redis redis-cli ping`

### Metrics
- Application metrics: http://localhost:8080/actuator/metrics
- Prometheus format: http://localhost:8080/actuator/prometheus

## 🛠 Management Commands

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# View logs
docker compose logs postgres
docker compose logs redis

# Database backup
docker exec distributed-job-scheduler-db pg_dump -U postgres distributed_job_scheduler > backup.sql

# Redis backup
docker exec distributed-job-scheduler-redis redis-cli BGSAVE
```

## 🔍 Troubleshooting

### Common Issues

1. **Port conflicts**: Ensure ports 5432, 6379, 8081, 8082 are free
2. **Permission issues**: Run `chmod +x validate-database-setup.sh`
3. **Docker not running**: Start Docker Desktop or Docker daemon

### Debug Commands

```bash
# Check container status
docker compose ps

# View real-time logs
docker compose logs -f

# Execute commands in containers
docker exec -it distributed-job-scheduler-db psql -U postgres
docker exec -it distributed-job-scheduler-redis redis-cli

# Check network connectivity
docker compose exec postgres ping redis
```

## 📚 Additional Resources

- [Full Database Documentation](./DATABASE_SETUP.md)
- [Docker Compose Reference](https://docs.docker.com/compose/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Redis Documentation](https://redis.io/documentation)
