-- Database initialization script for PostgreSQL
CREATE DATABASE IF NOT EXISTS distributed_job_scheduler;

-- Create user if not exists (PostgreSQL syntax)
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles 
      WHERE  rolname = 'job_scheduler_user') THEN
      
      CREATE ROLE job_scheduler_user LOGIN PASSWORD 'scheduler_password';
   END IF;
END
$do$;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE distributed_job_scheduler TO job_scheduler_user;
