export interface Job {
  id: string;
  name: string;
  description?: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'SCHEDULED';
  priority: number;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  assignedWorkerId?: string;
  jobType: string;
  maxRetries: number;
  retryCount: number;
  errorMessage?: string;
  estimatedDuration?: number;
  dependencies?: string[];
  tags?: string[];
}

export interface Worker {
  id: string;
  workerId: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'BUSY' | 'FAILED';
  hostName: string;
  hostAddress: string;
  port: number;
  maxConcurrentJobs: number;
  currentJobCount: number;
  totalJobsProcessed: number;
  totalJobsSuccessful: number;
  lastHeartbeat: string;
  availableCapacity: number;
  capabilities?: string[];
  tags?: string[];
}

export interface MetricData {
  timestamp: string;
  value: number;
  label?: string;
}

export interface DashboardMetrics {
  totalJobs: number;
  runningJobs: number;
  completedJobs: number;
  failedJobs: number;
  activeWorkers: number;
  averageExecutionTime: number;
  queueDepth: number;
  successRate: number;
}

export type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'SCHEDULED';

export interface CreateJobRequest {
  name: string;
  description?: string;
  scheduledAt?: string;
  parameters?: Record<string, any>;
}

export interface JobListResponse {
  jobs: Job[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  success: boolean;
}
