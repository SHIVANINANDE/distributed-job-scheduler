import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { jobService, workerService, monitoringService } from '../services/api';
import { useJobStore } from '../store/useJobStore';
import { Job, Worker } from '../store/useJobStore';

// Query Keys
export const queryKeys = {
  jobs: ['jobs'] as const,
  job: (id: string) => ['jobs', id] as const,
  jobsByStatus: (status: string) => ['jobs', 'status', status] as const,
  workers: ['workers'] as const,
  worker: (id: string) => ['workers', id] as const,
  workerJobs: (workerId: string) => ['workers', workerId, 'jobs'] as const,
  dashboard: ['dashboard'] as const,
  jobMetrics: ['metrics', 'jobs'] as const,
  workerMetrics: ['metrics', 'workers'] as const,
  systemHealth: ['health'] as const,
  jobTrail: (jobId: string) => ['audit', 'job', jobId] as const,
  benchmarks: ['benchmarks'] as const,
};

// Job Hooks
export const useJobs = () => {
  return useQuery({
    queryKey: queryKeys.jobs,
    queryFn: async () => {
      const response = await jobService.getAllJobs();
      return response.data;
    },
    staleTime: 30000, // 30 seconds
    refetchInterval: 30000, // Refetch every 30 seconds
  });
};

export const useJob = (id: string) => {
  return useQuery({
    queryKey: queryKeys.job(id),
    queryFn: async () => {
      const response = await jobService.getJob(id);
      return response.data;
    },
    enabled: !!id,
  });
};

export const useJobsByStatus = (status: string) => {
  return useQuery({
    queryKey: queryKeys.jobsByStatus(status),
    queryFn: async () => {
      const response = await jobService.getJobsByStatus(status);
      return response.data;
    },
    enabled: !!status && status !== 'ALL',
  });
};

// Job Mutations
export const useCreateJob = () => {
  const queryClient = useQueryClient();
  const addJob = useJobStore((state) => state.addJob);

  return useMutation({
    mutationFn: (data: Partial<Job>) => jobService.createJob(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      addJob(response.data);
    },
  });
};

export const useUpdateJob = () => {
  const queryClient = useQueryClient();
  const updateJob = useJobStore((state) => state.updateJob);

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Job> }) =>
      jobService.updateJob(id, data),
    onSuccess: (response, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      queryClient.invalidateQueries({ queryKey: queryKeys.job(variables.id) });
      updateJob(response.data);
    },
  });
};

export const useDeleteJob = () => {
  const queryClient = useQueryClient();
  const removeJob = useJobStore((state) => state.removeJob);

  return useMutation({
    mutationFn: (id: string) => jobService.deleteJob(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      removeJob(id);
    },
  });
};

export const useStartJob = () => {
  const queryClient = useQueryClient();
  const updateJobStatus = useJobStore((state) => state.updateJobStatus);

  return useMutation({
    mutationFn: (id: string) => jobService.startJob(id),
    onSuccess: (response, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      queryClient.invalidateQueries({ queryKey: queryKeys.job(id) });
      updateJobStatus(id, 'RUNNING');
    },
  });
};

export const useCancelJob = () => {
  const queryClient = useQueryClient();
  const updateJobStatus = useJobStore((state) => state.updateJobStatus);

  return useMutation({
    mutationFn: (id: string) => jobService.cancelJob(id),
    onSuccess: (response, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      queryClient.invalidateQueries({ queryKey: queryKeys.job(id) });
      updateJobStatus(id, 'CANCELLED');
    },
  });
};

export const useRetryJob = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => jobService.retryJob(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.jobs });
      queryClient.invalidateQueries({ queryKey: queryKeys.job(id) });
    },
  });
};

// Worker Hooks
export const useWorkers = () => {
  return useQuery({
    queryKey: queryKeys.workers,
    queryFn: async () => {
      const response = await workerService.getAllWorkers();
      return response.data;
    },
    staleTime: 30000,
    refetchInterval: 30000,
  });
};

export const useWorker = (workerId: string) => {
  return useQuery({
    queryKey: queryKeys.worker(workerId),
    queryFn: async () => {
      const response = await workerService.getWorker(workerId);
      return response.data;
    },
    enabled: !!workerId,
  });
};

export const useWorkerJobs = (workerId: string) => {
  return useQuery({
    queryKey: queryKeys.workerJobs(workerId),
    queryFn: async () => {
      const response = await workerService.getWorkerJobs(workerId);
      return response.data;
    },
    enabled: !!workerId,
  });
};

// Worker Mutations
export const useRegisterWorker = () => {
  const queryClient = useQueryClient();
  const addWorker = useJobStore((state) => state.addWorker);

  return useMutation({
    mutationFn: (data: Partial<Worker>) => workerService.registerWorker(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.workers });
      addWorker(response.data);
    },
  });
};

export const useUpdateWorker = () => {
  const queryClient = useQueryClient();
  const updateWorker = useJobStore((state) => state.updateWorker);

  return useMutation({
    mutationFn: ({ workerId, data }: { workerId: string; data: Partial<Worker> }) =>
      workerService.updateWorker(workerId, data),
    onSuccess: (response, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.workers });
      queryClient.invalidateQueries({ queryKey: queryKeys.worker(variables.workerId) });
      updateWorker(response.data);
    },
  });
};

export const useDeregisterWorker = () => {
  const queryClient = useQueryClient();
  const removeWorker = useJobStore((state) => state.removeWorker);

  return useMutation({
    mutationFn: (workerId: string) => workerService.deregisterWorker(workerId),
    onSuccess: (_, workerId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.workers });
      removeWorker(workerId);
    },
  });
};

// Monitoring Hooks
export const useDashboardMetrics = () => {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: async () => {
      const response = await monitoringService.getDashboardMetrics();
      return response.data;
    },
    staleTime: 10000, // 10 seconds
    refetchInterval: 15000, // Refetch every 15 seconds
  });
};

export const useJobMetrics = () => {
  return useQuery({
    queryKey: queryKeys.jobMetrics,
    queryFn: async () => {
      const response = await monitoringService.getJobMetrics();
      return response.data;
    },
    staleTime: 30000,
    refetchInterval: 30000,
  });
};

export const useWorkerMetrics = () => {
  return useQuery({
    queryKey: queryKeys.workerMetrics,
    queryFn: async () => {
      const response = await monitoringService.getWorkerMetrics();
      return response.data;
    },
    staleTime: 30000,
    refetchInterval: 30000,
  });
};

export const useSystemHealth = () => {
  return useQuery({
    queryKey: queryKeys.systemHealth,
    queryFn: async () => {
      const response = await monitoringService.getSystemHealth();
      return response.data;
    },
    staleTime: 10000,
    refetchInterval: 30000,
  });
};

export const useJobExecutionTrail = (jobId: string) => {
  return useQuery({
    queryKey: queryKeys.jobTrail(jobId),
    queryFn: async () => {
      const response = await monitoringService.getJobExecutionTrail(jobId);
      return response.data;
    },
    enabled: !!jobId,
  });
};

export const usePerformanceBenchmarks = () => {
  return useQuery({
    queryKey: queryKeys.benchmarks,
    queryFn: async () => {
      const response = await monitoringService.getPerformanceBenchmarks();
      return response.data;
    },
    staleTime: 60000, // 1 minute
    refetchInterval: 120000, // Refetch every 2 minutes
  });
};
