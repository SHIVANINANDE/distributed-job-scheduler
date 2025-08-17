export interface Job {
  id: string;
  name: string;
  description?: string;
  status: JobStatus;
  createdAt: string;
  updatedAt: string;
  scheduledAt?: string;
  completedAt?: string;
  errorMessage?: string;
}

export enum JobStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

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
