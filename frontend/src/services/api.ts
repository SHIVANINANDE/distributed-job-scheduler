import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
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

export const jobService = {
  getAllJobs: () => api.get('/jobs'),
  getJob: (id: string) => api.get(`/jobs/${id}`),
  createJob: (data: any) => api.post('/jobs', data),
  updateJob: (id: string, data: any) => api.put(`/jobs/${id}`, data),
  deleteJob: (id: string) => api.delete(`/jobs/${id}`),
  healthCheck: () => api.get('/jobs/health'),
};

export default api;
