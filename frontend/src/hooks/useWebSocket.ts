import { useEffect, useRef, useCallback } from 'react';
import { useJobStore } from '../store/useJobStore';
import { Job, Worker, MetricData } from '../store/useJobStore';

interface WebSocketMessage {
  type: 'JOB_UPDATE' | 'WORKER_UPDATE' | 'METRICS_UPDATE' | 'SYSTEM_EVENT';
  data: any;
  timestamp: string;
}

interface UseWebSocketOptions {
  url?: string;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Event) => void;
}

export const useWebSocket = (options: UseWebSocketOptions = {}) => {
  const {
    url = process.env.REACT_APP_WS_URL || 'ws://localhost:8080/ws',
    reconnectInterval = 3000,
    maxReconnectAttempts = 5,
    onConnect,
    onDisconnect,
    onError,
  } = options;

  const ws = useRef<WebSocket | null>(null);
  const reconnectCount = useRef(0);
  const reconnectTimer = useRef<NodeJS.Timeout | null>(null);

  const handleMessage = useCallback((event: MessageEvent) => {
    try {
      const message: WebSocketMessage = JSON.parse(event.data);
      const store = useJobStore.getState();
      
      switch (message.type) {
        case 'JOB_UPDATE':
          const jobData = message.data as Job;
          if (jobData.id) {
            store.updateJob(jobData);
          }
          break;
          
        case 'WORKER_UPDATE':
          const workerData = message.data as Worker;
          if (workerData.workerId) {
            store.updateWorker(workerData);
          }
          break;
          
        case 'METRICS_UPDATE':
          if (message.data.realtimeMetric) {
            const metric: MetricData = {
              timestamp: message.timestamp,
              value: message.data.value,
              label: message.data.label,
            };
            store.addRealtimeMetric(metric);
          }
          
          if (message.data.dashboardMetrics) {
            store.setDashboardMetrics(message.data.dashboardMetrics);
          }
          break;
          
        case 'SYSTEM_EVENT':
          // Handle system events like job completion notifications
          console.log('System event:', message.data);
          break;
          
        default:
          console.warn('Unknown message type:', message.type);
      }
    } catch (error) {
      console.error('Error parsing WebSocket message:', error);
    }
  }, []); // No dependencies

  const handleOpen = useCallback(() => {
    console.log('WebSocket connected');
    useJobStore.getState().setWsConnected(true);
    reconnectCount.current = 0;
    onConnect?.();
  }, [onConnect]);

  const handleClose = useCallback(() => {
    console.log('WebSocket disconnected');
    useJobStore.getState().setWsConnected(false);
    onDisconnect?.();
    
    // Attempt to reconnect
    if (reconnectCount.current < maxReconnectAttempts) {
      reconnectCount.current++;
      console.log(`Attempting to reconnect... (${reconnectCount.current}/${maxReconnectAttempts})`);
      
      reconnectTimer.current = setTimeout(() => {
        if (ws.current?.readyState !== WebSocket.OPEN) {
          try {
            ws.current = new WebSocket(url);
            ws.current.onopen = handleOpen;
            ws.current.onclose = handleClose;
            ws.current.onerror = handleError;
            ws.current.onmessage = handleMessage;
          } catch (error) {
            console.error('Failed to reconnect WebSocket:', error);
          }
        }
      }, reconnectInterval);
    } else {
      console.error('Max reconnection attempts reached');
    }
  }, [url, maxReconnectAttempts, reconnectInterval, onDisconnect]);

  const handleError = useCallback((error: Event) => {
    console.error('WebSocket error:', error);
    onError?.(error);
  }, [onError]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const connect = useCallback(() => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      return;
    }

    try {
      ws.current = new WebSocket(url);
      ws.current.onopen = handleOpen;
      ws.current.onclose = handleClose;
      ws.current.onerror = handleError;
      ws.current.onmessage = handleMessage;
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error);
    }
  }, [url]); // Only depend on URL

  const disconnect = useCallback(() => {
    if (reconnectTimer.current) {
      clearTimeout(reconnectTimer.current);
      reconnectTimer.current = null;
    }
    
    if (ws.current) {
      ws.current.close();
      ws.current = null;
    }
    
    useJobStore.getState().setWsConnected(false);
  }, []);

  const sendMessage = useCallback((message: any) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not connected');
    }
  }, []);

  // Subscribe to specific events
  const subscribe = useCallback((eventType: string) => {
    sendMessage({
      type: 'SUBSCRIBE',
      eventType,
      timestamp: new Date().toISOString(),
    });
  }, [sendMessage]);

  const unsubscribe = useCallback((eventType: string) => {
    sendMessage({
      type: 'UNSUBSCRIBE',
      eventType,
      timestamp: new Date().toISOString(),
    });
  }, [sendMessage]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    // Initialize connection
    const initConnection = () => {
      if (ws.current?.readyState === WebSocket.OPEN) {
        return;
      }

      try {
        ws.current = new WebSocket(url);
        ws.current.onopen = handleOpen;
        ws.current.onclose = handleClose;
        ws.current.onerror = handleError;
        ws.current.onmessage = handleMessage;
      } catch (error) {
        console.error('Failed to create WebSocket connection:', error);
      }
    };

    initConnection();

    return () => {
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);
        reconnectTimer.current = null;
      }
      
      if (ws.current) {
        ws.current.close();
        ws.current = null;
      }
    };
  }, [url]); // Only depend on URL

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);
      }
    };
  }, []);

  return {
    connect,
    disconnect,
    sendMessage,
    subscribe,
    unsubscribe,
    isConnected: useJobStore((state) => state.wsConnected),
  };
};

