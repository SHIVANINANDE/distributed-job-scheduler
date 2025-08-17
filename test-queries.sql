-- Test queries for validating the database setup

-- 1. Verify all tables exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 2. Check job status distribution
SELECT status, COUNT(*) as count 
FROM jobs 
GROUP BY status 
ORDER BY count DESC;

-- 3. Worker node capacity summary
SELECT 
    node_name,
    status,
    cpu_cores,
    memory_gb,
    max_concurrent_jobs,
    current_job_count,
    ROUND((current_job_count::decimal / max_concurrent_jobs) * 100, 2) as utilization_percent
FROM worker_nodes
ORDER BY utilization_percent DESC;

-- 4. Job dependency relationships
SELECT 
    p.name as parent_job,
    d.name as dependent_job,
    jd.dependency_type
FROM job_dependencies jd
JOIN jobs p ON jd.parent_job_id = p.id
JOIN jobs d ON jd.dependent_job_id = d.id
ORDER BY p.name;

-- 5. Recent job execution performance
SELECT 
    j.name,
    jeh.execution_start,
    jeh.execution_end,
    EXTRACT(EPOCH FROM (jeh.execution_end - jeh.execution_start)) as duration_seconds,
    jeh.cpu_usage_percent,
    jeh.memory_usage_mb,
    jeh.status
FROM job_execution_history jeh
JOIN jobs j ON jeh.job_id = j.id
WHERE jeh.execution_start > CURRENT_TIMESTAMP - INTERVAL '24 hours'
ORDER BY jeh.execution_start DESC;

-- 6. Queue analysis
SELECT 
    j.name,
    jq.priority,
    jq.queue_time,
    jq.estimated_duration,
    jq.required_capabilities
FROM job_queue jq
JOIN jobs j ON jq.job_id = j.id
ORDER BY jq.priority DESC, jq.queue_time ASC;

-- 7. Scheduled jobs overview
SELECT 
    js.schedule_name,
    j.name as job_template,
    js.cron_expression,
    js.enabled,
    js.next_execution,
    js.last_execution
FROM job_schedules js
JOIN jobs j ON js.job_template_id = j.id
ORDER BY js.next_execution;

-- 8. Index usage statistics (PostgreSQL specific)
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- 9. Table size information
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) as size
FROM information_schema.tables 
WHERE table_schema = 'public'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC;

-- 10. Active connections
SELECT 
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start,
    state_change
FROM pg_stat_activity 
WHERE state = 'active' 
    AND pid <> pg_backend_pid()
ORDER BY query_start;
