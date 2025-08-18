import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  Stack,
  Alert,
  CircularProgress,
} from '@mui/material';
import { Timeline, TimelineItem, TimelineSeparator, TimelineConnector, TimelineContent, TimelineDot } from '@mui/lab';
import { 
  PlayArrow, 
  CheckCircle, 
  Error as ErrorIcon,
  Refresh,
  Cancel,
  Speed,
  AccessTime,
  Computer,
  Schedule,
  Stop,
} from '@mui/icons-material';
import { Job } from '../../store/useJobStore';
import { useJob } from '../../hooks/useApiQueries';

interface JobExecutionEvent {
  id: string;
  timestamp: string;
  event: 'CREATED' | 'STARTED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'RETRIED';
  message?: string;
  workerId?: string;
  duration?: number;
}

interface JobDetailsWithHistoryProps {
  job?: Job;
  jobId?: string;
  isLoading?: boolean;
}

const JobDetailsWithHistory: React.FC<JobDetailsWithHistoryProps> = ({ 
  job: propJob, 
  jobId,
  isLoading: propIsLoading = false 
}) => {
  // If jobId is provided, fetch the job data
  const { data: fetchedJob, isLoading: fetchIsLoading } = jobId 
    ? useJob(jobId) 
    : { data: undefined, isLoading: false };
  
  const job = propJob || fetchedJob;
  const isLoading = propIsLoading || fetchIsLoading;

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (!job) {
    return (
      <Alert severity="error">
        Job not found
      </Alert>
    );
  }
  // Mock execution history - in real app, this would come from an API
  const executionHistory: JobExecutionEvent[] = [
    {
      id: '1',
      timestamp: job.createdAt,
      event: 'CREATED',
      message: 'Job created and added to queue',
    },
    ...(job.startedAt ? [{
      id: '2',
      timestamp: job.startedAt,
      event: 'STARTED' as const,
      message: 'Job execution started',
      workerId: job.assignedWorkerId,
    }] : []),
    ...(job.retryCount > 0 ? Array.from({ length: job.retryCount }, (_, i) => ({
      id: `retry-${i}`,
      timestamp: new Date(Date.now() - (job.retryCount - i) * 60000).toISOString(),
      event: 'RETRIED' as const,
      message: `Job retry attempt ${i + 1}`,
    })) : []),
    ...(job.completedAt ? [{
      id: '3',
      timestamp: job.completedAt,
      event: job.status === 'COMPLETED' ? 'COMPLETED' as const : 
             job.status === 'FAILED' ? 'FAILED' as const : 'CANCELLED' as const,
      message: job.status === 'COMPLETED' ? 'Job completed successfully' :
               job.status === 'FAILED' ? job.errorMessage || 'Job failed' :
               'Job was cancelled',
      duration: job.startedAt ? 
        Math.round((new Date(job.completedAt).getTime() - new Date(job.startedAt).getTime()) / 1000) :
        undefined,
    }] : []),
  ];

  const getEventIcon = (event: JobExecutionEvent['event']) => {
    switch (event) {
      case 'CREATED':
        return <Schedule />;
      case 'STARTED':
        return <PlayArrow />;
      case 'COMPLETED':
        return <CheckCircle />;
      case 'FAILED':
        return <ErrorIcon />;
      case 'CANCELLED':
        return <Stop />;
      case 'RETRIED':
        return <Refresh />;
      default:
        return <Schedule />;
    }
  };

  const getChipColor = (event: JobExecutionEvent['event']): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' => {
    switch (event) {
      case 'CREATED':
        return 'default';
      case 'STARTED':
        return 'primary';
      case 'COMPLETED':
        return 'success';
      case 'FAILED':
        return 'error';
      case 'CANCELLED':
        return 'warning';
      case 'RETRIED':
        return 'info';
      default:
        return 'default';
    }
  };

  const formatDuration = (seconds: number) => {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Stack spacing={3}>
        {/* Job Summary */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Execution Summary
            </Typography>
            <Stack direction="row" spacing={3} flexWrap="wrap">
              <Box>
                <Typography variant="body2" color="textSecondary">
                  Total Retries
                </Typography>
                <Typography variant="h6">
                  {job.retryCount} / {job.maxRetries}
                </Typography>
              </Box>
              
              {job.startedAt && job.completedAt && (
                <Box>
                  <Typography variant="body2" color="textSecondary">
                    Execution Time
                  </Typography>
                  <Typography variant="h6">
                    {formatDuration(
                      Math.round((new Date(job.completedAt).getTime() - new Date(job.startedAt).getTime()) / 1000)
                    )}
                  </Typography>
                </Box>
              )}
              
              {job.estimatedDuration && (
                <Box>
                  <Typography variant="body2" color="textSecondary">
                    Estimated Duration
                  </Typography>
                  <Typography variant="h6">
                    {formatDuration(job.estimatedDuration)}
                  </Typography>
                </Box>
              )}
              
              {job.assignedWorkerId && (
                <Box>
                  <Typography variant="body2" color="textSecondary">
                    Assigned Worker
                  </Typography>
                  <Typography variant="h6">
                    {job.assignedWorkerId}
                  </Typography>
                </Box>
              )}
            </Stack>
          </CardContent>
        </Card>

        {/* Error Message */}
        {job.errorMessage && (
          <Alert severity="error">
            <Typography variant="subtitle2" gutterBottom>
              Error Details
            </Typography>
            {job.errorMessage}
          </Alert>
        )}

        {/* Execution Timeline */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Execution History
            </Typography>
            
            {executionHistory.length > 0 ? (
              <Timeline>
                {executionHistory.map((event, index) => (
                  <TimelineItem key={event.id}>
                    <TimelineSeparator>
                      <TimelineDot color={getChipColor(event.event) === 'default' ? 'grey' : 
                                            getChipColor(event.event) === 'secondary' ? 'primary' : 
                                            getChipColor(event.event) as any}>
                        {getEventIcon(event.event)}
                      </TimelineDot>
                      {index < executionHistory.length - 1 && <TimelineConnector />}
                    </TimelineSeparator>
                    <TimelineContent>
                      <Box>
                        <Box display="flex" alignItems="center" gap={1} mb={1}>
                          <Chip 
                            label={event.event} 
                            size="small" 
                            color={getChipColor(event.event)}
                            variant="outlined"
                          />
                          <Typography variant="body2" color="textSecondary">
                            {new Date(event.timestamp).toLocaleString()}
                          </Typography>
                        </Box>
                        
                        <Typography variant="body1" gutterBottom>
                          {event.message}
                        </Typography>
                        
                        <Stack direction="row" spacing={2} flexWrap="wrap">
                          {event.workerId && (
                            <Typography variant="caption" color="textSecondary">
                              Worker: {event.workerId}
                            </Typography>
                          )}
                          {event.duration && (
                            <Typography variant="caption" color="textSecondary">
                              Duration: {formatDuration(event.duration)}
                            </Typography>
                          )}
                        </Stack>
                      </Box>
                    </TimelineContent>
                  </TimelineItem>
                ))}
              </Timeline>
            ) : (
              <Typography variant="body2" color="textSecondary" py={4} textAlign="center">
                No execution history available.
              </Typography>
            )}
          </CardContent>
        </Card>

        {/* Dependencies */}
        {job.dependencies && job.dependencies.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Dependencies
              </Typography>
              <Typography variant="body2" color="textSecondary" mb={2}>
                This job depends on the following jobs to complete first:
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {job.dependencies && job.dependencies.map((depId: string) => (
                  <Chip
                    key={depId}
                    label={depId}
                    variant="outlined"
                    size="small"
                    clickable
                    onClick={() => {
                      // Navigate to dependency job details
                      window.open(`/jobs/${depId}`, '_blank');
                    }}
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>
        )}

        {/* Tags */}
        {job.tags && job.tags.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Tags
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {job.tags && job.tags.map((tag: string) => (
                  <Chip
                    key={tag}
                    label={tag}
                    variant="outlined"
                    size="small"
                    color="primary"
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>
        )}
      </Stack>
    </Box>
  );
};

export default JobDetailsWithHistory;
