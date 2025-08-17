package com.jobscheduler.service;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobDependency;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.repository.JobDependencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyGraphServiceTest {
    
    @Mock
    private JobRepository jobRepository;
    
    @Mock
    private JobDependencyRepository dependencyRepository;
    
    @Mock
    private JobService jobService;
    
    @InjectMocks
    private DependencyGraphService dependencyGraphService;
    
    @BeforeEach
    void setUp() {
        // Reset service state before each test
        dependencyGraphService.buildDependencyGraph();
    }
    
    @Test
    void testBuildDependencyGraph() {
        // Arrange
        List<Job> jobs = Arrays.asList(
            createJob(1L, "Job1"),
            createJob(2L, "Job2"),
            createJob(3L, "Job3")
        );
        
        List<JobDependency> dependencies = Arrays.asList(
            createDependency(2L, 1L), // Job 2 depends on Job 1
            createDependency(3L, 2L)  // Job 3 depends on Job 2
        );
        
        when(jobRepository.findAll()).thenReturn(jobs);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        
        // Act
        dependencyGraphService.buildDependencyGraph();
        
        // Assert
        verify(jobRepository).findAll();
        verify(dependencyRepository).findAll();
        
        // Test graph structure
        Set<Long> job2Dependencies = dependencyGraphService.getJobDependencies(2L);
        assertTrue(job2Dependencies.contains(1L));
        
        Set<Long> job1Dependents = dependencyGraphService.getJobDependents(1L);
        assertTrue(job1Dependents.contains(2L));
    }
    
    @Test
    void testAddDependencySuccess() {
        // Arrange
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(jobRepository.existsById(2L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(2L, 1L)).thenReturn(false);
        when(dependencyRepository.save(any(JobDependency.class))).thenReturn(new JobDependency());
        
        // Act
        boolean result = dependencyGraphService.addDependency(2L, 1L);
        
        // Assert
        assertTrue(result);
        verify(dependencyRepository).save(any(JobDependency.class));
    }
    
    @Test
    void testAddDependencyWithCycle() {
        // Arrange
        when(jobRepository.existsById(1L)).thenReturn(true);
        when(jobRepository.existsById(2L)).thenReturn(true);
        when(dependencyRepository.existsByJobIdAndDependencyJobId(1L, 2L)).thenReturn(false);
        
        // Setup existing dependency: Job 2 depends on Job 1
        List<JobDependency> existingDeps = Arrays.asList(createDependency(2L, 1L));
        when(dependencyRepository.findAll()).thenReturn(existingDeps);
        dependencyGraphService.buildDependencyGraph();
        
        // Act - Try to add Job 1 depends on Job 2 (would create cycle)
        boolean result = dependencyGraphService.addDependency(1L, 2L);
        
        // Assert
        assertFalse(result);
        verify(dependencyRepository, never()).save(any(JobDependency.class));
    }
    
    @Test
    void testTopologicalSort() {
        // Arrange
        List<Job> jobs = Arrays.asList(
            createJob(1L, "Job1"),
            createJob(2L, "Job2"),
            createJob(3L, "Job3")
        );
        
        List<JobDependency> dependencies = Arrays.asList(
            createDependency(2L, 1L), // Job 2 depends on Job 1
            createDependency(3L, 2L)  // Job 3 depends on Job 2
        );
        
        when(jobRepository.findAll()).thenReturn(jobs);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        
        dependencyGraphService.buildDependencyGraph();
        
        // Act
        List<Long> sortedJobs = dependencyGraphService.topologicalSort();
        
        // Assert
        assertFalse(sortedJobs.isEmpty());
        assertEquals(3, sortedJobs.size());
        
        // Job 1 should come before Job 2
        int job1Index = sortedJobs.indexOf(1L);
        int job2Index = sortedJobs.indexOf(2L);
        int job3Index = sortedJobs.indexOf(3L);
        
        assertTrue(job1Index < job2Index);
        assertTrue(job2Index < job3Index);
    }
    
    @Test
    void testGetJobsReadyForExecution() {
        // Arrange
        List<Job> jobs = Arrays.asList(
            createPendingJob(1L, "Job1"),
            createPendingJob(2L, "Job2"),
            createPendingJob(3L, "Job3")
        );
        
        List<JobDependency> dependencies = Arrays.asList(
            createDependency(2L, 1L), // Job 2 depends on Job 1
            createDependency(3L, 2L)  // Job 3 depends on Job 2
        );
        
        when(jobRepository.findAll()).thenReturn(jobs);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        when(jobService.getJobById(1L)).thenReturn(jobs.get(0));
        
        dependencyGraphService.buildDependencyGraph();
        
        // Act
        List<Job> readyJobs = dependencyGraphService.getJobsReadyForExecution();
        
        // Assert
        assertEquals(1, readyJobs.size());
        assertEquals(1L, readyJobs.get(0).getId());
    }
    
    @Test
    void testUpdateJobCompletion() {
        // Arrange
        List<Job> jobs = Arrays.asList(
            createPendingJob(1L, "Job1"),
            createPendingJob(2L, "Job2"),
            createPendingJob(3L, "Job3")
        );
        
        List<JobDependency> dependencies = Arrays.asList(
            createDependency(2L, 1L), // Job 2 depends on Job 1
            createDependency(3L, 1L)  // Job 3 depends on Job 1
        );
        
        when(jobRepository.findAll()).thenReturn(jobs);
        when(dependencyRepository.findAll()).thenReturn(dependencies);
        when(dependencyRepository.findByDependencyJobIdAndIsSatisfied(1L, false))
            .thenReturn(dependencies);
        when(jobService.getJobById(2L)).thenReturn(jobs.get(1));
        when(jobService.getJobById(3L)).thenReturn(jobs.get(2));
        
        dependencyGraphService.buildDependencyGraph();
        
        // Act
        List<Job> newlyReady = dependencyGraphService.updateJobCompletion(1L);
        
        // Assert
        assertEquals(2, newlyReady.size());
        assertTrue(newlyReady.stream().anyMatch(job -> job.getId().equals(2L)));
        assertTrue(newlyReady.stream().anyMatch(job -> job.getId().equals(3L)));
    }
    
    @Test
    void testValidateDependencyGraph() {
        // Arrange
        when(jobRepository.findAll()).thenReturn(Arrays.asList(createJob(1L, "Job1")));
        when(dependencyRepository.findAll()).thenReturn(Collections.emptyList());
        when(jobRepository.existsById(anyLong())).thenReturn(true);
        
        dependencyGraphService.buildDependencyGraph();
        
        // Act
        List<String> errors = dependencyGraphService.validateDependencyGraph();
        
        // Assert
        assertTrue(errors.isEmpty());
    }
    
    private Job createJob(Long id, String name) {
        Job job = new Job();
        job.setId(id);
        job.setName(name);
        job.setStatus(Job.JobStatus.PENDING);
        return job;
    }
    
    private Job createPendingJob(Long id, String name) {
        Job job = createJob(id, name);
        job.setStatus(Job.JobStatus.PENDING);
        return job;
    }
    
    private JobDependency createDependency(Long jobId, Long dependencyJobId) {
        JobDependency dependency = new JobDependency();
        dependency.setJobId(jobId);
        dependency.setDependencyJobId(dependencyJobId);
        dependency.setIsSatisfied(false);
        return dependency;
    }
}
