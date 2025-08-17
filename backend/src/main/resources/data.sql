-- Sample data for development
INSERT INTO jobs (id, name, description, status, created_at, updated_at) VALUES 
(1, 'Data Processing Job', 'Process customer data files', 'PENDING', NOW(), NOW()),
(2, 'Email Notification Job', 'Send daily newsletter to subscribers', 'COMPLETED', NOW(), NOW()),
(3, 'File Backup Job', 'Backup application logs to S3', 'FAILED', NOW(), NOW()),
(4, 'Database Cleanup Job', 'Clean up old temporary records', 'RUNNING', NOW(), NOW());
