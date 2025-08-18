import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Alert,
  Stack,
  LinearProgress,
} from '@mui/material';
import {
  TrendingUp,
  Speed,
  CheckCircle,
  Error,
  Computer,
} from '@mui/icons-material';
import { useDashboardMetrics } from '../../hooks/useApiQueries';

const MonitoringPage: React.FC = () => {
  const { data: metrics, isLoading, error } = useDashboardMetrics();

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error">
        Failed to load monitoring data. Please try again.
      </Alert>
    );
  }

  const MetricCard = ({ 
    title, 
    value, 
    icon, 
    color = 'primary',
    subtitle,
    progress
  }: {
    title: string;
    value: string | number;
    icon: React.ReactNode;
    color?: 'primary' | 'success' | 'error' | 'warning';
    subtitle?: string;
    progress?: number;
  }) => (
    <Card>
      <CardContent>
        <Box display="flex" alignItems="center" gap={2} mb={1}>
          <Box 
            sx={{ 
              p: 1, 
              borderRadius: 1, 
              backgroundColor: `${color}.light`,
              color: `${color}.contrastText`,
              display: 'flex',
              alignItems: 'center'
            }}
          >
            {icon}
          </Box>
          <Box flex={1}>
            <Typography variant="h4" color={`${color}.main`}>
              {value}
            </Typography>
            <Typography variant="h6" color="textSecondary">
              {title}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="textSecondary">
                {subtitle}
              </Typography>
            )}
          </Box>
        </Box>
        {progress !== undefined && (
          <LinearProgress 
            variant="determinate" 
            value={progress} 
            color={color}
            sx={{ height: 6, borderRadius: 3, mt: 1 }}
          />
        )}
      </CardContent>
    </Card>
  );

  return (
    <Box>
      <Typography variant="h4" component="h1" mb={3}>
        System Monitoring
      </Typography>

      <Stack spacing={3}>
        {/* Key Metrics Overview */}
        <Box>
          <Typography variant="h5" gutterBottom>
            Key Performance Indicators
          </Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <MetricCard
              title="Total Jobs"
              value={metrics?.totalJobs || 0}
              icon={<TrendingUp />}
              color="primary"
              subtitle="All time"
            />
            <MetricCard
              title="Running Jobs"
              value={metrics?.runningJobs || 0}
              icon={<Speed />}
              color="warning"
              subtitle="Currently executing"
            />
            <MetricCard
              title="Completed Jobs"
              value={metrics?.completedJobs || 0}
              icon={<CheckCircle />}
              color="success"
              subtitle="Successfully finished"
            />
            <MetricCard
              title="Failed Jobs"
              value={metrics?.failedJobs || 0}
              icon={<Error />}
              color="error"
              subtitle="Encountered errors"
            />
          </Stack>
        </Box>

        {/* Worker Metrics */}
        <Box>
          <Typography variant="h5" gutterBottom>
            Worker Status
          </Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <MetricCard
              title="Active Workers"
              value={metrics?.activeWorkers || 0}
              icon={<Computer />}
              color="success"
              subtitle="Online and ready"
            />
            <MetricCard
              title="Queue Depth"
              value={metrics?.queueDepth || 0}
              icon={<TrendingUp />}
              color="primary"
              subtitle="Jobs waiting"
            />
            <MetricCard
              title="Success Rate"
              value={metrics?.successRate ? `${metrics.successRate.toFixed(1)}%` : '0%'}
              icon={<CheckCircle />}
              color="success"
              subtitle="Job completion rate"
              progress={metrics?.successRate || 0}
            />
            <MetricCard
              title="Avg Execution Time"
              value={metrics?.averageExecutionTime ? `${metrics.averageExecutionTime.toFixed(2)}s` : '0s'}
              icon={<Speed />}
              color="primary"
              subtitle="Per job average"
            />
          </Stack>
        </Box>

        {/* System Health Summary */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              System Health Summary
            </Typography>
            <Stack spacing={2}>
              <Box>
                <Typography variant="body1" gutterBottom>
                  <strong>Overall Status:</strong> {metrics?.activeWorkers ? 'Healthy' : 'No Active Workers'}
                </Typography>
              </Box>
              
              <Box>
                <Typography variant="body2" color="textSecondary" gutterBottom>
                  Job Processing Efficiency
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={metrics?.successRate || 0} 
                  color={
                    (metrics?.successRate || 0) >= 90 ? 'success' :
                    (metrics?.successRate || 0) >= 70 ? 'warning' : 'error'
                  }
                  sx={{ height: 10, borderRadius: 5 }}
                />
                <Typography variant="caption" color="textSecondary">
                  {metrics?.successRate ? `${metrics.successRate.toFixed(1)}%` : '0%'} success rate
                </Typography>
              </Box>

              <Stack direction="row" spacing={4}>
                <Box>
                  <Typography variant="h6" color="primary">
                    {((metrics?.completedJobs || 0) + (metrics?.runningJobs || 0) + (metrics?.queueDepth || 0)).toLocaleString()}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Total Jobs in System
                  </Typography>
                </Box>
                
                <Box>
                  <Typography variant="h6" color="success.main">
                    {metrics?.activeWorkers || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Workers Online
                  </Typography>
                </Box>
                
                <Box>
                  <Typography variant="h6" color="warning.main">
                    {metrics?.queueDepth || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Jobs Queued
                  </Typography>
                </Box>
              </Stack>

              {metrics?.queueDepth && metrics.queueDepth > 10 && (
                <Alert severity="warning">
                  High queue depth detected. Consider adding more workers to improve processing speed.
                </Alert>
              )}

              {metrics?.successRate && metrics.successRate < 80 && (
                <Alert severity="error">
                  Low success rate detected. Please check worker health and job configurations.
                </Alert>
              )}

              {(!metrics?.activeWorkers || metrics.activeWorkers === 0) && (
                <Alert severity="error">
                  No active workers found. System cannot process jobs until workers are online.
                </Alert>
              )}
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
};

export default MonitoringPage;
