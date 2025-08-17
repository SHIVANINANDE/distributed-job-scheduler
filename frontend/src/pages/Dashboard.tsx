import React from 'react';
import { 
  Container, 
  Typography, 
  Card, 
  CardContent, 
  Box,
  Button
} from '@mui/material';
import { useNavigate } from 'react-router-dom';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Distributed Job Scheduler Dashboard
      </Typography>
      
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 4 }}>
        <Card sx={{ minWidth: 200, flex: 1 }}>
          <CardContent>
            <Typography variant="h6" component="h2">
              Total Jobs
            </Typography>
            <Typography variant="h4" color="primary">
              0
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ minWidth: 200, flex: 1 }}>
          <CardContent>
            <Typography variant="h6" component="h2">
              Running Jobs
            </Typography>
            <Typography variant="h4" color="success.main">
              0
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ minWidth: 200, flex: 1 }}>
          <CardContent>
            <Typography variant="h6" component="h2">
              Failed Jobs
            </Typography>
            <Typography variant="h4" color="error.main">
              0
            </Typography>
          </CardContent>
        </Card>
        
        <Card sx={{ minWidth: 200, flex: 1 }}>
          <CardContent>
            <Typography variant="h6" component="h2">
              Completed Jobs
            </Typography>
            <Typography variant="h4" color="info.main">
              0
            </Typography>
          </CardContent>
        </Card>
      </Box>
      
      <Box sx={{ mt: 4 }}>
        <Button 
          variant="contained" 
          onClick={() => navigate('/jobs')}
          sx={{ mr: 2 }}
        >
          View All Jobs
        </Button>
        <Button variant="outlined">
          Create New Job
        </Button>
      </Box>
    </Container>
  );
};

export default Dashboard;
