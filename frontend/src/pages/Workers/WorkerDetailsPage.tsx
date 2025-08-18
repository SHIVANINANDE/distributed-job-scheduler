import React from 'react';
import { useParams } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Alert,
  Chip,
  Divider,
  IconButton,
  Stack,
  LinearProgress,
} from '@mui/material';
import {
  ArrowBack,
  Computer,
  CheckCircle,
  Error,
  Warning,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useWorker } from '../../hooks/useApiQueries';

const WorkerDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const { data: worker, isLoading, error } = useWorker(id!);

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
        Failed to load worker details. Please try again.
      </Alert>
    );
  }

  if (!worker) {
    return (
      <Alert severity="warning">
        Worker not found.
      </Alert>
    );
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'BUSY':
        return 'primary';
      case 'INACTIVE':
        return 'default';
      case 'FAILED':
        return 'error';
      default:
        return 'default';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <CheckCircle />;
      case 'BUSY':
        return <Computer />;
      case 'INACTIVE':
        return <Warning />;
      case 'FAILED':
        return <Error />;
      default:
        return <Computer />;
    }
  };

  const utilizationPercentage = (worker.currentJobCount / worker.maxConcurrentJobs) * 100;
  const successRate = worker.totalJobsProcessed > 0 
    ? (worker.totalJobsSuccessful / worker.totalJobsProcessed) * 100 
    : 0;

  return (
    <Box>
      <Box display="flex" alignItems="center" mb={3}>
        <IconButton onClick={() => navigate('/workers')} sx={{ mr: 2 }}>
          <ArrowBack />
        </IconButton>
        <Typography variant="h4" component="h1">
          Worker Details
        </Typography>
      </Box>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Box flex={2}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Box display="flex" alignItems="center" gap={2}>
                  {getStatusIcon(worker.status)}
                  <Typography variant="h5" component="h2">
                    {worker.name}
                  </Typography>
                </Box>
                <Chip 
                  label={worker.status} 
                  color={getStatusColor(worker.status) as any}
                  variant="filled"
                />
              </Box>

              <Typography variant="body2" color="textSecondary" fontFamily="monospace" mb={2}>
                Worker ID: {worker.workerId}
              </Typography>

              <Divider sx={{ my: 2 }} />

              <Stack direction="row" flexWrap="wrap" spacing={2}>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Host Name
                  </Typography>
                  <Typography variant="body1">
                    {worker.hostName}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Host Address
                  </Typography>
                  <Typography variant="body1" fontFamily="monospace">
                    {worker.hostAddress}:{worker.port}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Max Concurrent Jobs
                  </Typography>
                  <Typography variant="body1">
                    {worker.maxConcurrentJobs}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Current Job Count
                  </Typography>
                  <Typography variant="body1">
                    {worker.currentJobCount}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Available Capacity
                  </Typography>
                  <Typography variant="body1">
                    {worker.availableCapacity}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Last Heartbeat
                  </Typography>
                  <Typography variant="body1">
                    {worker.lastHeartbeat 
                      ? new Date(worker.lastHeartbeat).toLocaleString()
                      : 'Never'
                    }
                  </Typography>
                </Stack>
              </Stack>

              <Divider sx={{ my: 2 }} />

              <Typography variant="h6" gutterBottom>
                Performance Metrics
              </Typography>
              <Stack spacing={2}>
                <Box>
                  <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="body2" color="textSecondary">
                      Utilization
                    </Typography>
                    <Typography variant="body2">
                      {utilizationPercentage.toFixed(1)}%
                    </Typography>
                  </Box>
                  <LinearProgress 
                    variant="determinate" 
                    value={utilizationPercentage} 
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Box>
                <Box>
                  <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="body2" color="textSecondary">
                      Success Rate
                    </Typography>
                    <Typography variant="body2">
                      {successRate.toFixed(1)}%
                    </Typography>
                  </Box>
                  <LinearProgress 
                    variant="determinate" 
                    value={successRate} 
                    color="success"
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Box>
              </Stack>

              <Stack direction="row" spacing={4} mt={2}>
                <Box>
                  <Typography variant="h6" color="primary">
                    {worker.totalJobsProcessed}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Total Jobs Processed
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h6" color="success.main">
                    {worker.totalJobsSuccessful}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Successful Jobs
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h6" color="error.main">
                    {worker.totalJobsProcessed - worker.totalJobsSuccessful}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Failed Jobs
                  </Typography>
                </Box>
              </Stack>

              {worker.capabilities && worker.capabilities.length > 0 && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" color="textSecondary" mb={1}>
                    Capabilities
                  </Typography>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {worker.capabilities.map((capability, index) => (
                      <Chip key={index} label={capability} size="small" variant="outlined" />
                    ))}
                  </Box>
                </>
              )}

              {worker.tags && worker.tags.length > 0 && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" color="textSecondary" mb={1}>
                    Tags
                  </Typography>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {worker.tags.map((tag, index) => (
                      <Chip key={index} label={tag} size="small" variant="outlined" />
                    ))}
                  </Box>
                </>
              )}
            </CardContent>
          </Card>
        </Box>

        <Box flex={1}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Worker Status
              </Typography>
              <Stack spacing={2}>
                <Box 
                  display="flex" 
                  alignItems="center" 
                  gap={1}
                  p={2} 
                  sx={{ 
                    backgroundColor: getStatusColor(worker.status) === 'success' ? 'success.light' : 
                                   getStatusColor(worker.status) === 'error' ? 'error.light' :
                                   getStatusColor(worker.status) === 'primary' ? 'primary.light' : 'grey.100',
                    borderRadius: 1,
                    color: getStatusColor(worker.status) === 'success' ? 'success.contrastText' : 
                           getStatusColor(worker.status) === 'error' ? 'error.contrastText' :
                           getStatusColor(worker.status) === 'primary' ? 'primary.contrastText' : 'text.primary',
                  }}
                >
                  {getStatusIcon(worker.status)}
                  <Typography variant="h6">
                    {worker.status}
                  </Typography>
                </Box>
                
                <Typography variant="body2" color="textSecondary">
                  This worker is currently {worker.status.toLowerCase()}.
                  {worker.status === 'ACTIVE' && ' Ready to accept new jobs.'}
                  {worker.status === 'BUSY' && ` Processing ${worker.currentJobCount} job(s).`}
                  {worker.status === 'INACTIVE' && ' Not responding to heartbeat.'}
                  {worker.status === 'FAILED' && ' Encountered an error and needs attention.'}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Box>
      </Stack>
    </Box>
  );
};

export default WorkerDetailsPage;
