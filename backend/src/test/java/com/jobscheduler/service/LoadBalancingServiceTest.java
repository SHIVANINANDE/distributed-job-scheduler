package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for LoadBalancingService
 * Tests queue management, load balancing algorithms, and performance metrics
 */
@ExtendWith(MockitoExtension.class)
public class LoadBalancingServiceTest {
    
    @Mock
    private WorkerService workerService;
    
    @Mock
    private JobService jobService;
    
    @Mock
    private WorkerPerformanceService workerPerformanceService;
    
    @Mock
    private JobAssignmentService jobAssignmentService;
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private LoadBalancingService loadBalancingService;
    
    private Job testJob;
    private Worker testWorker1;
    private Worker testWorker2;
    private List<Worker> availableWorkers;
    
    @BeforeEach
    void setUp() {
        // Create test job
        testJob = new Job();
        testJob.setId(1L);
        testJob.setName("Test Job");
        testJob.setPriority(100);
        
        // Create test workers
        testWorker1 = new Worker("worker-1", "Test Worker 1");
        testWorker1.setStatus(Worker.WorkerStatus.ACTIVE);
        testWorker1.setMaxConcurrentJobs(5);
        testWorker1.setCurrentJobCount(2);
        testWorker1.setTotalJobsProcessed(100L);
        testWorker1.setTotalJobsSuccessful(95L);
        testWorker1.updateAvailableCapacity();
        
        testWorker2 = new Worker("worker-2", "Test Worker 2");
        testWorker2.setStatus(Worker.WorkerStatus.ACTIVE);
        testWorker2.setMaxConcurrentJobs(3);
        testWorker2.setCurrentJobCount(3); // At capacity
        testWorker2.setTotalJobsProcessed(50L);
        testWorker2.setTotalJobsSuccessful(48L);
        testWorker2.updateAvailableCapacity();
        
        availableWorkers = Arrays.asList(testWorker1, testWorker2);
    }
    
    @Test
    void testEnqueueJob_HighPriority() {
        // Given
        testJob.setPriority(600); // High priority
        
        // When
        boolean result = loadBalancingService.enqueueJob(testJob);
        
        // Then
        assertTrue(result);
        // Job should be in high priority queue
    }
    
    @Test
    void testEnqueueJob_NormalPriority() {
        // Given
        testJob.setPriority(200); // Normal priority
        
        // When
        boolean result = loadBalancingService.enqueueJob(testJob);
        
        // Then
        assertTrue(result);
        // Job should be in normal priority queue
    }
    
    @Test
    void testEnqueueJob_LowPriority() {
        // Given
        testJob.setPriority(50); // Low priority
        
        // When
        boolean result = loadBalancingService.enqueueJob(testJob);
        
        // Then
        assertTrue(result);
        // Job should be in low priority queue
    }
    
    @Test
    void testGetQueueStatus() {
        // Given
        loadBalancingService.enqueueJob(testJob);
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        
        // When
        Map<String, Object> status = loadBalancingService.getQueueStatus();
        
        // Then
        assertNotNull(status);
        assertTrue(status.containsKey("totalQueued"));
        assertTrue(status.containsKey("systemLoad"));
        assertTrue(status.containsKey("availableWorkers"));
        assertTrue(status.containsKey("loadBalancingEnabled"));
        assertTrue(status.containsKey("currentAlgorithm"));
    }
    
    @Test
    void testGetTotalQueuedJobs() {
        // Given
        Job job1 = new Job();
        job1.setPriority(600); // High priority
        
        Job job2 = new Job();
        job2.setPriority(200); // Normal priority
        
        Job job3 = new Job();
        job3.setPriority(50); // Low priority
        
        // When
        loadBalancingService.enqueueJob(job1);
        loadBalancingService.enqueueJob(job2);
        loadBalancingService.enqueueJob(job3);
        
        int totalQueued = loadBalancingService.getTotalQueuedJobs();
        
        // Then
        assertEquals(3, totalQueued);
    }
    
    @Test
    void testProcessJobQueues() {
        // Given
        loadBalancingService.enqueueJob(testJob);
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(workerService.getAllWorkers()).thenReturn(availableWorkers);
        when(jobAssignmentService.assignJob(any(Job.class))).thenReturn(testWorker1);
        
        // When
        loadBalancingService.processJobQueues();
        
        // Then
        verify(workerService, atLeastOnce()).getAvailableWorkers();
        verify(jobAssignmentService, atLeastOnce()).assignJob(any(Job.class));
    }
    
    @Test
    void testWorkerLoadInfo() {
        // Given
        when(workerService.getAllWorkers()).thenReturn(availableWorkers);
        
        // When
        loadBalancingService.processJobQueues(); // This updates worker load info
        Map<String, LoadBalancingService.WorkerLoadInfo> loadInfo = loadBalancingService.getWorkerLoadInfo();
        
        // Then
        assertNotNull(loadInfo);
        // May be empty initially if no processing has occurred
    }
    
    @Test
    void testGetLoadBalancingMetrics() {
        // When
        LoadBalancingService.LoadBalancingMetrics metrics = loadBalancingService.getLoadBalancingMetrics();
        
        // Then
        assertNotNull(metrics);
        assertEquals(0, metrics.getTotalJobsBalanced());
        assertEquals(0.0, metrics.getSuccessRate());
    }
    
