import { useState, useEffect } from 'react';
import { jobService } from '../services/api';
import { Job } from '../types';

export const useJobs = () => {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchJobs = async () => {
    try {
      setLoading(true);
      const response = await jobService.getAllJobs();
      setJobs(response.data);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch jobs');
    } finally {
      setLoading(false);
    }
  };

  const createJob = async (jobData: any) => {
    try {
      const response = await jobService.createJob(jobData);
      setJobs(prev => [...prev, response.data]);
      return response.data;
    } catch (err: any) {
      throw new Error(err.message || 'Failed to create job');
    }
  };

  const deleteJob = async (id: string) => {
    try {
      await jobService.deleteJob(id);
      setJobs(prev => prev.filter(job => job.id !== id));
    } catch (err: any) {
      throw new Error(err.message || 'Failed to delete job');
    }
  };

  useEffect(() => {
    fetchJobs();
  }, []);

  return {
    jobs,
    loading,
    error,
    fetchJobs,
    createJob,
    deleteJob,
  };
};
