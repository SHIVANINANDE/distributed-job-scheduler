-- Database Schema for Distributed Job Scheduler

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create ENUM types
CREATE TYPE job_status AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'SCHEDULED');
CREATE TYPE node_status AS ENUM ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED');
CREATE TYPE dependency_type AS ENUM ('SEQUENTIAL', 'PARALLEL', 'CONDITIONAL');

-- Jobs table
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    job_type VARCHAR(255),
    status job_status NOT NULL DEFAULT 'PENDING',
    parameters TEXT, -- JSON string for job parameters
    max_retries INTEGER DEFAULT 3,
    retry_count INTEGER DEFAULT 0,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    worker_node_id BIGINT,
    created_by VARCHAR(255),
    tags TEXT[], -- Array of tags for job categorization
    timeout_seconds INTEGER DEFAULT 3600,
    
    -- Indexes
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries >= 0),
    CONSTRAINT chk_priority CHECK (priority >= 0)
);

-- Worker Nodes table
CREATE TABLE IF NOT EXISTS worker_nodes (
    id BIGSERIAL PRIMARY KEY,
    node_name VARCHAR(255) NOT NULL UNIQUE,
    node_address VARCHAR(255) NOT NULL,
    node_port INTEGER NOT NULL DEFAULT 8080,
    status node_status NOT NULL DEFAULT 'ACTIVE',
    cpu_cores INTEGER DEFAULT 1,
    memory_gb INTEGER DEFAULT 1,
    max_concurrent_jobs INTEGER DEFAULT 5,
    current_job_count INTEGER DEFAULT 0,
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB, -- Additional node metadata
    capabilities TEXT[], -- Array of job types this node can handle
    
    -- Constraints
    CONSTRAINT chk_cpu_cores CHECK (cpu_cores > 0),
    CONSTRAINT chk_memory_gb CHECK (memory_gb > 0),
    CONSTRAINT chk_max_concurrent_jobs CHECK (max_concurrent_jobs > 0),
    CONSTRAINT chk_current_job_count CHECK (current_job_count >= 0)
);

-- Job Dependencies table
CREATE TABLE IF NOT EXISTS job_dependencies (
    id BIGSERIAL PRIMARY KEY,
    parent_job_id BIGINT NOT NULL,
    dependent_job_id BIGINT NOT NULL,
    dependency_type dependency_type NOT NULL DEFAULT 'SEQUENTIAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    condition_expression TEXT, -- Optional condition for conditional dependencies
    
    -- Foreign key constraints
    CONSTRAINT fk_parent_job FOREIGN KEY (parent_job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_dependent_job FOREIGN KEY (dependent_job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    
    -- Prevent self-dependency
    CONSTRAINT chk_no_self_dependency CHECK (parent_job_id != dependent_job_id),
    
    -- Unique constraint to prevent duplicate dependencies
    CONSTRAINT uk_job_dependency UNIQUE (parent_job_id, dependent_job_id)
);

-- Job Execution History table
CREATE TABLE IF NOT EXISTS job_execution_history (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    worker_node_id BIGINT,
    execution_start TIMESTAMP NOT NULL,
    execution_end TIMESTAMP,
    status job_status NOT NULL,
    error_message TEXT,
    execution_log TEXT,
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_history_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_worker FOREIGN KEY (worker_node_id) REFERENCES worker_nodes(id) ON DELETE SET NULL
);

-- Job Queue table (for priority queue management)
CREATE TABLE IF NOT EXISTS job_queue (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL UNIQUE,
    priority INTEGER NOT NULL DEFAULT 0,
    queue_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estimated_duration INTEGER, -- in seconds
    required_capabilities TEXT[],
    
    -- Foreign key constraint
    CONSTRAINT fk_queue_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Job Schedules table (for recurring jobs)
CREATE TABLE IF NOT EXISTS job_schedules (
    id BIGSERIAL PRIMARY KEY,
    job_template_id BIGINT,
    schedule_name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    next_execution TIMESTAMP,
    last_execution TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_schedule_job FOREIGN KEY (job_template_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Add foreign key constraint to jobs table for worker_node_id
ALTER TABLE jobs ADD CONSTRAINT fk_job_worker_node 
FOREIGN KEY (worker_node_id) REFERENCES worker_nodes(id) ON DELETE SET NULL;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON jobs(created_at);
CREATE INDEX IF NOT EXISTS idx_jobs_scheduled_at ON jobs(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_jobs_priority ON jobs(priority DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_worker_node ON jobs(worker_node_id);
CREATE INDEX IF NOT EXISTS idx_jobs_tags ON jobs USING gin(tags);

CREATE INDEX IF NOT EXISTS idx_worker_nodes_status ON worker_nodes(status);
CREATE INDEX IF NOT EXISTS idx_worker_nodes_last_heartbeat ON worker_nodes(last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_worker_nodes_capabilities ON worker_nodes USING gin(capabilities);

CREATE INDEX IF NOT EXISTS idx_job_dependencies_parent ON job_dependencies(parent_job_id);
CREATE INDEX IF NOT EXISTS idx_job_dependencies_dependent ON job_dependencies(dependent_job_id);

CREATE INDEX IF NOT EXISTS idx_job_execution_history_job_id ON job_execution_history(job_id);
CREATE INDEX IF NOT EXISTS idx_job_execution_history_worker_id ON job_execution_history(worker_node_id);
CREATE INDEX IF NOT EXISTS idx_job_execution_history_start ON job_execution_history(execution_start);

CREATE INDEX IF NOT EXISTS idx_job_queue_priority ON job_queue(priority DESC, queue_time);
CREATE INDEX IF NOT EXISTS idx_job_queue_capabilities ON job_queue USING gin(required_capabilities);

CREATE INDEX IF NOT EXISTS idx_job_schedules_next_execution ON job_schedules(next_execution);
CREATE INDEX IF NOT EXISTS idx_job_schedules_enabled ON job_schedules(enabled);

-- Create triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_jobs_updated_at BEFORE UPDATE ON jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_worker_nodes_updated_at BEFORE UPDATE ON worker_nodes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_job_schedules_updated_at BEFORE UPDATE ON job_schedules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