    @Test
    void testRebalanceWorkerLoads() {
        // Given
        // Create overloaded worker
        testWorker1.setCurrentJobCount(5); // At max capacity
        testWorker1.updateAvailableCapacity();
        
        // Create underloaded worker
        Worker underloadedWorker = new Worker("worker-3", "Underloaded Worker");
        underloadedWorker.setStatus(Worker.WorkerStatus.ACTIVE);
        underloadedWorker.setMaxConcurrentJobs(5);
        underloadedWorker.setCurrentJobCount(1);
        underloadedWorker.updateAvailableCapacity();
        
        List<Worker> allWorkers = Arrays.asList(testWorker1, testWorker2, underloadedWorker);
        
        when(workerService.getAllWorkers()).thenReturn(allWorkers);
        when(jobService.getJobsByWorker(anyString())).thenReturn(Arrays.asList(testJob));
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        loadBalancingService.rebalanceWorkerLoads();
        
        // Then
        verify(workerService, atLeastOnce()).getAllWorkers();
        // Verify that load balancing attempts were made
    }
    
    @Test
    void testWorkerLoadInfoClass() {
        // Given
        LoadBalancingService.WorkerLoadInfo loadInfo = 
                new LoadBalancingService.WorkerLoadInfo("test-worker");
        
        // When
        loadInfo.updateLoad(testWorker1);
        
        // Then
        assertEquals("test-worker", loadInfo.getWorkerId());
        assertEquals(testWorker1.getLoadPercentage(), loadInfo.getCurrentLoad());
        assertEquals(testWorker1.getCurrentJobCount(), loadInfo.getActiveJobs());
        assertEquals(testWorker1.getMaxConcurrentJobs(), loadInfo.getMaxCapacity());
        assertTrue(loadInfo.canAcceptMoreJobs());
        assertFalse(loadInfo.isOverloaded());
    }
    
    @Test
    void testLoadBalancingMetricsClass() {
        // Given
        LoadBalancingService.LoadBalancingMetrics metrics = 
                new LoadBalancingService.LoadBalancingMetrics();
        
        // When
        metrics.recordBalancing(true, 100L, "INTELLIGENT");
        metrics.recordBalancing(false, 200L, "ROUND_ROBIN");
        
        // Then
        assertEquals(2, metrics.getTotalJobsBalanced());
        assertEquals(1, metrics.getSuccessfulBalancing());
        assertEquals(1, metrics.getFailedBalancing());
        assertEquals(50.0, metrics.getSuccessRate());
        assertEquals(150.0, metrics.getAverageBalancingTime());
        assertNotNull(metrics.getLastBalancing());
        
        Map<String, Integer> algorithmUsage = metrics.getAlgorithmUsage();
        assertEquals(1, algorithmUsage.get("INTELLIGENT"));
        assertEquals(1, algorithmUsage.get("ROUND_ROBIN"));
    }
    
    @Test
    void testAssignUsingDifferentAlgorithms() {
        // This test would verify different load balancing algorithms
        // For now, we test that the service can handle different configurations
        
        // Given
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(jobAssignmentService.assignJob(any(Job.class))).thenReturn(testWorker1);
        
        // Test processing with different job priorities
        Job highPriorityJob = new Job();
        highPriorityJob.setPriority(600);
        
        Job normalPriorityJob = new Job();
        normalPriorityJob.setPriority(200);
        
        Job lowPriorityJob = new Job();
        lowPriorityJob.setPriority(50);
        
        // When
        boolean highResult = loadBalancingService.enqueueJob(highPriorityJob);
        boolean normalResult = loadBalancingService.enqueueJob(normalPriorityJob);
        boolean lowResult = loadBalancingService.enqueueJob(lowPriorityJob);
        
        // Then
        assertTrue(highResult);
        assertTrue(normalResult);
        assertTrue(lowResult);
        
        // Verify total queued
        assertEquals(3, loadBalancingService.getTotalQueuedJobs());
    }
    
    @Test
    void testQueueCapacityLimits() {
        // Test that queues respect their capacity limits
        // This would require filling up a queue to its limit
        
        // For demonstration, test that the service handles queue state correctly
        Map<String, Object> initialStatus = loadBalancingService.getQueueStatus();
        assertNotNull(initialStatus);
        
        // Add a job
        loadBalancingService.enqueueJob(testJob);
        
        Map<String, Object> afterEnqueueStatus = loadBalancingService.getQueueStatus();
        assertNotNull(afterEnqueueStatus);
        
        // Total should have increased
        int initialTotal = (Integer) initialStatus.get("totalQueued");
        int afterTotal = (Integer) afterEnqueueStatus.get("totalQueued");
        assertEquals(initialTotal + 1, afterTotal);
    }
    
    @Test
    void testCacheOperations() {
        // Given
        loadBalancingService.enqueueJob(testJob);
        
        // When
        loadBalancingService.processJobQueues();
        
        // Then
        // Verify that cache operations are performed
        verify(cacheService, atLeastOnce()).put(anyString(), any(), anyInt());
    }
    
    @Test
    void testExceptionHandling() {
        // Given
        when(workerService.getAllWorkers()).thenThrow(new RuntimeException("Database error"));
        
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> {
            loadBalancingService.processJobQueues();
        });
        
        assertDoesNotThrow(() -> {
            loadBalancingService.rebalanceWorkerLoads();
        });
    }
    
    @Test
    void testScheduledMethods() {
        // Test that scheduled methods can be called without error
        
        // Given
        when(workerService.getAllWorkers()).thenReturn(availableWorkers);
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        
        // When/Then - Should not throw exceptions
        assertDoesNotThrow(() -> {
            loadBalancingService.processJobQueues();
        });
        
        assertDoesNotThrow(() -> {
            loadBalancingService.rebalanceWorkerLoads();
        });
    }
}
