import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Container,
  Tabs,
  Tab,
  Paper,
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
import { SystemMonitoringDashboard } from '../components/Dashboard';
import { JobCreationForm, JobList, JobDetailsWithHistory } from '../components/Jobs';

const ComprehensiveDashboardPage: React.FC = () => {
  const [tabValue, setTabValue] = useState(0);
  const [jobFormOpen, setJobFormOpen] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const { data: metrics, isLoading, error } = useDashboardMetrics();

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleCreateJob = () => {
    setJobFormOpen(true);
  };

  const handleJobSelect = (jobId: string) => {
    setSelectedJobId(jobId);
    setTabValue(2); // Switch to job details tab
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
        Failed to load dashboard data. Please try again.
      </Alert>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h3" component="h1" gutterBottom>
        Distributed Job Scheduler
      </Typography>
      
      <Paper sx={{ mt: 3 }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs 
            value={tabValue} 
            onChange={handleTabChange} 
            aria-label="dashboard tabs"
            variant="scrollable"
            scrollButtons="auto"
          >
            <Tab label="System Monitoring" />
            <Tab label="Job Management" />
            <Tab label="Job Details" />
          </Tabs>
        </Box>
        
        {/* System Monitoring Tab */}
        {tabValue === 0 && (
          <Box sx={{ p: 0 }}>
            <SystemMonitoringDashboard />
          </Box>
        )}
        
        {/* Job Management Tab */}
        {tabValue === 1 && (
          <Box sx={{ p: 3 }}>
            <JobList 
              onCreateJob={handleCreateJob}
              onJobSelect={handleJobSelect}
            />
          </Box>
        )}
        
        {/* Job Details Tab */}
        {tabValue === 2 && (
          <Box sx={{ p: 3 }}>
            {selectedJobId ? (
              <JobDetailsWithHistory jobId={selectedJobId} />
            ) : (
              <Box 
                sx={{ 
                  display: 'flex', 
                  justifyContent: 'center', 
                  alignItems: 'center', 
                  height: '400px',
                  flexDirection: 'column',
                  gap: 2 
                }}
              >
                <Typography variant="h6" color="text.secondary">
                  No job selected
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Select a job from the Job Management tab to view details
                </Typography>
              </Box>
            )}
          </Box>
        )}
      </Paper>

      <JobCreationForm 
        open={jobFormOpen} 
        onClose={() => setJobFormOpen(false)} 
      />
    </Container>
  );
};

export default ComprehensiveDashboardPage;
