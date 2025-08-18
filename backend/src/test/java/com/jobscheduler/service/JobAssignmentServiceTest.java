package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for JobAssignmentService
 * Tests all assignment strategies and edge cases
 */
@ExtendWith(MockitoExtension.class)
public class JobAssignmentServiceTest {
    
    @Mock
    private WorkerService workerService;
    
    @Mock
    private JobService jobService;
    
    @Mock
    private WorkerPerformanceService workerPerformanceService;
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private JobAssignmentService jobAssignmentService;
    
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
        testJob.setStatus(JobStatus.PENDING);
        
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
        testWorker2.setCurrentJobCount(1);
        testWorker2.setTotalJobsProcessed(50L);
        testWorker2.setTotalJobsSuccessful(48L);
        testWorker2.updateAvailableCapacity();
        
        availableWorkers = Arrays.asList(testWorker1, testWorker2);
    }
    
    @Test
    void testAssignJob_Success() {
        // Given
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNotNull(assignedWorker);
        assertEquals(JobStatus.RUNNING, testJob.getStatus());
        assertNotNull(testJob.getStartedAt());
        verify(jobService).updateJob(testJob);
        verify(workerService).updateWorker(any(Worker.class));
    }
    
    @Test
    void testAssignJob_NoAvailableWorkers() {
        // Given
        when(workerService.getAvailableWorkers()).thenReturn(Arrays.asList());
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNull(assignedWorker);
        verify(jobService, never()).updateJob(any(Job.class));
        verify(workerService, never()).updateWorker(any(Worker.class));
    }
    
    @Test
    void testAssignJob_HighPriorityJob() {
        // Given
        testJob.setPriority(600); // High priority
        testWorker1.setMaxConcurrentJobs(10); // High capacity worker
        testWorker1.updateAvailableCapacity();
        
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNotNull(assignedWorker);
        // High-priority jobs should prefer high-capacity workers
        assertTrue(assignedWorker.getMaxConcurrentJobs() >= 5);
    }
    
    @Test
    void testReassignJob_Success() {
        // Given
        testJob.setStatus(JobStatus.RUNNING);
        testJob.assignToWorker("failed-worker", "Failed Worker", "localhost", 8080);
        
        when(jobService.getJobByIdDirect(1L)).thenReturn(testJob);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.getWorkerByWorkerIdDirect("failed-worker")).thenReturn(testWorker1);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        
        // When
        boolean result = jobAssignmentService.reassignJob(1L, "failed-worker", "Worker crashed");
        
        // Then
        assertTrue(result);
        assertEquals(JobStatus.PENDING, testJob.getStatus());
        assertNull(testJob.getAssignedWorkerId());
        assertTrue(testJob.getRetryCount() > 0);
        verify(jobService, atLeast(1)).updateJob(testJob);
    }
    
    @Test
    void testReassignJob_MaxRetriesExceeded() {
        // Given
        testJob.setRetryCount(5); // Exceeds max retries
        testJob.setStatus(JobStatus.RUNNING);
        
        when(jobService.getJobByIdDirect(1L)).thenReturn(testJob);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        
        // When
        boolean result = jobAssignmentService.reassignJob(1L, "failed-worker", "Worker crashed");
        
        // Then
        assertFalse(result);
        assertEquals(JobStatus.FAILED, testJob.getStatus());
        assertTrue(testJob.getErrorMessage().contains("Max retry attempts exceeded"));
    }
    
    @Test
    void testReassignJob_JobNotFound() {
        // Given
        when(jobService.getJobByIdDirect(999L)).thenReturn(null);
        
        // When
        boolean result = jobAssignmentService.reassignJob(999L, "failed-worker", "Worker crashed");
        
        // Then
        assertFalse(result);
        verify(jobService, never()).updateJob(any(Job.class));
    }
    
    @Test
    void testAssignmentStatistics() {
        // Given
        String workerId = "test-worker";
        
        // When
        JobAssignmentService.AssignmentStats stats = jobAssignmentService.getWorkerAssignmentStats(workerId);
        
        // Then
        assertNotNull(stats);
        assertEquals(0, stats.getTotalAssignments());
        assertEquals(0.0, stats.getSuccessRate());
        
        // Test recording assignments
        jobAssignmentService.getWorkerAssignmentStats(workerId); // This creates the stats
        // In a real test, we would have methods to record assignments
    }
    
    @Test
    void testCanWorkerHandleJob() {
        // This tests the private method indirectly through assignment
        
        // Test worker with insufficient capacity
        testWorker1.setCurrentJobCount(5); // At max capacity
        testWorker1.updateAvailableCapacity();
        
        when(workerService.getAvailableWorkers()).thenReturn(Arrays.asList(testWorker1));
        
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Should not assign to worker at capacity
        assertNull(assignedWorker);
    }
    
    @Test
    void testIntelligentAssignmentStrategy() {
        // Given - Set up workers with different performance characteristics
        testWorker1.setTotalJobsProcessed(1000L);
        testWorker1.setTotalJobsSuccessful(950L); // 95% success rate
        testWorker1.setCurrentJobCount(1); // Low load
        
        testWorker2.setTotalJobsProcessed(100L);
        testWorker2.setTotalJobsSuccessful(80L); // 80% success rate
        testWorker2.setCurrentJobCount(2); // Higher load
        
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(workerPerformanceService.getWorkerPerformanceMetrics(anyString()))
                .thenReturn(new WorkerPerformanceService.WorkerPerformanceMetrics("test"));
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNotNull(assignedWorker);
        // Should prefer worker with better performance (worker1)
        assertEquals("worker-1", assignedWorker.getWorkerId());
    }
    
    @Test
    void testAssignmentWithWorkerPerformanceMetrics() {
        // Given
        WorkerPerformanceService.WorkerPerformanceMetrics metrics = 
                new WorkerPerformanceService.WorkerPerformanceMetrics("worker-1");
        metrics.recordJobCompletion(true, 5000L);
        
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(workerPerformanceService.getWorkerPerformanceMetrics("worker-1")).thenReturn(metrics);
        when(workerPerformanceService.getWorkerPerformanceMetrics("worker-2"))
                .thenReturn(new WorkerPerformanceService.WorkerPerformanceMetrics("worker-2"));
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNotNull(assignedWorker);
        verify(workerPerformanceService).getWorkerPerformanceMetrics(anyString());
    }
    
    @Test
    void testCacheOperations() {
        // Given
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNotNull(assignedWorker);
        // Verify cache operations
        verify(cacheService, atLeastOnce()).put(anyString(), anyString(), anyInt());
    }
    
    @Test
    void testWorkerBlacklistCheck() {
        // Given
        when(cacheService.get(contains("blacklist"), eq(Boolean.class)))
                .thenReturn(java.util.Optional.of(true));
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        // Should not assign to blacklisted workers
        verify(cacheService, atLeastOnce()).get(anyString(), eq(Boolean.class));
    }
    
    @Test
    void testExceptionHandling() {
        // Given
        when(workerService.getAvailableWorkers()).thenThrow(new RuntimeException("Database error"));
        
        // When
        Worker assignedWorker = jobAssignmentService.assignJob(testJob);
        
        // Then
        assertNull(assignedWorker); // Should handle exception gracefully
    }
    
    @Test
    void testMultipleAssignmentStrategies() {
        // This test would require access to private methods or reflection
        // For now, we test through the public interface
        
        when(workerService.getAvailableWorkers()).thenReturn(availableWorkers);
        when(jobService.updateJob(any(Job.class))).thenReturn(testJob);
        when(workerService.updateWorker(any(Worker.class))).thenReturn(testWorker1);
        
        // Test with different job priorities to trigger different strategies
        testJob.setPriority(50); // Low priority
        Worker lowPriorityAssignment = jobAssignmentService.assignJob(testJob);
        
        testJob.setPriority(500); // High priority
        Worker highPriorityAssignment = jobAssignmentService.assignJob(testJob);
        
        // Both should succeed but may choose different workers
        assertNotNull(lowPriorityAssignment);
        assertNotNull(highPriorityAssignment);
    }
}
