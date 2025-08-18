package com.jobscheduler.service;

import com.jobscheduler.model.Worker;
import com.jobscheduler.model.Worker.WorkerStatus;
import com.jobscheduler.repository.WorkerRepository;
import com.jobscheduler.service.WorkerRegistrationService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerRegistrationServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private WorkerRegistrationService workerRegistrationService;

    private WorkerRegistrationRequest validRegistrationRequest;
    private Worker existingWorker;

    @BeforeEach
    void setUp() {
        validRegistrationRequest = new WorkerRegistrationRequest();
        validRegistrationRequest.setWorkerId("worker-001");
        validRegistrationRequest.setName("Test Worker");
        validRegistrationRequest.setHostName("localhost");
        validRegistrationRequest.setHostAddress("127.0.0.1");
        validRegistrationRequest.setPort(8080);
        validRegistrationRequest.setMaxConcurrentJobs(5);
        validRegistrationRequest.setCapabilities("{\"java\": true, \"python\": false}");
        validRegistrationRequest.setTags("test,development");
        validRegistrationRequest.setVersion("1.0.0");
        validRegistrationRequest.setPriorityThreshold(100);
        validRegistrationRequest.setWorkerLoadFactor(1.0);

        existingWorker = new Worker("worker-001", "Existing Worker");
        existingWorker.setId(1L);
        existingWorker.setStatus(WorkerStatus.ACTIVE);
        existingWorker.setLastHeartbeat(LocalDateTime.now());
    }

    @Test
    void testRegisterNewWorker_Success() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.empty());
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Worker registered successfully", result.getMessage());
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository).save(any(Worker.class));
        verify(cacheService).cacheWorker(eq("worker-001"), any(Worker.class), eq(600));
    }

    @Test
    void testRegisterExistingWorker_UpdateSuccess() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Worker registered successfully", result.getMessage());
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository).save(any(Worker.class));
    }

    @Test
    void testRegisterWorker_ValidationFailure_EmptyWorkerId() {
        // Given
        validRegistrationRequest.setWorkerId("");

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Worker ID is required"));
        assertNull(result.getWorker());
        
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    void testRegisterWorker_ValidationFailure_InvalidCapacity() {
        // Given
        validRegistrationRequest.setMaxConcurrentJobs(-1);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Max concurrent jobs must be positive"));
        assertNull(result.getWorker());
    }

    @Test
    void testRegisterWorker_ValidationFailure_ExcessiveCapacity() {
        // Given
        validRegistrationRequest.setMaxConcurrentJobs(150);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Max concurrent jobs cannot exceed 100"));
        assertNull(result.getWorker());
    }

    @Test
    void testRegisterWorker_ValidationFailure_InvalidPort() {
        // Given
        validRegistrationRequest.setPort(70000);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Port must be between 1 and 65535"));
        assertNull(result.getWorker());
    }

    @Test
    void testRegisterWorker_ValidationFailure_InvalidLoadFactor() {
        // Given
        validRegistrationRequest.setWorkerLoadFactor(3.0);

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Worker load factor must be between 0.1 and 2.0"));
        assertNull(result.getWorker());
    }

    @Test
    void testProcessHeartbeat_Success() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        HeartbeatRequest heartbeatRequest = new HeartbeatRequest();
        heartbeatRequest.setStatus(WorkerStatus.ACTIVE);
        heartbeatRequest.setCurrentJobCount(2);
        heartbeatRequest.setAvailableCapacity(3);
        heartbeatRequest.setCpuUsage(45.5);
        heartbeatRequest.setMemoryUsage(67.8);

        // When
        HeartbeatResult result = workerRegistrationService.processHeartbeat("worker-001", heartbeatRequest);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Heartbeat processed successfully", result.getMessage());
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository).save(any(Worker.class));
        verify(cacheService).cacheWorker(eq("worker-001"), any(Worker.class), eq(300));
    }

    @Test
    void testProcessHeartbeat_WorkerNotFound() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.empty());

        HeartbeatRequest heartbeatRequest = new HeartbeatRequest();

        // When
        HeartbeatResult result = workerRegistrationService.processHeartbeat("worker-001", heartbeatRequest);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Worker not registered", result.getMessage());
        assertNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    void testProcessHeartbeat_EmptyRequest() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        // When
        HeartbeatResult result = workerRegistrationService.processHeartbeat("worker-001", new HeartbeatRequest());

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Heartbeat processed successfully", result.getMessage());
        assertNotNull(result.getWorker());
    }

    @Test
    void testDeregisterWorker_Success() {
        // Given
        existingWorker.setCurrentJobCount(0);
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        DeregistrationRequest request = new DeregistrationRequest(false, "Manual deregistration");

        // When
        DeregistrationResult result = workerRegistrationService.deregisterWorker("worker-001", request);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Worker deregistered successfully", result.getMessage());
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository).save(any(Worker.class));
        verify(cacheService).evictWorkerFromCache("worker-001");
    }

    @Test
    void testDeregisterWorker_WithActiveJobs_NoForce() {
        // Given
        existingWorker.setCurrentJobCount(2);
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));

        DeregistrationRequest request = new DeregistrationRequest(false, "Manual deregistration");

        // When
        DeregistrationResult result = workerRegistrationService.deregisterWorker("worker-001", request);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("has 2 active jobs"));
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    void testDeregisterWorker_WithActiveJobs_Force() {
        // Given
        existingWorker.setCurrentJobCount(2);
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(any(Worker.class))).thenReturn(existingWorker);

        DeregistrationRequest request = new DeregistrationRequest(true, "Force deregistration");

        // When
        DeregistrationResult result = workerRegistrationService.deregisterWorker("worker-001", request);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Worker deregistered successfully", result.getMessage());
        assertNotNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository).save(any(Worker.class));
    }

    @Test
    void testDeregisterWorker_WorkerNotFound() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.empty());

        DeregistrationRequest request = new DeregistrationRequest(false, "Manual deregistration");

        // When
        DeregistrationResult result = workerRegistrationService.deregisterWorker("worker-001", request);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Worker not found", result.getMessage());
        assertNull(result.getWorker());
        
        verify(workerRepository).findByWorkerId("worker-001");
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    void testGetWorkerHealthReport_Success() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));

        // When
        WorkerHealthReport report = workerRegistrationService.getWorkerHealthReport("worker-001");

        // Then
        assertEquals("worker-001", report.getWorkerId());
        assertTrue(report.isHealthy());
        assertEquals("Healthy", report.getDescription());
        assertNotNull(report.getWorker());
        assertNotNull(report.getMetrics());
        
        verify(workerRepository).findByWorkerId("worker-001");
    }

    @Test
    void testGetWorkerHealthReport_WorkerNotFound() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.empty());

        // When
        WorkerHealthReport report = workerRegistrationService.getWorkerHealthReport("worker-001");

        // Then
        assertEquals("worker-001", report.getWorkerId());
        assertFalse(report.isHealthy());
        assertEquals("Worker not found", report.getDescription());
        assertNull(report.getWorker());
        assertNull(report.getMetrics());
    }

    @Test
    void testGetWorkerHealthReport_UnhealthyWorker() {
        // Given
        existingWorker.setLastHeartbeat(LocalDateTime.now().minusMinutes(10)); // Old heartbeat
        existingWorker.setStatus(WorkerStatus.ERROR);
        when(workerRepository.findByWorkerId("worker-001")).thenReturn(Optional.of(existingWorker));

        // When
        WorkerHealthReport report = workerRegistrationService.getWorkerHealthReport("worker-001");

        // Then
        assertEquals("worker-001", report.getWorkerId());
        assertFalse(report.isHealthy());
        assertTrue(report.getDescription().contains("error") || report.getDescription().contains("timeout"));
        assertNotNull(report.getWorker());
    }

    @Test
    void testGetSystemHealthStatistics() {
        // Given
        Worker worker1 = new Worker("worker-001", "Worker 1");
        worker1.setStatus(WorkerStatus.ACTIVE);
        worker1.setMaxConcurrentJobs(5);
        worker1.setCurrentJobCount(2);

        Worker worker2 = new Worker("worker-002", "Worker 2");
        worker2.setStatus(WorkerStatus.ERROR);
        worker2.setMaxConcurrentJobs(3);
        worker2.setCurrentJobCount(0);

        Worker worker3 = new Worker("worker-003", "Worker 3");
        worker3.setStatus(WorkerStatus.ACTIVE);
        worker3.setMaxConcurrentJobs(4);
        worker3.setCurrentJobCount(1);

        when(workerRepository.findAll()).thenReturn(Arrays.asList(worker1, worker2, worker3));

        // When
        SystemHealthStatistics stats = workerRegistrationService.getSystemHealthStatistics();

        // Then
        assertEquals(3, stats.getTotalWorkers());
        assertEquals(2, stats.getActiveWorkers());
        assertEquals(1, stats.getFailedWorkers());
        assertEquals(12, stats.getTotalCapacity()); // 5 + 3 + 4
        assertEquals(3, stats.getUsedCapacity()); // 2 + 0 + 1
        assertEquals(9, stats.getAvailableCapacity()); // 12 - 3
        assertTrue(stats.getSystemHealthPercentage() > 0);
        assertNotNull(stats.getTimestamp());
        
        verify(workerRepository).findAll();
    }

    @Test
    void testRegisterWorker_TooManyAttempts() {
        // Simulate multiple failed registration attempts
        // This would require access to the internal registration attempts map
        // For now, we'll test the basic validation failure scenario
        
        // Given
        validRegistrationRequest.setWorkerId(""); // Invalid to cause failure

        // When - attempt registration multiple times
        for (int i = 0; i < 5; i++) {
            WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);
            assertFalse(result.isSuccess());
        }

        // The service should eventually block further attempts
        // This would need to be implemented based on the internal state management
    }

    @Test
    void testWorkerValidationResult() {
        // Test validation result class
        WorkerValidationResult validResult = new WorkerValidationResult(true, "Validation passed");
        assertTrue(validResult.isValid());
        assertEquals("Validation passed", validResult.getMessage());

        WorkerValidationResult invalidResult = new WorkerValidationResult(false, "Validation failed");
        assertFalse(invalidResult.isValid());
        assertEquals("Validation failed", invalidResult.getMessage());
    }

    @Test
    void testHeartbeatRequest() {
        // Test heartbeat request class
        HeartbeatRequest request = new HeartbeatRequest();
        request.setStatus(WorkerStatus.ACTIVE);
        request.setCurrentJobCount(3);
        request.setAvailableCapacity(2);
        request.setCpuUsage(55.5);
        request.setMemoryUsage(70.2);
        request.setErrorCount(1);
        request.setMessage("Test message");

        assertEquals(WorkerStatus.ACTIVE, request.getStatus());
        assertEquals(3, request.getCurrentJobCount());
        assertEquals(2, request.getAvailableCapacity());
        assertEquals(55.5, request.getCpuUsage());
        assertEquals(70.2, request.getMemoryUsage());
        assertEquals(1, request.getErrorCount());
        assertEquals("Test message", request.getMessage());
    }

    @Test
    void testDeregistrationRequest() {
        // Test deregistration request class
        DeregistrationRequest request = new DeregistrationRequest();
        assertFalse(request.isForceDeregister());
        assertNull(request.getReason());

        request.setForceDeregister(true);
        request.setReason("Force cleanup");

        assertTrue(request.isForceDeregister());
        assertEquals("Force cleanup", request.getReason());

        // Test constructor
        DeregistrationRequest constructedRequest = new DeregistrationRequest(true, "Test reason");
        assertTrue(constructedRequest.isForceDeregister());
        assertEquals("Test reason", constructedRequest.getReason());
    }

    @Test
    void testWorkerHealthMetrics() {
        // Test worker health metrics class
        LocalDateTime now = LocalDateTime.now();
        WorkerHealthMetrics metrics = new WorkerHealthMetrics(
            true, now, now, 100L, 99.5, 75.0, 95.2, 45.5, 68.3
        );

        assertTrue(metrics.isHealthy());
        assertEquals(now, metrics.getLastHeartbeat());
        assertEquals(now, metrics.getLastHealthCheck());
        assertEquals(100L, metrics.getHeartbeatCount());
        assertEquals(99.5, metrics.getUptimePercentage());
        assertEquals(75.0, metrics.getLoadPercentage());
        assertEquals(95.2, metrics.getSuccessRate());
        assertEquals(45.5, metrics.getCpuUsage());
        assertEquals(68.3, metrics.getMemoryUsage());
    }

    @Test
    void testWorkerHealthStatus() {
        // Test worker health status class
        WorkerHealthStatus status = new WorkerHealthStatus();
        assertTrue(status.isHealthy());
        assertEquals(0, status.getErrorCount());

        status.setHealthy(false);
        status.setCpuUsage(80.5);
        status.setMemoryUsage(90.2);
        status.setErrorCount(3);
        status.setLastError("Test error");
        status.setLastErrorTime(LocalDateTime.now());

        assertFalse(status.isHealthy());
        assertEquals(80.5, status.getCpuUsage());
        assertEquals(90.2, status.getMemoryUsage());
        assertEquals(3, status.getErrorCount());
        assertEquals("Test error", status.getLastError());
        assertNotNull(status.getLastErrorTime());
    }

    @Test
    void testSystemHealthStatistics() {
        // Test system health statistics class
        LocalDateTime timestamp = LocalDateTime.now();
        SystemHealthStatistics stats = new SystemHealthStatistics(
            10, 8, 2, 1, 1, 0, 50, 30, 20, 80.0, 2, timestamp
        );

        assertEquals(10, stats.getTotalWorkers());
        assertEquals(8, stats.getActiveWorkers());
        assertEquals(2, stats.getIdleWorkers());
        assertEquals(1, stats.getBusyWorkers());
        assertEquals(1, stats.getFailedWorkers());
        assertEquals(0, stats.getMaintenanceWorkers());
        assertEquals(50, stats.getTotalCapacity());
        assertEquals(30, stats.getUsedCapacity());
        assertEquals(20, stats.getAvailableCapacity());
        assertEquals(80.0, stats.getSystemHealthPercentage());
        assertEquals(2, stats.getUnhealthyWorkers());
        assertEquals(timestamp, stats.getTimestamp());
    }

    @Test
    void testRegistrationExceptionHandling() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenThrow(new RuntimeException("Database error"));

        // When
        WorkerRegistrationResult result = workerRegistrationService.registerWorker(validRegistrationRequest);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Registration failed"));
        assertNull(result.getWorker());
    }

    @Test
    void testHeartbeatExceptionHandling() {
        // Given
        when(workerRepository.findByWorkerId("worker-001")).thenThrow(new RuntimeException("Database error"));

        HeartbeatRequest request = new HeartbeatRequest();

        // When
        HeartbeatResult result = workerRegistrationService.processHeartbeat("worker-001", request);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Heartbeat processing failed"));
        assertNull(result.getWorker());
    }
}
