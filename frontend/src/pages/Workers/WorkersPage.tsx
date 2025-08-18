import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Alert,
  Chip,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Stack,
} from '@mui/material';
import {
  Add,
  Visibility,
  Computer,
  CheckCircle,
  Error,
  Warning,
} from '@mui/icons-material';
import { useWorkers } from '../../hooks/useApiQueries';

const WorkersPage: React.FC = () => {
  const navigate = useNavigate();
  const { data: workers, isLoading, error } = useWorkers();

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
        Failed to load workers. Please try again.
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

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Workers
        </Typography>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => navigate('/workers/register')}
        >
          Register Worker
        </Button>
      </Box>

      <Card>
        <CardContent>
          <Stack spacing={2} mb={3}>
            <Typography variant="h6">
              Worker Overview
            </Typography>
            <Stack direction="row" spacing={3}>
              <Box>
                <Typography variant="h4" color="success.main">
                  {workers?.filter(w => w.status === 'ACTIVE').length || 0}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Active Workers
                </Typography>
              </Box>
              <Box>
                <Typography variant="h4" color="primary.main">
                  {workers?.filter(w => w.status === 'BUSY').length || 0}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Busy Workers
                </Typography>
              </Box>
              <Box>
                <Typography variant="h4" color="error.main">
                  {workers?.filter(w => w.status === 'FAILED').length || 0}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Failed Workers
                </Typography>
              </Box>
              <Box>
                <Typography variant="h4" color="text.secondary">
                  {workers?.filter(w => w.status === 'INACTIVE').length || 0}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Inactive Workers
                </Typography>
              </Box>
            </Stack>
          </Stack>

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Worker ID</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Host</TableCell>
                  <TableCell>Capabilities</TableCell>
                  <TableCell>Last Heartbeat</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {workers?.map((worker) => (
                  <TableRow key={worker.id}>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {worker.id}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={1}>
                        {getStatusIcon(worker.status)}
                        <Typography variant="body1">
                          {worker.name}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip 
                        label={worker.status} 
                        color={getStatusColor(worker.status) as any}
                        size="small"
                        variant="filled"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {worker.hostName}:{worker.port}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Box display="flex" gap={0.5} flexWrap="wrap">
                        {worker.capabilities?.map((capability, index) => (
                          <Chip 
                            key={index}
                            label={capability} 
                            size="small" 
                            variant="outlined"
                          />
                        ))}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="textSecondary">
                        {worker.lastHeartbeat 
                          ? new Date(worker.lastHeartbeat).toLocaleString()
                          : 'Never'
                        }
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <IconButton
                        size="small"
                        onClick={() => navigate(`/workers/${worker.id}`)}
                      >
                        <Visibility />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
                {(!workers || workers.length === 0) && (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography variant="body1" color="textSecondary" py={4}>
                        No workers registered yet.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
    </Box>
  );
};

export default WorkersPage;
