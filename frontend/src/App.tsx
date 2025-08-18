import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CssBaseline, Box } from '@mui/material';

// Components
import Layout from './components/Layout/Layout';
import Dashboard from './pages/Dashboard';
import JobList from './pages/JobList';
import JobDetailsPage from './pages/Jobs/JobDetailsPage';
import WorkersPage from './pages/Workers/WorkersPage';
import WorkerDetailsPage from './pages/Workers/WorkerDetailsPage';
import MonitoringPage from './pages/Monitoring/MonitoringPage';

// Hooks
import { useWebSocket } from './hooks/useWebSocket';
import './App.css';

// Create a client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000, // 5 minutes
    },
  },
});

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1976d2',
      light: '#42a5f5',
      dark: '#1565c0',
    },
    secondary: {
      main: '#dc004e',
    },
    background: {
      default: '#f5f5f5',
      paper: '#ffffff',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h4: {
      fontWeight: 600,
    },
    h5: {
      fontWeight: 600,
    },
    h6: {
      fontWeight: 600,
    },
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          borderRadius: 8,
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          borderRadius: 6,
        },
      },
    },
  },
});

// WebSocket wrapper component
const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  useWebSocket({
    onConnect: () => console.log('WebSocket connected successfully'),
    onDisconnect: () => console.log('WebSocket disconnected'),
    onError: (error) => console.error('WebSocket error:', error),
  });

  return <>{children}</>;
};

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <WebSocketProvider>
          <Router>
            <Box sx={{ display: 'flex', minHeight: '100vh' }}>
              <Routes>
                <Route path="/" element={<Layout />}>
                  <Route index element={<Navigate to="/dashboard" replace />} />
                  <Route path="dashboard" element={<Dashboard />} />
                  <Route path="jobs" element={<JobList />} />
                  <Route path="jobs/:id" element={<JobDetailsPage />} />
                  <Route path="workers" element={<WorkersPage />} />
                  <Route path="workers/:workerId" element={<WorkerDetailsPage />} />
                  <Route path="monitoring" element={<MonitoringPage />} />
                  <Route path="*" element={<Navigate to="/dashboard" replace />} />
                </Route>
              </Routes>
            </Box>
          </Router>
        </WebSocketProvider>
        <ReactQueryDevtools initialIsOpen={false} />
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export default App;
