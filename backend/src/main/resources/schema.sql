-- Database Schema for Distributed Job Scheduler

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create ENUM types
CREATE TYPE job_status AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'SCHEDULED');
CREATE TYPE node_status AS ENUM ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED');
CREATE TYPE worker_status AS ENUM ('ACTIVE', 'INACTIVE', 'BUSY', 'ERROR', 'MAINTENANCE');
CREATE TYPE dependency_type AS ENUM ('MUST_COMPLETE', 'MUST_START', 'MUST_SUCCEED', 'CONDITIONAL', 'SOFT_DEPENDENCY', 'TIME_BASED', 'RESOURCE_BASED');
CREATE TYPE failure_action AS ENUM ('BLOCK', 'PROCEED', 'WARN', 'RETRY', 'SKIP', 'ESCALATE');

-- Jobs table
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    job_type VARCHAR(255),
    status job_status NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 100,
    parameters TEXT, -- JSON string for job parameters
    max_retries INTEGER DEFAULT 3,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    
    -- Duration fields
    estimated_duration_minutes BIGINT,
    actual_duration_minutes BIGINT,
    
    -- Worker assignment fields
    assigned_worker_id VARCHAR(255),
    assigned_worker_name VARCHAR(255),
    worker_assigned_at TIMESTAMP,
    worker_started_at TIMESTAMP,
    worker_host VARCHAR(255),
    worker_port INTEGER,
    
    -- Legacy fields for backward compatibility
    worker_node_id BIGINT,
    created_by VARCHAR(255),
    tags TEXT[], -- Array of tags for job categorization
    timeout_seconds INTEGER DEFAULT 3600,
    
    -- Constraints
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries >= 0),
    CONSTRAINT chk_priority CHECK (priority >= 1)
);

-- Workers table (enhanced worker management)
CREATE TABLE IF NOT EXISTS workers (
    id BIGSERIAL PRIMARY KEY,
    worker_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status worker_status NOT NULL DEFAULT 'INACTIVE',
    host_name VARCHAR(255),
    host_address VARCHAR(255),
    port INTEGER,
    max_concurrent_jobs INTEGER DEFAULT 1,
    current_job_count INTEGER DEFAULT 0,
    
    -- Enhanced capacity management fields
    current_job_ids TEXT, -- JSON array of currently assigned job IDs
    available_capacity INTEGER DEFAULT 1,
    reserved_capacity INTEGER DEFAULT 0,
    processing_capacity INTEGER DEFAULT 0,
    queue_capacity INTEGER DEFAULT 0,
    priority_threshold INTEGER DEFAULT 100,
    worker_load_factor DECIMAL(3,2) DEFAULT 1.0,
    
    total_jobs_processed BIGINT DEFAULT 0,
    total_jobs_successful BIGINT DEFAULT 0,
    total_jobs_failed BIGINT DEFAULT 0,
    last_heartbeat TIMESTAMP,
    last_job_completed TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(255),
    capabilities TEXT, -- JSON string of worker capabilities
    tags TEXT, -- JSON array of tags for job matching
    
    -- Constraints
    CONSTRAINT chk_max_concurrent_jobs CHECK (max_concurrent_jobs > 0),
    CONSTRAINT chk_current_job_count CHECK (current_job_count >= 0),
    CONSTRAINT chk_total_jobs_processed CHECK (total_jobs_processed >= 0),
    CONSTRAINT chk_total_jobs_successful CHECK (total_jobs_successful >= 0),
    CONSTRAINT chk_total_jobs_failed CHECK (total_jobs_failed >= 0),
    CONSTRAINT chk_worker_load_factor CHECK (worker_load_factor >= 0.1 AND worker_load_factor <= 2.0)
);

