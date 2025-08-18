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
  Queue,
} from '@mui/icons-material';
import { useDashboardMetrics } from '../hooks/useApiQueries';

const DashboardPage: React.FC = () => {
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
        Failed to load dashboard data. Please try again.
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
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Box display="flex" alignItems="flex-start" gap={2} mb={2}>
          <Box 
            sx={{ 
              p: 1.5, 
              borderRadius: 2, 
              backgroundColor: `${color}.light`,
              color: `${color}.contrastText`,
              display: 'flex',
              alignItems: 'center'
            }}
          >
            {icon}
          </Box>
          <Box flex={1}>
            <Typography variant="h3" color={`${color}.main`} fontWeight="bold">
              {value}
            </Typography>
            <Typography variant="h6" color="textPrimary" gutterBottom>
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
          <Box>
            <Box display="flex" justifyContent="space-between" mb={1}>
              <Typography variant="caption" color="textSecondary">
                Progress
              </Typography>
              <Typography variant="caption">
                {progress.toFixed(1)}%
              </Typography>
            </Box>
            <LinearProgress 
              variant="determinate" 
              value={progress} 
              color={color}
              sx={{ height: 8, borderRadius: 4 }}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>
        Dashboard
      </Typography>
      <Typography variant="body1" color="textSecondary" paragraph>
        Real-time overview of your distributed job scheduler system
      </Typography>

      <Stack spacing={4}>
        {/* Key Metrics */}
        <Box>
          <Typography variant="h5" gutterBottom>
            System Overview
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
            <MetricCard
              title="Total Jobs"
              value={metrics?.totalJobs?.toLocaleString() || '0'}
              icon={<TrendingUp fontSize="large" />}
              color="primary"
              subtitle="All time"
            />
            <MetricCard
              title="Active Workers"
              value={metrics?.activeWorkers || 0}
              icon={<Computer fontSize="large" />}
              color="success"
              subtitle="Currently online"
            />
            <MetricCard
              title="Queue Depth"
              value={metrics?.queueDepth || 0}
              icon={<Queue fontSize="large" />}
              color="warning"
              subtitle="Jobs waiting"
            />
            <MetricCard
              title="Success Rate"
              value={metrics?.successRate ? `${metrics.successRate.toFixed(1)}%` : '0%'}
              icon={<CheckCircle fontSize="large" />}
              color="success"
              subtitle="Job completion rate"
              progress={metrics?.successRate || 0}
            />
          </Stack>
        </Box>

        {/* Current Activity */}
        <Box>
          <Typography variant="h5" gutterBottom>
            Current Activity
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
            <MetricCard
              title="Running Jobs"
              value={metrics?.runningJobs || 0}
              icon={<Speed fontSize="large" />}
              color="warning"
              subtitle="Currently executing"
            />
            <MetricCard
              title="Completed Jobs"
              value={metrics?.completedJobs?.toLocaleString() || '0'}
              icon={<CheckCircle fontSize="large" />}
              color="success"
              subtitle="Successfully finished"
            />
            <MetricCard
              title="Failed Jobs"
              value={metrics?.failedJobs || 0}
              icon={<Error fontSize="large" />}
              color="error"
              subtitle="Encountered errors"
            />
            <MetricCard
              title="Avg Execution"
              value={metrics?.averageExecutionTime ? `${metrics.averageExecutionTime.toFixed(1)}s` : '0s'}
              icon={<Speed fontSize="large" />}
              color="primary"
              subtitle="Per job average"
            />
          </Stack>
        </Box>

        {/* System Status */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              System Health
            </Typography>
            
            <Stack spacing={3}>
              <Box>
                <Typography variant="body1" gutterBottom>
                  <strong>Status:</strong> {
                    (metrics?.activeWorkers || 0) > 0 ? (
                      <span style={{ color: 'green' }}>System Operational</span>
                    ) : (
                      <span style={{ color: 'red' }}>No Active Workers</span>
                    )
                  }
                </Typography>
              </Box>

              {/* Performance Indicators */}
              <Stack spacing={2}>
                <Box>
                  <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="body2" color="textSecondary">
                      Success Rate
                    </Typography>
                    <Typography variant="body2">
                      {metrics?.successRate ? `${metrics.successRate.toFixed(1)}%` : '0%'}
                    </Typography>
                  </Box>
                  <LinearProgress 
                    variant="determinate" 
                    value={metrics?.successRate || 0} 
                    color={
                      (metrics?.successRate || 0) >= 90 ? 'success' :
                      (metrics?.successRate || 0) >= 70 ? 'warning' : 'error'
                    }
                    sx={{ height: 10, borderRadius: 5 }}
                  />
                </Box>

                <Stack direction="row" spacing={4}>
                  <Box textAlign="center">
                    <Typography variant="h5" color="primary">
                      {((metrics?.totalJobs || 0)).toLocaleString()}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Total Jobs
                    </Typography>
                  </Box>
                  
                  <Box textAlign="center">
                    <Typography variant="h5" color="success.main">
                      {metrics?.activeWorkers || 0}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Workers Online
                    </Typography>
                  </Box>
                  
                  <Box textAlign="center">
                    <Typography variant="h5" color="warning.main">
                      {metrics?.queueDepth || 0}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Jobs Queued
                    </Typography>
                  </Box>
                </Stack>
              </Stack>

              {/* Alerts */}
              {metrics?.queueDepth && metrics.queueDepth > 10 && (
                <Alert severity="warning">
                  High queue depth detected ({metrics.queueDepth} jobs). Consider adding more workers.
                </Alert>
              )}

              {metrics?.successRate && metrics.successRate < 80 && (
                <Alert severity="error">
                  Low success rate ({metrics.successRate.toFixed(1)}%). Check worker health and job configurations.
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

export default DashboardPage;
