import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

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

interface JobStore {
  // State
  jobs: Job[];
  workers: Worker[];
  selectedJob: Job | null;
  selectedWorker: Worker | null;
  dashboardMetrics: DashboardMetrics | null;
  realtimeMetrics: MetricData[];
  isLoading: boolean;
  error: string | null;
  
  // WebSocket connection
  wsConnected: boolean;
  
  // Filters and pagination
  jobStatusFilter: string;
  workerStatusFilter: string;
  currentPage: number;
  pageSize: number;
  searchTerm: string;
  
  // Actions
  setJobs: (jobs: Job[]) => void;
  setWorkers: (workers: Worker[]) => void;
  setSelectedJob: (job: Job | null) => void;
  setSelectedWorker: (worker: Worker | null) => void;
  setDashboardMetrics: (metrics: DashboardMetrics) => void;
  addRealtimeMetric: (metric: MetricData) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  setWsConnected: (connected: boolean) => void;
  
  // Filters
  setJobStatusFilter: (status: string) => void;
  setWorkerStatusFilter: (status: string) => void;
  setCurrentPage: (page: number) => void;
  setSearchTerm: (term: string) => void;
  
  // Job operations
  updateJobStatus: (jobId: string, status: Job['status']) => void;
  addJob: (job: Job) => void;
  updateJob: (job: Job) => void;
  removeJob: (jobId: string) => void;
  
  // Worker operations
  updateWorkerStatus: (workerId: string, status: Worker['status']) => void;
  addWorker: (worker: Worker) => void;
  updateWorker: (worker: Worker) => void;
  removeWorker: (workerId: string) => void;
  
  // Utility
  getFilteredJobs: () => Job[];
  getFilteredWorkers: () => Worker[];
  clearFilters: () => void;
}

export const useJobStore = create<JobStore>()(
  devtools(
    (set, get) => ({
      // Initial state
      jobs: [],
      workers: [],
      selectedJob: null,
      selectedWorker: null,
      dashboardMetrics: null,
      realtimeMetrics: [],
      isLoading: false,
      error: null,
      wsConnected: false,
      
      // Filters
      jobStatusFilter: 'ALL',
      workerStatusFilter: 'ALL',
      currentPage: 1,
      pageSize: 10,
      searchTerm: '',
      
      // Basic setters
      setJobs: (jobs) => set({ jobs }),
      setWorkers: (workers) => set({ workers }),
      setSelectedJob: (job) => set({ selectedJob: job }),
      setSelectedWorker: (worker) => set({ selectedWorker: worker }),
      setDashboardMetrics: (metrics) => set({ dashboardMetrics: metrics }),
      
      addRealtimeMetric: (metric) => set((state) => ({
        realtimeMetrics: [...state.realtimeMetrics.slice(-99), metric] // Keep last 100 metrics
      })),
      
      setLoading: (loading) => set({ isLoading: loading }),
      setError: (error) => set({ error }),
      setWsConnected: (connected) => set({ wsConnected: connected }),
      
      // Filter setters
      setJobStatusFilter: (status) => set({ jobStatusFilter: status, currentPage: 1 }),
      setWorkerStatusFilter: (status) => set({ workerStatusFilter: status, currentPage: 1 }),
      setCurrentPage: (page) => set({ currentPage: page }),
      setSearchTerm: (term) => set({ searchTerm: term, currentPage: 1 }),
      
      // Job operations
      updateJobStatus: (jobId, status) => set((state) => ({
        jobs: state.jobs.map(job => 
          job.id === jobId ? { ...job, status } : job
        )
      })),
      
      addJob: (job) => set((state) => ({
        jobs: [job, ...state.jobs]
      })),
      
      updateJob: (updatedJob) => set((state) => ({
        jobs: state.jobs.map(job => 
          job.id === updatedJob.id ? updatedJob : job
        )
      })),
      
      removeJob: (jobId) => set((state) => ({
        jobs: state.jobs.filter(job => job.id !== jobId)
      })),
      
      // Worker operations
      updateWorkerStatus: (workerId, status) => set((state) => ({
        workers: state.workers.map(worker => 
          worker.workerId === workerId ? { ...worker, status } : worker
        )
      })),
      
      addWorker: (worker) => set((state) => ({
        workers: [worker, ...state.workers]
      })),
      
      updateWorker: (updatedWorker) => set((state) => ({
        workers: state.workers.map(worker => 
          worker.id === updatedWorker.id ? updatedWorker : worker
        )
      })),
      
      removeWorker: (workerId) => set((state) => ({
        workers: state.workers.filter(worker => worker.workerId !== workerId)
      })),
      
      // Utility functions
      getFilteredJobs: () => {
        const { jobs, jobStatusFilter, searchTerm } = get();
        return jobs.filter(job => {
          const matchesStatus = jobStatusFilter === 'ALL' || job.status === jobStatusFilter;
          const matchesSearch = !searchTerm || 
            job.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            job.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
            job.jobType.toLowerCase().includes(searchTerm.toLowerCase());
          return matchesStatus && matchesSearch;
        });
      },
      
      getFilteredWorkers: () => {
        const { workers, workerStatusFilter, searchTerm } = get();
        return workers.filter(worker => {
          const matchesStatus = workerStatusFilter === 'ALL' || worker.status === workerStatusFilter;
          const matchesSearch = !searchTerm || 
            worker.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            worker.workerId.toLowerCase().includes(searchTerm.toLowerCase()) ||
            worker.hostName.toLowerCase().includes(searchTerm.toLowerCase());
          return matchesStatus && matchesSearch;
        });
      },
      
      clearFilters: () => set({
        jobStatusFilter: 'ALL',
        workerStatusFilter: 'ALL',
        searchTerm: '',
        currentPage: 1
      })
    }),
    {
      name: 'job-scheduler-store'
    }
  )
);