-- Job Dependencies table (enhanced with collection table support)
CREATE TABLE IF NOT EXISTS job_dependencies (
    job_id BIGINT NOT NULL,
    dependency_job_id BIGINT NOT NULL,
    
    -- Foreign key constraints
    CONSTRAINT fk_job_dependencies_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_dependencies_dependency FOREIGN KEY (dependency_job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    
    -- Prevent self-dependency
    CONSTRAINT chk_no_self_dependency CHECK (job_id != dependency_job_id),
    
    -- Primary key on both columns
    PRIMARY KEY (job_id, dependency_job_id)
);

-- Enhanced Job Dependencies table with detailed tracking
CREATE TABLE IF NOT EXISTS job_dependency_tracking (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL, -- Child job (depends on parent)
    dependency_job_id BIGINT NOT NULL, -- Parent job (must be satisfied)
    parent_job_id BIGINT NOT NULL, -- Same as dependency_job_id, for clarity
    child_job_id BIGINT NOT NULL, -- Same as job_id, for clarity
    dependency_type dependency_type NOT NULL DEFAULT 'MUST_COMPLETE',
    is_satisfied BOOLEAN NOT NULL DEFAULT FALSE,
    satisfied_at TIMESTAMP,
    
    -- Enhanced constraint and validation fields
    constraint_expression TEXT, -- Custom constraint logic
    validation_rule TEXT, -- Validation rule for conditional dependencies
    dependency_priority INTEGER DEFAULT 1,
    is_blocking BOOLEAN DEFAULT TRUE,
    is_optional BOOLEAN DEFAULT FALSE,
    timeout_minutes INTEGER,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    last_checked_at TIMESTAMP,
    check_interval_seconds INTEGER DEFAULT 30,
    failure_action VARCHAR(50) DEFAULT 'BLOCK',
    dependency_group VARCHAR(100), -- Group dependencies for AND/OR logic
    condition_met BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_dependency_tracking_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_tracking_dependency FOREIGN KEY (dependency_job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    
    -- Prevent self-dependency
    CONSTRAINT chk_no_self_dependency_tracking CHECK (job_id != dependency_job_id),
    
    -- Constraint checks
    CONSTRAINT chk_dependency_priority CHECK (dependency_priority >= 1 AND dependency_priority <= 10),
    CONSTRAINT chk_check_interval CHECK (check_interval_seconds >= 5),
    
    -- Unique constraint to prevent duplicate dependencies
    CONSTRAINT uk_job_dependency_tracking UNIQUE (job_id, dependency_job_id)
);

-- Worker Nodes table (legacy - for backward compatibility)
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
    CONSTRAINT chk_max_concurrent_jobs_nodes CHECK (max_concurrent_jobs > 0),
    CONSTRAINT chk_current_job_count_nodes CHECK (current_job_count >= 0)
);
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

-- Add foreign key constraints
ALTER TABLE jobs ADD CONSTRAINT fk_job_worker_node 
FOREIGN KEY (worker_node_id) REFERENCES worker_nodes(id) ON DELETE SET NULL;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON jobs(created_at);
CREATE INDEX IF NOT EXISTS idx_jobs_scheduled_at ON jobs(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_jobs_priority ON jobs(priority DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_worker_node ON jobs(worker_node_id);
CREATE INDEX IF NOT EXISTS idx_jobs_assigned_worker ON jobs(assigned_worker_id);
CREATE INDEX IF NOT EXISTS idx_jobs_tags ON jobs USING gin(tags);
CREATE INDEX IF NOT EXISTS idx_jobs_status_priority ON jobs(status, priority DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_estimated_duration ON jobs(estimated_duration_minutes);

CREATE INDEX IF NOT EXISTS idx_workers_status ON workers(status);
CREATE INDEX IF NOT EXISTS idx_workers_worker_id ON workers(worker_id);
CREATE INDEX IF NOT EXISTS idx_workers_last_heartbeat ON workers(last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_workers_host_address ON workers(host_address);
CREATE INDEX IF NOT EXISTS idx_workers_available_capacity ON workers(available_capacity);
CREATE INDEX IF NOT EXISTS idx_workers_load_factor ON workers(worker_load_factor);
CREATE INDEX IF NOT EXISTS idx_workers_priority_threshold ON workers(priority_threshold);

CREATE INDEX IF NOT EXISTS idx_worker_nodes_status ON worker_nodes(status);
CREATE INDEX IF NOT EXISTS idx_worker_nodes_last_heartbeat ON worker_nodes(last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_worker_nodes_capabilities ON worker_nodes USING gin(capabilities);

CREATE INDEX IF NOT EXISTS idx_job_dependencies_job_id ON job_dependencies(job_id);
CREATE INDEX IF NOT EXISTS idx_job_dependencies_dependency_id ON job_dependencies(dependency_job_id);

CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_job_id ON job_dependency_tracking(job_id);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_dependency_id ON job_dependency_tracking(dependency_job_id);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_satisfied ON job_dependency_tracking(is_satisfied);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_parent_child ON job_dependency_tracking(parent_job_id, child_job_id);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_blocking ON job_dependency_tracking(is_blocking);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_optional ON job_dependency_tracking(is_optional);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_priority ON job_dependency_tracking(dependency_priority);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_group ON job_dependency_tracking(dependency_group);
CREATE INDEX IF NOT EXISTS idx_job_dependency_tracking_last_checked ON job_dependency_tracking(last_checked_at);

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

CREATE TRIGGER update_workers_updated_at BEFORE UPDATE ON workers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_worker_nodes_updated_at BEFORE UPDATE ON worker_nodes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_job_schedules_updated_at BEFORE UPDATE ON job_schedules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
