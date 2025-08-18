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
  Button,
  IconButton,
  Stack,
} from '@mui/material';
import {
  PlayArrow,
  Stop,
  Refresh,
  ArrowBack,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useJob, useStartJob, useCancelJob, useRetryJob } from '../../hooks/useApiQueries';

const JobDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const { data: job, isLoading, error } = useJob(id!);
  const startJobMutation = useStartJob();
  const cancelJobMutation = useCancelJob();
  const retryJobMutation = useRetryJob();

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
        Failed to load job details. Please try again.
      </Alert>
    );
  }

  if (!job) {
    return (
      <Alert severity="warning">
        Job not found.
      </Alert>
    );
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'RUNNING':
        return 'primary';
      case 'FAILED':
        return 'error';
      case 'CANCELLED':
        return 'default';
      case 'PENDING':
      case 'SCHEDULED':
        return 'warning';
      default:
        return 'default';
    }
  };

  const handleStartJob = () => {
    startJobMutation.mutate(id!);
  };

  const handleCancelJob = () => {
    cancelJobMutation.mutate(id!);
  };

  const handleRetryJob = () => {
    retryJobMutation.mutate(id!);
  };

  return (
    <Box>
      <Box display="flex" alignItems="center" mb={3}>
        <IconButton onClick={() => navigate('/jobs')} sx={{ mr: 2 }}>
          <ArrowBack />
        </IconButton>
        <Typography variant="h4" component="h1">
          Job Details
        </Typography>
      </Box>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Box flex={2}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h5" component="h2">
                  {job.name}
                </Typography>
                <Chip 
                  label={job.status} 
                  color={getStatusColor(job.status) as any}
                  variant="filled"
                />
              </Box>

              <Typography variant="body1" color="textSecondary" paragraph>
                {job.description || 'No description provided'}
              </Typography>

              <Divider sx={{ my: 2 }} />

              <Stack direction="row" flexWrap="wrap" spacing={2}>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Job Type
                  </Typography>
                  <Typography variant="body1">
                    {job.jobType}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Priority
                  </Typography>
                  <Typography variant="body1">
                    {job.priority}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Created At
                  </Typography>
                  <Typography variant="body1">
                    {new Date(job.createdAt).toLocaleString()}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Started At
                  </Typography>
                  <Typography variant="body1">
                    {job.startedAt ? new Date(job.startedAt).toLocaleString() : 'Not started'}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Completed At
                  </Typography>
                  <Typography variant="body1">
                    {job.completedAt ? new Date(job.completedAt).toLocaleString() : 'Not completed'}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Assigned Worker
                  </Typography>
                  <Typography variant="body1">
                    {job.assignedWorkerId || 'Not assigned'}
                  </Typography>
                </Stack>
                <Stack spacing={1} sx={{ minWidth: 200 }}>
                  <Typography variant="subtitle2" color="textSecondary">
                    Retry Count
                  </Typography>
                  <Typography variant="body1">
                    {job.retryCount} / {job.maxRetries}
                  </Typography>
                </Stack>
              </Stack>

              {job.errorMessage && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" color="textSecondary">
                    Error Message
                  </Typography>
                  <Alert severity="error" sx={{ mt: 1 }}>
                    {job.errorMessage}
                  </Alert>
                </>
              )}

              {job.tags && job.tags.length > 0 && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" color="textSecondary" mb={1}>
                    Tags
                  </Typography>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {job.tags.map((tag, index) => (
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
                Actions
              </Typography>
              <Stack spacing={2}>
                {job.status === 'PENDING' && (
                  <Button
                    variant="contained"
                    startIcon={<PlayArrow />}
                    onClick={handleStartJob}
                    disabled={startJobMutation.isPending}
                  >
                    Start Job
                  </Button>
                )}
                
                {job.status === 'RUNNING' && (
                  <Button
                    variant="contained"
                    color="secondary"
                    startIcon={<Stop />}
                    onClick={handleCancelJob}
                    disabled={cancelJobMutation.isPending}
                  >
                    Cancel Job
                  </Button>
                )}
                
                {(job.status === 'FAILED' && job.retryCount < job.maxRetries) && (
                  <Button
                    variant="contained"
                    color="warning"
                    startIcon={<Refresh />}
                    onClick={handleRetryJob}
                    disabled={retryJobMutation.isPending}
                  >
                    Retry Job
                  </Button>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Box>
      </Stack>
    </Box>
  );
};

export default JobDetailsPage;