// Hook for Server-Sent Events as an alternative to WebSocket
export const useServerSentEvents = (options: { url?: string } = {}) => {
  const {
    url = process.env.REACT_APP_SSE_URL || 'http://localhost:8080/api/events',
  } = options;

  const eventSource = useRef<EventSource | null>(null);

  const connect = useCallback(() => {
    if (eventSource.current) {
      return;
    }

    try {
      eventSource.current = new EventSource(url);

      eventSource.current.onopen = () => {
        console.log('SSE connected');
        useJobStore.getState().setWsConnected(true);
      };

      eventSource.current.onerror = (error) => {
        console.error('SSE error:', error);
        useJobStore.getState().setWsConnected(false);
      };

      eventSource.current.addEventListener('job-update', (event) => {
        try {
          const job = JSON.parse(event.data);
          useJobStore.getState().updateJob(job);
        } catch (error) {
          console.error('Error parsing job update:', error);
        }
      });

      eventSource.current.addEventListener('worker-update', (event) => {
        try {
          const worker = JSON.parse(event.data);
          useJobStore.getState().updateWorker(worker);
        } catch (error) {
          console.error('Error parsing worker update:', error);
        }
      });

      eventSource.current.addEventListener('metrics-update', (event) => {
        try {
          const metric = JSON.parse(event.data);
          useJobStore.getState().addRealtimeMetric(metric);
        } catch (error) {
          console.error('Error parsing metrics update:', error);
        }
      });

    } catch (error) {
      console.error('Failed to create SSE connection:', error);
    }
  }, [url]);

  const disconnect = useCallback(() => {
    if (eventSource.current) {
      eventSource.current.close();
      eventSource.current = null;
      useJobStore.getState().setWsConnected(false);
    }
  }, []);

  useEffect(() => {
    // Initialize SSE connection
    if (eventSource.current) {
      return;
    }

    try {
      eventSource.current = new EventSource(url);

      eventSource.current.onopen = () => {
        console.log('SSE connected');
        useJobStore.getState().setWsConnected(true);
      };

      eventSource.current.onerror = (error) => {
        console.error('SSE error:', error);
        useJobStore.getState().setWsConnected(false);
      };

      // Add event listeners
      eventSource.current.addEventListener('job-update', (event) => {
        try {
          const job = JSON.parse(event.data);
          useJobStore.getState().updateJob(job);
        } catch (error) {
          console.error('Error parsing job update:', error);
        }
      });

      eventSource.current.addEventListener('worker-update', (event) => {
        try {
          const worker = JSON.parse(event.data);
          useJobStore.getState().updateWorker(worker);
        } catch (error) {
          console.error('Error parsing worker update:', error);
        }
      });

      eventSource.current.addEventListener('metrics-update', (event) => {
        try {
          const metric = JSON.parse(event.data);
          useJobStore.getState().addRealtimeMetric(metric);
        } catch (error) {
          console.error('Error parsing metrics update:', error);
        }
      });

    } catch (error) {
      console.error('Failed to create SSE connection:', error);
    }

    return () => {
      if (eventSource.current) {
        eventSource.current.close();
        eventSource.current = null;
      }
    };
  }, [url]);

  return {
    connect,
    disconnect,
    isConnected: useJobStore((state) => state.wsConnected),
  };
};
