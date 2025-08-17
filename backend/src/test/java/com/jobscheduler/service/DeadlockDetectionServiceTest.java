package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobDependency;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.JobDependencyRepository;
import com.jobscheduler.service.DeadlockDetectionService.DeadlockDetectionResult;
import com.jobscheduler.service.DeadlockDetectionService.DependencyValidationResult;
import com.jobscheduler.service.DeadlockDetectionService.DeadlockCycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadlockDetectionServiceTest {
    
    @Mock
    private JobRepository jobRepository;
    
    @Mock
    private JobDependencyRepository dependencyRepository;
    
    @Mock
    private DependencyGraphService graphService;
    
    @InjectMocks
    private DeadlockDetectionService deadlockDetectionService;
    
    private Job job1, job2, job3, job4;
    private JobDependency dep1, dep2, dep3;
    
    @BeforeEach
    void setUp() {
        // Create test jobs
        job1 = createTestJob(1L, "Job1");
        job2 = createTestJob(2L, "Job2");
        job3 = createTestJob(3L, "Job3");
        job4 = createTestJob(4L, "Job4");
        
        // Create test dependencies
        dep1 = createTestDependency(1L, 2L, 1L); // Job2 -> Job1
        dep2 = createTestDependency(2L, 3L, 2L); // Job3 -> Job2
        dep3 = createTestDependency(3L, 1L, 3L); // Job1 -> Job3 (creates cycle)
    }
    
    @Test
    void testDetectDeadlocks_NoCycle() {
        // Setup: No cyclic dependencies
        List<JobDependency> dependencies = Arrays.asList(dep1, dep2);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        when(dependencyRepository.findCircularDependencies()).thenReturn(Collections.emptyList());
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job1));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job2));
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job3));
        
        // Execute
        DeadlockDetectionResult result = deadlockDetectionService.detectDeadlocks();
        
        // Verify
        assertFalse(result.hasDeadlock());
        assertEquals(0, result.getCycleCount());
        assertNotNull(result.getStatistics());
        assertTrue(result.getStatistics().containsKey("totalDependencies"));
        assertEquals(2, result.getStatistics().get("totalDependencies"));
    }
    
    @Test
    void testDetectDeadlocks_WithCycle() {
        // Setup: Cyclic dependencies (Job1 -> Job2 -> Job3 -> Job1)
        List<JobDependency> dependencies = Arrays.asList(dep1, dep2, dep3);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        when(dependencyRepository.findCircularDependencies()).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job1));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job2));
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job3));
        
        // Execute
        DeadlockDetectionResult result = deadlockDetectionService.detectDeadlocks();
        
        // Verify
        assertTrue(result.hasDeadlock());
        assertTrue(result.getCycleCount() > 0);
        assertNotNull(result.getCycles());
        
        // Check cycle details
        DeadlockCycle cycle = result.getCycles().get(0);
        assertNotNull(cycle);
        assertTrue(cycle.getCycleLength() >= 3);
        assertTrue(cycle.getJobIds().contains(1L));
        assertTrue(cycle.getJobIds().contains(2L));
        assertTrue(cycle.getJobIds().contains(3L));
    }
    
    @Test
    void testValidateDependencyAddition_SelfDependency() {
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(1L, 1L);
        
        // Verify
        assertFalse(result.isValid());
        assertEquals("Self-dependency not allowed", result.getMessage());
        assertEquals(10, result.getSeverity());
        assertTrue(result.getAffectedJobIds().contains(1L));
    }
    
    @Test
    void testValidateDependencyAddition_JobsNotExist() {
        // Setup
        when(jobRepository.existsById(1L)).thenReturn(false);
        when(jobRepository.existsById(2L)).thenReturn(true);
        
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(1L, 2L);
        
        // Verify
        assertFalse(result.isValid());
        assertEquals("One or both jobs do not exist", result.getMessage());
        assertEquals(9, result.getSeverity());
    }
    
    @Test
    void testValidateDependencyAddition_DependencyExists() {
        // Setup
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(jobRepository.existsById(2L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(1L, 2L)).thenReturn(true);
        
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(1L, 2L);
        
        // Verify
        assertTrue(result.isValid());
        assertEquals("Dependency already exists", result.getMessage());
        assertEquals(1, result.getSeverity());
    }
    
    @Test
    void testValidateDependencyAddition_WouldCreateCycle() {
        // Setup: Job1 -> Job2 -> Job3, trying to add Job3 -> Job1
        List<JobDependency> existingDeps = Arrays.asList(dep1, dep2);
        when(jobRepository.existsById(3L)).thenReturn(true);
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(3L, 1L)).thenReturn(false);
        when(dependencyRepository.findAll()).thenReturn(existingDeps);
        
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(3L, 1L);
        
        // Verify
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("cycle"));
        assertEquals(8, result.getSeverity());
        assertTrue(result.isHighSeverity());
    }
    
    @Test
    void testValidateDependencyAddition_ValidDependency() {
        // Setup
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(jobRepository.existsById(4L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(1L, 4L)).thenReturn(false);
        when(dependencyRepository.findAll()).thenReturn(Arrays.asList(dep1, dep2));
        
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(1L, 4L);
        
        // Verify
        assertTrue(result.isValid());
        assertEquals("Dependency is valid", result.getMessage());
        assertEquals(0, result.getSeverity());
        assertFalse(result.isHighSeverity());
    }
    
    @Test
    void testValidateDependencyAddition_WithWarnings() {
        // Setup: Deep dependency chain
        List<JobDependency> longChain = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            JobDependency dep = createTestDependency((long) i, (long) (i + 1), (long) i);
            longChain.add(dep);
        }
        
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(jobRepository.existsById(16L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(1L, 16L)).thenReturn(false);
        when(dependencyRepository.findAll()).thenReturn(longChain);
        
        // Execute
        DependencyValidationResult result = deadlockDetectionService.validateDependencyAddition(1L, 16L);
        
        // Verify
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Deep dependency chain")));
    }
    
    @Test
    void testDetectDeadlocks_EmptyDependencies() {
        // Setup
        when(dependencyRepository.findAll()).thenReturn(Collections.emptyList());
        
        // Execute
        DeadlockDetectionResult result = deadlockDetectionService.detectDeadlocks();
        
        // Verify
        assertFalse(result.hasDeadlock());
        assertEquals(0, result.getCycleCount());
        assertEquals(0, result.getStatistics().get("totalDependencies"));
    }
    
    @Test
    void testDetectDeadlocks_DatabaseCycleDetectionFails() {
        // Setup
        List<JobDependency> dependencies = Arrays.asList(dep1, dep2);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        when(dependencyRepository.findCircularDependencies()).thenThrow(new RuntimeException("Database error"));
        when(jobRepository.findById(anyLong())).thenReturn(Optional.of(job1));
        
        // Execute
        DeadlockDetectionResult result = deadlockDetectionService.detectDeadlocks();
        
        // Verify
        assertFalse(result.hasDeadlock());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Database cycle detection failed")));
    }
    
    @Test
    void testClearCache() {
        // Execute
        deadlockDetectionService.clearCache();
        
        // Verify cache statistics
        Map<String, Object> stats = deadlockDetectionService.getCacheStatistics();
        assertEquals(0, stats.get("cacheSize"));
    }
    
    @Test
    void testGetCacheStatistics() {
        // Execute
        Map<String, Object> stats = deadlockDetectionService.getCacheStatistics();
        
        // Verify
        assertNotNull(stats);
        assertTrue(stats.containsKey("cacheSize"));
        assertTrue(stats.containsKey("validEntries"));
        assertTrue(stats.containsKey("expiredEntries"));
        assertTrue(stats.containsKey("validityMs"));
    }
    
    @Test
    void testDeadlockCycle_CreationAndGetters() {
        // Setup
        List<Long> jobIds = Arrays.asList(1L, 2L, 3L);
        List<String> jobNames = Arrays.asList("Job1", "Job2", "Job3");
        List<Long> depIds = Arrays.asList(1L, 2L, 3L);
        
        // Execute
        DeadlockCycle cycle = new DeadlockCycle(jobIds, jobNames, 8, "Test cycle", depIds);
        
        // Verify
        assertEquals(3, cycle.getCycleLength());
        assertEquals(8, cycle.getSeverity());
        assertTrue(cycle.isHighSeverity());
        assertEquals("Test cycle", cycle.getDescription());
        assertEquals(jobIds, cycle.getJobIds());
        assertEquals(jobNames, cycle.getJobNames());
        assertEquals(depIds, cycle.getDependencyIds());
    }
    
    @Test
    void testDeadlockDetectionResult_CreationAndGetters() {
        // Setup
        List<DeadlockCycle> cycles = Arrays.asList(
            new DeadlockCycle(Arrays.asList(1L, 2L), Arrays.asList("Job1", "Job2"), 5, "Test", null)
        );
        List<String> warnings = Arrays.asList("Warning 1");
        Map<String, Object> stats = Map.of("test", "value");
        
        // Execute
        DeadlockDetectionResult result = new DeadlockDetectionResult(true, cycles, warnings, stats);
        
        // Verify
        assertTrue(result.hasDeadlock());
        assertEquals(1, result.getCycleCount());
        assertTrue(result.hasWarnings());
        assertEquals(cycles, result.getCycles());
        assertEquals(warnings, result.getWarnings());
        assertEquals(stats, result.getStatistics());
        assertNotNull(result.getDetectionTime());
    }
    
    @Test
    void testDependencyValidationResult_CreationAndGetters() {
        // Setup
        List<Long> affectedJobs = Arrays.asList(1L, 2L);
        List<String> warnings = Arrays.asList("Warning");
        
        // Execute
        DependencyValidationResult result = new DependencyValidationResult(
            false, "Test message", affectedJobs, 9, warnings);
        
        // Verify
        assertFalse(result.isValid());
        assertEquals("Test message", result.getMessage());
        assertEquals(9, result.getSeverity());
        assertTrue(result.isHighSeverity());
        assertTrue(result.hasWarnings());
        assertEquals(affectedJobs, result.getAffectedJobIds());
        assertEquals(warnings, result.getWarnings());
    }
    
    // Helper methods
    
    private Job createTestJob(Long id, String name) {
        Job job = new Job();
        job.setId(id);
        job.setName(name);
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }
    
    private JobDependency createTestDependency(Long jobId, Long dependencyJobId, Long id) {
        JobDependency dep = new JobDependency();
        dep.setId(id);
        dep.setJobId(jobId);
        dep.setDependencyJobId(dependencyJobId);
        dep.setIsSatisfied(false);
        dep.setCreatedAt(LocalDateTime.now());
        return dep;
    }
}
