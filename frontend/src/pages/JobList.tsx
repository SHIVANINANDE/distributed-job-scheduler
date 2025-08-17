import React from 'react';
import { 
  Container, 
  Typography, 
  Table, 
  TableBody, 
  TableCell, 
  TableContainer, 
  TableHead, 
  TableRow, 
  Paper,
  Chip,
  Button,
  Box
} from '@mui/material';
import { useNavigate } from 'react-router-dom';

const JobList: React.FC = () => {
  const navigate = useNavigate();

  // Mock data for demonstration
  const jobs = [
    { id: 1, name: 'Data Processing Job', status: 'Running', createdAt: '2024-01-15T10:00:00Z' },
    { id: 2, name: 'Email Notification Job', status: 'Completed', createdAt: '2024-01-15T09:30:00Z' },
    { id: 3, name: 'File Backup Job', status: 'Failed', createdAt: '2024-01-15T09:00:00Z' },
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Running':
        return 'primary';
      case 'Completed':
        return 'success';
      case 'Failed':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          Job List
        </Typography>
        <Box>
          <Button variant="contained" sx={{ mr: 2 }}>
            Create New Job
          </Button>
          <Button variant="outlined" onClick={() => navigate('/')}>
            Back to Dashboard
          </Button>
        </Box>
      </Box>
      
      <TableContainer component={Paper}>
        <Table sx={{ minWidth: 650 }} aria-label="job table">
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Job Name</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Created At</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {jobs.map((job) => (
              <TableRow key={job.id}>
                <TableCell component="th" scope="row">
                  {job.id}
                </TableCell>
                <TableCell>{job.name}</TableCell>
                <TableCell>
                  <Chip 
                    label={job.status} 
                    color={getStatusColor(job.status) as any}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>{new Date(job.createdAt).toLocaleString()}</TableCell>
                <TableCell>
                  <Button size="small" variant="outlined" sx={{ mr: 1 }}>
                    View
                  </Button>
                  <Button size="small" variant="outlined" color="error">
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      
      {jobs.length === 0 && (
        <Box sx={{ textAlign: 'center', mt: 4 }}>
          <Typography variant="h6" color="textSecondary">
            No jobs found. Create your first job!
          </Typography>
        </Box>
      )}
    </Container>
  );
};

export default JobList;
