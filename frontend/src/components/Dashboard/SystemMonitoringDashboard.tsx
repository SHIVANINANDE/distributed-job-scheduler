import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Paper,
  Stack,
  CircularProgress,
  Alert,
  Chip,
  LinearProgress,
} from '@mui/material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  BarChart,
  Bar,
} from 'recharts';
import {
  CheckCircle as HealthyIcon,
  Warning as DegradedIcon,
  Error as UnhealthyIcon,
  Computer as WorkerIcon,
} from '@mui/icons-material';

interface Worker {
  id: string;
  name: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  cpuUsage: number;
  memoryUsage: number;
  activeJobs: number;
  lastSeen: string;
}

interface SystemAlert {
  title: string;
  message: string;
  severity: 'error' | 'warning' | 'info' | 'success';
  timestamp: string;
}

const SystemMonitoringDashboard: React.FC = () => {
  const [workers, setWorkers] = useState<Worker[]>([
    {
      id: '1',
      name: 'worker-node-01',
      status: 'healthy',
      cpuUsage: 65,
      memoryUsage: 42,
      activeJobs: 3,
      lastSeen: new Date().toISOString(),
    },
    {
      id: '2',
      name: 'worker-node-02',
      status: 'degraded',
      cpuUsage: 89,
      memoryUsage: 78,
      activeJobs: 5,
      lastSeen: new Date(Date.now() - 30000).toISOString(),
    },
    {
      id: '3',
      name: 'worker-node-03',
      status: 'healthy',
      cpuUsage: 34,
      memoryUsage: 51,
      activeJobs: 2,
      lastSeen: new Date().toISOString(),
    },
    {
      id: '4',
      name: 'worker-node-04',
      status: 'unhealthy',
      cpuUsage: 0,
      memoryUsage: 0,
      activeJobs: 0,
      lastSeen: new Date(Date.now() - 300000).toISOString(),
    },
  ]);

  const [alerts, setAlerts] = useState<SystemAlert[]>([
    {
      title: 'High CPU Usage',
      message: 'Worker node-02 is experiencing high CPU usage (89%)',
      severity: 'warning',
      timestamp: new Date().toISOString(),
    },
    {
      title: 'Worker Offline',
      message: 'Worker node-04 has been offline for 5 minutes',
      severity: 'error',
      timestamp: new Date(Date.now() - 300000).toISOString(),
    },
  ]);

  const [performanceData] = useState([
    { time: '14:00', cpu: 45, memory: 62, jobs: 12 },
    { time: '14:05', cpu: 52, memory: 58, jobs: 15 },
    { time: '14:10', cpu: 48, memory: 61, jobs: 18 },
    { time: '14:15', cpu: 61, memory: 65, jobs: 14 },
    { time: '14:20', cpu: 58, memory: 63, jobs: 16 },
    { time: '14:25', cpu: 73, memory: 71, jobs: 22 },
    { time: '14:30', cpu: 67, memory: 68, jobs: 19 },
  ]);

  const getWorkerStatusColor = (status: string): string => {
    switch (status) {
      case 'healthy': return '#4caf50';
      case 'degraded': return '#ff9800';
      case 'unhealthy': return '#f44336';
      default: return '#9e9e9e';
    }
  };

  const getWorkerStatusIcon = (status: string) => {
    switch (status) {
      case 'healthy': return <HealthyIcon sx={{ color: '#4caf50' }} />;
      case 'degraded': return <DegradedIcon sx={{ color: '#ff9800' }} />;
      case 'unhealthy': return <UnhealthyIcon sx={{ color: '#f44336' }} />;
      default: return <WorkerIcon sx={{ color: '#9e9e9e' }} />;
    }
  };

  // Simulate real-time updates
  useEffect(() => {
    const interval = setInterval(() => {
      setWorkers(prev => prev.map(worker => ({
        ...worker,
        cpuUsage: Math.max(0, Math.min(100, worker.cpuUsage + (Math.random() - 0.5) * 10)),
        memoryUsage: Math.max(0, Math.min(100, worker.memoryUsage + (Math.random() - 0.5) * 5)),
        lastSeen: worker.status !== 'unhealthy' ? new Date().toISOString() : worker.lastSeen,
      })));
    }, 5000);

    return () => clearInterval(interval);
  }, []);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        System Monitoring Dashboard
      </Typography>

      <Stack spacing={3}>
        {/* System Alerts */}
        <Box>
          {alerts.map((alert, index) => (
            <Alert
              key={index}
              severity={alert.severity}
              sx={{ mb: 1 }}
              onClose={alert.severity !== 'error' ? () => {
                setAlerts(prev => prev.filter((_, i) => i !== index));
              } : undefined}
            >
              <Typography variant="body2">
                <strong>{alert.title}:</strong> {alert.message}
              </Typography>
            </Alert>
          ))}
        </Box>

        {/* Main Content */}
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 3 }}>
          {/* Worker Status Grid */}
          <Box sx={{ flex: 2 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Worker Nodes Status ({workers.length} workers)
                </Typography>
                <Box sx={{ 
                  display: 'grid', 
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(2, 1fr)' }, 
                  gap: 2 
                }}>
                  {workers.map((worker) => (
                    <Paper
                      key={worker.id}
                      elevation={2}
                      sx={{
                        p: 2,
                        borderLeft: `4px solid ${getWorkerStatusColor(worker.status)}`,
                        background: worker.status === 'healthy' 
                          ? 'rgba(76, 175, 80, 0.05)' 
                          : worker.status === 'degraded'
                          ? 'rgba(255, 152, 0, 0.05)'
                          : 'rgba(244, 67, 54, 0.05)',
                      }}
                    >
                      <Stack spacing={1}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            {getWorkerStatusIcon(worker.status)}
                            <Typography variant="subtitle1" fontWeight="bold">
                              {worker.name}
                            </Typography>
                          </Box>
                          <Chip
                            label={worker.status}
                            size="small"
                            sx={{
                              backgroundColor: getWorkerStatusColor(worker.status),
                              color: 'white',
                              fontWeight: 'bold',
                            }}
                          />
                        </Stack>
                        
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            CPU Usage
                          </Typography>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <LinearProgress
                              variant="determinate"
                              value={worker.cpuUsage}
                              sx={{ 
                                flexGrow: 1, 
                                height: 8, 
                                borderRadius: 4,
                                '& .MuiLinearProgress-bar': {
                                  backgroundColor: worker.cpuUsage > 80 ? '#f44336' : worker.cpuUsage > 60 ? '#ff9800' : '#4caf50',
                                }
                              }}
                            />
                            <Typography variant="body2" color="text.secondary" sx={{ minWidth: '35px' }}>
                              {Math.round(worker.cpuUsage)}%
                            </Typography>
                          </Box>
                        </Box>
                        
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            Memory Usage
                          </Typography>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <LinearProgress
                              variant="determinate"
                              value={worker.memoryUsage}
                              sx={{ 
                                flexGrow: 1, 
                                height: 8, 
                                borderRadius: 4,
                                '& .MuiLinearProgress-bar': {
                                  backgroundColor: worker.memoryUsage > 80 ? '#f44336' : worker.memoryUsage > 60 ? '#ff9800' : '#4caf50',
                                }
                              }}
                            />
                            <Typography variant="body2" color="text.secondary" sx={{ minWidth: '35px' }}>
                              {Math.round(worker.memoryUsage)}%
                            </Typography>
                          </Box>
                        </Box>
                        
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography variant="body2" color="text.secondary">
                            Jobs: {worker.activeJobs} active
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {new Date(worker.lastSeen).toLocaleTimeString()}
                          </Typography>
                        </Box>
                      </Stack>
                    </Paper>
                  ))}
                </Box>
                
                {/* Priority Queue Status */}
                <Paper elevation={1} sx={{ p: 2, mt: 2 }}>
                  <Typography variant="h6" gutterBottom>
                    Priority Queue Status
                  </Typography>
                  <Stack direction="row" spacing={3}>
                    <Box>
                      <Typography variant="h4" color="primary">24</Typography>
                      <Typography variant="body2" color="text.secondary">High Priority</Typography>
                    </Box>
                    <Box>
                      <Typography variant="h4" color="warning.main">12</Typography>
                      <Typography variant="body2" color="text.secondary">Medium Priority</Typography>
                    </Box>
                    <Box>
                      <Typography variant="h4" color="info.main">8</Typography>
                      <Typography variant="body2" color="text.secondary">Low Priority</Typography>
                    </Box>
                  </Stack>
                </Paper>
              </CardContent>
            </Card>
          </Box>

          {/* System Overview */}
          <Box sx={{ flex: 1 }}>
            <Card sx={{ height: 'fit-content' }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Job Status Overview
                </Typography>
                <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                  <PieChart width={280} height={200}>
                    <Pie
                      data={[
                        { name: 'Running', value: 45, fill: '#4caf50' },
                        { name: 'Queued', value: 23, fill: '#ff9800' },
                        { name: 'Failed', value: 8, fill: '#f44336' },
                        { name: 'Completed', value: 124, fill: '#2196f3' },
                      ]}
                      cx={140}
                      cy={100}
                      outerRadius={70}
                      dataKey="value"
                      label={({ name, percent }) => `${name}: ${(percent! * 100).toFixed(0)}%`}
                    />
                    <Tooltip />
                  </PieChart>
                </Box>
              </CardContent>
            </Card>
          </Box>
        </Box>

        {/* Performance Metrics Row */}
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', lg: 'row' }, gap: 3 }}>
          {/* System Performance */}
          <Box sx={{ flex: 1 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  System Performance (Last Hour)
                </Typography>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={performanceData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="cpu" stroke="#8884d8" strokeWidth={2} name="CPU %" />
                    <Line type="monotone" dataKey="memory" stroke="#82ca9d" strokeWidth={2} name="Memory %" />
                    <Line type="monotone" dataKey="jobs" stroke="#ffc658" strokeWidth={2} name="Active Jobs" />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Box>

          {/* Job Throughput */}
          <Box sx={{ flex: 1 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Weekly Job Throughput
                </Typography>
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={[
                    { name: 'Mon', completed: 24, failed: 2 },
                    { name: 'Tue', completed: 31, failed: 1 },
                    { name: 'Wed', completed: 28, failed: 3 },
                    { name: 'Thu', completed: 35, failed: 1 },
                    { name: 'Fri', completed: 42, failed: 2 },
                    { name: 'Sat', completed: 18, failed: 0 },
                    { name: 'Sun', completed: 15, failed: 1 },
                  ]}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="completed" fill="#4caf50" name="Completed" />
                    <Bar dataKey="failed" fill="#f44336" name="Failed" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Box>
        </Box>

        {/* Recent System Events */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Recent System Events
            </Typography>
            <Stack spacing={2}>
              {[
                { time: '14:32', event: 'Worker node-03 came online', type: 'info' as const },
                { time: '14:28', event: 'High priority job batch started', type: 'success' as const },
                { time: '14:25', event: 'Worker node-01 showing high memory usage', type: 'warning' as const },
                { time: '14:20', event: 'Job queue cleared', type: 'success' as const },
                { time: '14:15', event: 'System backup completed', type: 'info' as const },
                { time: '14:10', event: 'Connection timeout to worker node-04', type: 'error' as const },
              ].map((event, index) => (
                <Paper key={index} elevation={1} sx={{ p: 2 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2">{event.event}</Typography>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="caption" color="text.secondary">
                        {event.time}
                      </Typography>
                      <Chip
                        label={event.type}
                        size="small"
                        color={
                          event.type === 'success' ? 'success' :
                          event.type === 'warning' ? 'warning' :
                          event.type === 'error' ? 'error' : 'default'
                        }
                      />
                    </Stack>
                  </Stack>
                </Paper>
              ))}
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
};

export default SystemMonitoringDashboard;
