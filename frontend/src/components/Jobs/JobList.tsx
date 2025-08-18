import React, { useState, useMemo } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  TableSortLabel,
  Chip,
  IconButton,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stack,
  Tooltip,
  Menu,
  MenuList,
  ListItemText,
  CircularProgress,
  Alert,
} from '@mui/material';
import {
  Visibility,
  PlayArrow,
  Stop,
  Refresh,
  Delete,
  MoreVert,
  FilterList,
  Add,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { Job } from '../../types';
import { useJobs, useDeleteJob, useStartJob, useCancelJob, useRetryJob } from '../../hooks/useApiQueries';
import JobCreationForm from './JobCreationForm';

type SortDirection = 'asc' | 'desc';
type SortField = keyof Job;

interface JobListProps {
  showCreateButton?: boolean;
  onCreateJob?: () => void;
  onJobSelect?: (jobId: string) => void;
}

const JobList: React.FC<JobListProps> = ({ 
  showCreateButton = true,
  onCreateJob,
  onJobSelect
}) => {
  const navigate = useNavigate();
  const { data: jobs = [], isLoading, error } = useJobs();
  
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [createFormOpen, setCreateFormOpen] = useState(false);

  const deleteJobMutation = useDeleteJob();
  const startJobMutation = useStartJob();
  const cancelJobMutation = useCancelJob();
  const retryJobMutation = useRetryJob();

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

  const getPriorityColor = (priority: number) => {
    if (priority >= 8) return 'error';
    if (priority >= 5) return 'warning';
    return 'info';
  };

  const filteredAndSortedJobs = useMemo(() => {
    let filtered = jobs.filter((job) => {
      const matchesSearch = job.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                           (job.description?.toLowerCase().includes(searchTerm.toLowerCase()) ?? false);
      const matchesStatus = statusFilter === 'ALL' || job.status === statusFilter;
      const matchesPriority = priorityFilter === 'ALL' || 
                             (priorityFilter === 'HIGH' && job.priority >= 8) ||
                             (priorityFilter === 'MEDIUM' && job.priority >= 4 && job.priority < 8) ||
                             (priorityFilter === 'LOW' && job.priority < 4);
      
      return matchesSearch && matchesStatus && matchesPriority;
    });

    // Sort
    filtered.sort((a, b) => {
      let aValue = a[sortField];
      let bValue = b[sortField];

      // Handle different data types
      if (sortField === 'createdAt' || sortField === 'startedAt' || sortField === 'completedAt') {
        aValue = aValue ? new Date(aValue as string).getTime() : 0;
        bValue = bValue ? new Date(bValue as string).getTime() : 0;
      }

      if (typeof aValue === 'string' && typeof bValue === 'string') {
        return sortDirection === 'asc' 
          ? aValue.localeCompare(bValue)
          : bValue.localeCompare(aValue);
      }

      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
      }

      return 0;
    });

    return filtered;
  }, [jobs, searchTerm, statusFilter, priorityFilter, sortField, sortDirection]);

  const paginatedJobs = useMemo(() => {
    const startIndex = page * rowsPerPage;
    return filteredAndSortedJobs.slice(startIndex, startIndex + rowsPerPage);
  }, [filteredAndSortedJobs, page, rowsPerPage]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, jobId: string) => {
    setAnchorEl(event.currentTarget);
    setSelectedJobId(jobId);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setSelectedJobId(null);
  };

  const handleAction = async (action: string) => {
    if (!selectedJobId) return;

    try {
      switch (action) {
        case 'start':
          await startJobMutation.mutateAsync(selectedJobId);
          break;
        case 'cancel':
          await cancelJobMutation.mutateAsync(selectedJobId);
          break;
        case 'retry':
          await retryJobMutation.mutateAsync(selectedJobId);
          break;
        case 'delete':
          await deleteJobMutation.mutateAsync(selectedJobId);
          break;
        case 'view':
          if (onJobSelect) {
            onJobSelect(selectedJobId);
          } else {
            navigate(`/jobs/${selectedJobId}`);
          }
          break;
      }
    } catch (error) {
      console.error(`Failed to ${action} job:`, error);
    } finally {
      handleMenuClose();
    }
  };

  const getAvailableActions = (job: Job) => {
    const actions = [
      { key: 'view', label: 'View Details', icon: <Visibility /> },
    ];

    if (job.status === 'PENDING') {
      actions.push({ key: 'start', label: 'Start Job', icon: <PlayArrow /> });
    }
    
    if (job.status === 'RUNNING') {
      actions.push({ key: 'cancel', label: 'Cancel Job', icon: <Stop /> });
    }
    
    if (job.status === 'FAILED' && job.retryCount < job.maxRetries) {
      actions.push({ key: 'retry', label: 'Retry Job', icon: <Refresh /> });
    }

    if (job.status !== 'RUNNING') {
      actions.push({ key: 'delete', label: 'Delete Job', icon: <Delete /> });
    }

    return actions;
  };

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
        Failed to load jobs. Please try again.
      </Alert>
    );
  }

  return (
    <Box>
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
            <Typography variant="h6">
              Job Management ({filteredAndSortedJobs.length} jobs)
            </Typography>
            {showCreateButton && (
              <Button
                variant="contained"
                startIcon={<Add />}
                onClick={() => onCreateJob ? onCreateJob() : setCreateFormOpen(true)}
              >
                Create Job
              </Button>
            )}
          </Box>

          {/* Filters */}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mb={3}>
            <TextField
              label="Search Jobs"
              variant="outlined"
              size="small"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              sx={{ minWidth: 200 }}
            />
            
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>Status</InputLabel>
              <Select
                value={statusFilter}
                label="Status"
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="RUNNING">Running</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
                <MenuItem value="CANCELLED">Cancelled</MenuItem>
                <MenuItem value="SCHEDULED">Scheduled</MenuItem>
              </Select>
            </FormControl>

            <FormControl size="small" sx={{ minWidth: 120 }}>
              <InputLabel>Priority</InputLabel>
              <Select
                value={priorityFilter}
                label="Priority"
                onChange={(e) => setPriorityFilter(e.target.value)}
              >
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="HIGH">High (8-10)</MenuItem>
                <MenuItem value="MEDIUM">Medium (4-7)</MenuItem>
                <MenuItem value="LOW">Low (1-3)</MenuItem>
              </Select>
            </FormControl>
          </Stack>

          {/* Job Table */}
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'name'}
                      direction={sortField === 'name' ? sortDirection : 'asc'}
                      onClick={() => handleSort('name')}
                    >
                      Name
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'status'}
                      direction={sortField === 'status' ? sortDirection : 'asc'}
                      onClick={() => handleSort('status')}
                    >
                      Status
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'priority'}
                      direction={sortField === 'priority' ? sortDirection : 'asc'}
                      onClick={() => handleSort('priority')}
                    >
                      Priority
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'createdAt'}
                      direction={sortField === 'createdAt' ? sortDirection : 'asc'}
                      onClick={() => handleSort('createdAt')}
                    >
                      Created
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Dependencies</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {paginatedJobs.map((job) => (
                  <TableRow 
                    key={job.id} 
                    hover 
                    sx={{ cursor: onJobSelect ? 'pointer' : 'default' }}
                    onClick={() => onJobSelect && onJobSelect(job.id)}
                  >
                    <TableCell>
                      <Box>
                        <Typography variant="body2" fontWeight="medium">
                          {job.name}
                        </Typography>
                        {job.description && (
                          <Typography variant="caption" color="textSecondary">
                            {job.description.length > 50 
                              ? `${job.description.substring(0, 50)}...`
                              : job.description
                            }
                          </Typography>
                        )}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip 
                        label={job.jobType} 
                        size="small" 
                        variant="outlined" 
                      />
                    </TableCell>
                    <TableCell>
                      <Chip 
                        label={job.status} 
                        color={getStatusColor(job.status) as any}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Chip 
                        label={job.priority} 
                        color={getPriorityColor(job.priority) as any}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(job.createdAt).toLocaleDateString()}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {new Date(job.createdAt).toLocaleTimeString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {job.estimatedDuration ? `${job.estimatedDuration}s` : 'N/A'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {job.dependencies && job.dependencies.length > 0 ? (
                        <Tooltip title={job.dependencies.join(', ')}>
                          <Chip 
                            label={`${job.dependencies.length} deps`} 
                            size="small" 
                            variant="outlined" 
                          />
                        </Tooltip>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          None
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <IconButton
                        size="small"
                        onClick={(e) => handleMenuOpen(e, job.id)}
                      >
                        <MoreVert />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
                {paginatedJobs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} align="center">
                      <Typography variant="body1" color="textSecondary" py={4}>
                        No jobs found matching the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            component="div"
            count={filteredAndSortedJobs.length}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value, 10));
              setPage(0);
            }}
            rowsPerPageOptions={[5, 10, 25, 50]}
          />
        </CardContent>
      </Card>

      {/* Action Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuList>
          {selectedJobId && 
            getAvailableActions(jobs.find(j => j.id === selectedJobId)!).map((action) => (
              <MenuItem key={action.key} onClick={() => handleAction(action.key)}>
                {action.icon}
                <ListItemText sx={{ ml: 1 }}>{action.label}</ListItemText>
              </MenuItem>
            ))
          }
        </MenuList>
      </Menu>

      {/* Create Job Form */}
      <JobCreationForm
        open={createFormOpen}
        onClose={() => setCreateFormOpen(false)}
      />
    </Box>
  );
};

export default JobList;
