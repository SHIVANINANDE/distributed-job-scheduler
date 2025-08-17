-- Sample data for Distributed Job Scheduler

-- Insert sample worker nodes
INSERT INTO worker_nodes (node_name, node_address, node_port, status, cpu_cores, memory_gb, max_concurrent_jobs, capabilities) VALUES
('worker-node-1', '192.168.1.10', 8080, 'ACTIVE', 4, 8, 3, ARRAY['data-processing', 'batch-job', 'api-call']),
('worker-node-2', '192.168.1.11', 8080, 'ACTIVE', 8, 16, 5, ARRAY['data-processing', 'ml-training', 'heavy-compute']),
('worker-node-3', '192.168.1.12', 8080, 'ACTIVE', 2, 4, 2, ARRAY['api-call', 'notification', 'light-compute']),
('worker-node-4', '192.168.1.13', 8080, 'MAINTENANCE', 4, 8, 3, ARRAY['data-processing', 'batch-job']);

-- Insert sample jobs
INSERT INTO jobs (name, description, job_type, status, parameters, max_retries, priority, worker_node_id, created_by, tags, timeout_seconds) VALUES
('Daily Data Backup', 'Backup database to cloud storage', 'data-processing', 'COMPLETED', '{"source": "main_db", "destination": "s3://backup-bucket"}', 3, 5, 1, 'admin', ARRAY['backup', 'daily'], 7200),
('User Email Campaign', 'Send promotional emails to users', 'notification', 'PENDING', '{"template": "promo_2024", "segment": "active_users"}', 2, 3, 3, 'marketing', ARRAY['email', 'campaign'], 3600),
('ML Model Training', 'Train recommendation model with latest data', 'ml-training', 'RUNNING', '{"model_type": "collaborative_filtering", "dataset": "user_interactions"}', 1, 8, 2, 'data-science', ARRAY['ml', 'training'], 14400),
('Report Generation', 'Generate monthly sales report', 'batch-job', 'SCHEDULED', '{"report_type": "sales", "period": "monthly"}', 3, 4, 1, 'finance', ARRAY['report', 'monthly'], 5400),
('API Health Check', 'Check health of external APIs', 'api-call', 'FAILED', '{"endpoints": ["api1.example.com", "api2.example.com"]}', 5, 2, 3, 'ops', ARRAY['monitoring', 'health'], 300);

-- Insert job dependencies
INSERT INTO job_dependencies (parent_job_id, dependent_job_id, dependency_type) VALUES
(1, 4, 'SEQUENTIAL'),  -- Backup must complete before report generation
(3, 5, 'PARALLEL');    -- ML training and API health check can run in parallel

-- Insert job execution history
INSERT INTO job_execution_history (job_id, worker_node_id, execution_start, execution_end, status, cpu_usage_percent, memory_usage_mb) VALUES
(1, 1, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'COMPLETED', 45.2, 1024),
(5, 3, CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '25 minutes', 'FAILED', 15.8, 256);

-- Insert job queue entries for pending jobs
INSERT INTO job_queue (job_id, priority, estimated_duration, required_capabilities) VALUES
(2, 3, 3600, ARRAY['notification']),
(4, 4, 5400, ARRAY['batch-job']);

-- Insert job schedules for recurring jobs
INSERT INTO job_schedules (job_template_id, schedule_name, cron_expression, enabled, next_execution) VALUES
(1, 'Daily Backup Schedule', '0 2 * * *', true, CURRENT_DATE + INTERVAL '1 day' + INTERVAL '2 hours'),
(4, 'Monthly Report Schedule', '0 9 1 * *', true, DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month' + INTERVAL '9 hours');
