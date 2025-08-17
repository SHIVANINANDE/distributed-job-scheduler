#!/bin/bash

# Database Setup Validation Script
# Run this script to validate the database setup

echo "=== Distributed Job Scheduler Database Setup Validation ==="
echo

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    echo "   Visit: https://docs.docker.com/get-docker/"
    exit 1
fi

echo "✅ Docker is installed"

# Check if docker-compose or docker compose is available
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
else
    echo "❌ Docker Compose is not available"
    exit 1
fi

echo "✅ Docker Compose is available ($DOCKER_COMPOSE_CMD)"

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ docker-compose.yml not found in current directory"
    exit 1
fi

echo "✅ docker-compose.yml found"

# Start the services
echo
echo "🚀 Starting database services..."
$DOCKER_COMPOSE_CMD up -d

# Wait for services to be ready
echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo
echo "📊 Service Status:"
$DOCKER_COMPOSE_CMD ps

# Check PostgreSQL health
echo
echo "🔍 Testing PostgreSQL connection..."
if docker exec distributed-job-scheduler-db pg_isready -U postgres -d distributed_job_scheduler; then
    echo "✅ PostgreSQL is ready"
else
    echo "❌ PostgreSQL is not ready"
fi

# Check Redis health
echo
echo "🔍 Testing Redis connection..."
if docker exec distributed-job-scheduler-redis redis-cli ping | grep -q "PONG"; then
    echo "✅ Redis is ready"
else
    echo "❌ Redis is not ready"
fi

# Display connection information
echo
echo "🔗 Connection Information:"
echo "   PostgreSQL: localhost:5432"
echo "   Database: distributed_job_scheduler"
echo "   Username: postgres"
echo "   Password: password"
echo
echo "   Redis: localhost:6379"
echo "   Database: 0"
echo
echo "   Web Interfaces:"
echo "   - Adminer (PostgreSQL GUI): http://localhost:8081"
echo "   - Redis Commander: http://localhost:8082"

# Show logs command
echo
echo "📝 To view logs:"
echo "   $DOCKER_COMPOSE_CMD logs postgres"
echo "   $DOCKER_COMPOSE_CMD logs redis"

# Show stop command
echo
echo "🛑 To stop services:"
echo "   $DOCKER_COMPOSE_CMD down"

echo
echo "=== Database setup validation complete ==="
