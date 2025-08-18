import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Stack,
  Chip,
  LinearProgress,
  Divider,
} from '@mui/material';
import {
  TrendingUp as TrendingUpIcon,
  Speed as SpeedIcon,
  Timeline as TimelineIcon,
  DataUsage as DataUsageIcon,
} from '@mui/icons-material';

interface PerformanceMetricsProps {
  className?: string;
}

const PerformanceMetrics: React.FC<PerformanceMetricsProps> = ({ className }) => {
  const metrics = {
    // Core Performance Indicators
    totalJobsProcessed: 47832,
    successRate: 99.77,
    averageLatency: 94,
    systemUptime: 99.7,
    dailyThroughput: 2156,
    errorRate: 0.23,
    
    // Processing Capabilities
    peakConcurrentJobs: 47,
    totalDataProcessed: 1.2, // TB
    averageResponseTime: 156,
    queueEfficiency: 73,
    
    // Weekly Totals
    weeklyJobsCompleted: 22733,
    weeklySuccessRate: 91.8,
    dailyAverage: 3247,
    
    // Resource Utilization
    activeworkers: 6,
    totalWorkers: 6,
    cpuUtilization: 58.2,
    memoryUtilization: 54.7,
    networkThroughput: 245.7, // Mbps
  };

  const MetricCard = ({ 
    title, 
    value, 
    unit, 
    trend, 
    color = 'primary.main',
    icon: Icon 
  }: {
    title: string;
    value: number | string;
    unit?: string;
    trend?: string;
    color?: string;
    icon?: React.ElementType;
  }) => (
    <Card elevation={1}>
      <CardContent sx={{ textAlign: 'center', p: 2 }}>
        {Icon && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mb: 1 }}>
            <Icon sx={{ color, fontSize: 32 }} />
          </Box>
        )}
        <Typography variant="h4" color={color} fontWeight="bold">
          {typeof value === 'number' ? value.toLocaleString() : value}
          {unit && <Typography component="span" variant="h6" color="text.secondary">{unit}</Typography>}
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {title}
        </Typography>
        {trend && (
          <Chip
            label={trend}
            size="small"
            color={trend.includes('+') || trend.includes('↑') ? 'success' : trend.includes('-') || trend.includes('↓') ? 'error' : 'default'}
            sx={{ fontSize: '0.7rem' }}
          />
        )}
      </CardContent>
    </Card>
  );

  return (
    <Box className={className}>
      <Typography variant="h5" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <SpeedIcon />
        Performance Metrics Dashboard
      </Typography>
      
      {/* Core KPIs */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h6" gutterBottom>Core Performance Indicators</Typography>
        <Box sx={{ 
          display: 'grid', 
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, 
          gap: 2 
        }}>
          <MetricCard
            title="Total Jobs Processed"
            value={metrics.totalJobsProcessed}
            trend="+2,156 today"
            color="primary.main"
            icon={DataUsageIcon}
          />
          <MetricCard
            title="Success Rate"
            value={metrics.successRate}
            unit="%"
            trend="↑ 0.23% this week"
            color="success.main"
            icon={TrendingUpIcon}
          />
          <MetricCard
            title="Average Latency"
            value={metrics.averageLatency}
            unit="ms"
            trend="↓ 12ms from yesterday"
            color="info.main"
            icon={SpeedIcon}
          />
          <MetricCard
            title="System Uptime"
            value={metrics.systemUptime}
            unit="%"
            trend="30 days"
            color="success.main"
            icon={TimelineIcon}
          />
        </Box>
      </Box>

      {/* Processing Metrics */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h6" gutterBottom>Processing Capabilities</Typography>
        <Box sx={{ 
          display: 'grid', 
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' }, 
          gap: 2 
        }}>
          <MetricCard
            title="Peak Concurrent Jobs"
            value={metrics.peakConcurrentJobs}
            trend="This week"
            color="warning.main"
          />
          <MetricCard
            title="Data Processed"
            value={metrics.totalDataProcessed}
            unit=" TB"
            trend="Weekly total"
            color="secondary.main"
          />
          <MetricCard
            title="Queue Efficiency"
            value={metrics.queueEfficiency}
            unit="%"
            trend="↑ 5% from last week"
            color="info.main"
          />
        </Box>
      </Box>

      {/* Resource Utilization */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Resource Utilization</Typography>
          <Stack spacing={3}>
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2">Active Workers</Typography>
                <Typography variant="body2">{metrics.activeworkers}/{metrics.totalWorkers}</Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={(metrics.activeworkers / metrics.totalWorkers) * 100}
                sx={{ height: 8, borderRadius: 4 }}
              />
            </Box>
            
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2">Average CPU Utilization</Typography>
                <Typography variant="body2">{metrics.cpuUtilization}%</Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={metrics.cpuUtilization}
                sx={{ 
                  height: 8, 
                  borderRadius: 4,
                  '& .MuiLinearProgress-bar': {
                    backgroundColor: metrics.cpuUtilization > 80 ? '#f44336' : metrics.cpuUtilization > 60 ? '#ff9800' : '#4caf50',
                  }
                }}
              />
            </Box>
            
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2">Memory Utilization</Typography>
                <Typography variant="body2">{metrics.memoryUtilization}%</Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={metrics.memoryUtilization}
                sx={{ 
                  height: 8, 
                  borderRadius: 4,
                  '& .MuiLinearProgress-bar': {
                    backgroundColor: metrics.memoryUtilization > 80 ? '#f44336' : metrics.memoryUtilization > 60 ? '#ff9800' : '#4caf50',
                  }
                }}
              />
            </Box>
            
            <Divider />
            
            <Box sx={{ display: 'flex', justifyContent: 'space-around', textAlign: 'center' }}>
              <Box>
                <Typography variant="h6" color="primary">{metrics.dailyThroughput}</Typography>
                <Typography variant="caption" color="text.secondary">Jobs/Day</Typography>
              </Box>
              <Box>
                <Typography variant="h6" color="info.main">{metrics.networkThroughput}</Typography>
                <Typography variant="caption" color="text.secondary">Mbps Network</Typography>
              </Box>
              <Box>
                <Typography variant="h6" color="success.main">{metrics.averageResponseTime}</Typography>
                <Typography variant="caption" color="text.secondary">ms Response</Typography>
              </Box>
              <Box>
                <Typography variant="h6" color="error.main">{metrics.errorRate}</Typography>
                <Typography variant="caption" color="text.secondary">% Error Rate</Typography>
              </Box>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Weekly Summary */}
      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Weekly Performance Summary</Typography>
          <Box sx={{ 
            display: 'grid', 
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, 
            gap: 2,
            textAlign: 'center'
          }}>
            <Box>
              <Typography variant="h4" color="primary.main" fontWeight="bold">
                {metrics.weeklyJobsCompleted.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="text.secondary">Jobs Completed</Typography>
              <Typography variant="caption" color="success.main">+15% from last week</Typography>
            </Box>
            <Box>
              <Typography variant="h4" color="success.main" fontWeight="bold">
                {metrics.weeklySuccessRate}%
              </Typography>
              <Typography variant="body2" color="text.secondary">Success Rate</Typography>
              <Typography variant="caption" color="success.main">+2.1% improvement</Typography>
            </Box>
            <Box>
              <Typography variant="h4" color="info.main" fontWeight="bold">
                {metrics.dailyAverage.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="text.secondary">Daily Average</Typography>
              <Typography variant="caption" color="info.main">Consistent performance</Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default PerformanceMetrics;
