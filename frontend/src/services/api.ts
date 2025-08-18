import axios from 'axios';
import { Job, Worker, DashboardMetrics } from '../store/useJobStore';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for adding auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for handling errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized access
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Job Service
export const jobService = {
  getAllJobs: (): Promise<{ data: Job[] }> => api.get('/jobs'),
  getJob: (id: string): Promise<{ data: Job }> => api.get(`/jobs/${id}`),
  createJob: (data: Partial<Job>): Promise<{ data: Job }> => api.post('/jobs', data),
  updateJob: (id: string, data: Partial<Job>): Promise<{ data: Job }> => api.put(`/jobs/${id}`, data),
  deleteJob: (id: string): Promise<void> => api.delete(`/jobs/${id}`),
  startJob: (id: string): Promise<{ data: Job }> => api.post(`/jobs/${id}/start`),
  cancelJob: (id: string): Promise<{ data: Job }> => api.post(`/jobs/${id}/cancel`),
  retryJob: (id: string): Promise<{ data: Job }> => api.post(`/jobs/${id}/retry`),
  getJobsByStatus: (status: string): Promise<{ data: Job[] }> => api.get(`/jobs?status=${status}`),
  healthCheck: () => api.get('/jobs/health'),
};

// Worker Service
export const workerService = {
  getAllWorkers: (): Promise<{ data: Worker[] }> => api.get('/workers'),
  getWorker: (workerId: string): Promise<{ data: Worker }> => api.get(`/workers/${workerId}`),
  registerWorker: (data: Partial<Worker>): Promise<{ data: Worker }> => api.post('/workers', data),
  updateWorker: (workerId: string, data: Partial<Worker>): Promise<{ data: Worker }> => api.put(`/workers/${workerId}`, data),
  deregisterWorker: (workerId: string): Promise<void> => api.delete(`/workers/${workerId}`),
  getWorkerJobs: (workerId: string): Promise<{ data: Job[] }> => api.get(`/workers/${workerId}/jobs`),
  sendHeartbeat: (workerId: string, data: any): Promise<void> => api.post(`/workers/${workerId}/heartbeat`, data),
};

// Monitoring Service
export const monitoringService = {
  getDashboardMetrics: (): Promise<{ data: DashboardMetrics }> => api.get('/monitoring/dashboard'),
  getJobMetrics: (): Promise<{ data: any }> => api.get('/monitoring/jobs'),
  getWorkerMetrics: (): Promise<{ data: any }> => api.get('/monitoring/workers'),
  getSystemHealth: (): Promise<{ data: any }> => api.get('/monitoring/health'),
  getJobExecutionTrail: (jobId: string): Promise<{ data: any }> => api.get(`/monitoring/audit/job/${jobId}`),
  getPerformanceBenchmarks: (): Promise<{ data: any }> => api.get('/monitoring/benchmarks'),
};

// Batch Job Service
export const batchService = {
  createBatchJob: (data: any): Promise<{ data: any }> => api.post('/batch', data),
  getBatchJob: (batchId: string): Promise<{ data: any }> => api.get(`/batch/${batchId}`),
  getBatchStatus: (batchId: string): Promise<{ data: any }> => api.get(`/batch/${batchId}/status`),
  cancelBatch: (batchId: string): Promise<void> => api.post(`/batch/${batchId}/cancel`),
};

// Scheduling Service
export const schedulingService = {
  createScheduledJob: (data: any): Promise<{ data: any }> => api.post('/scheduling/schedule', data),
  updateSchedule: (scheduleId: string, data: any): Promise<{ data: any }> => api.put(`/scheduling/${scheduleId}`, data),
  deleteSchedule: (scheduleId: string): Promise<void> => api.delete(`/scheduling/${scheduleId}`),
  getSchedules: (): Promise<{ data: any[] }> => api.get('/scheduling'),
  pauseSchedule: (scheduleId: string): Promise<void> => api.post(`/scheduling/${scheduleId}/pause`),
  resumeSchedule: (scheduleId: string): Promise<void> => api.post(`/scheduling/${scheduleId}/resume`),
};

export default api;
