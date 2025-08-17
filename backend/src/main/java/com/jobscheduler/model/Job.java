package com.jobscheduler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs")
public class Job {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Job name is required")
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @NotNull(message = "Job status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;
    
    @NotNull(message = "Job priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    @Column(nullable = false)
    private Integer priority = 100; // Default priority
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "job_type")
    private String jobType;
    
    @Column(columnDefinition = "TEXT")
    private String parameters;
    
    // New fields for enhanced job model
    
    @Column(name = "estimated_duration_minutes")
    private Long estimatedDurationMinutes; // Duration in minutes
    
    @Column(name = "actual_duration_minutes")
    private Long actualDurationMinutes; // Actual execution time in minutes
    
    @ElementCollection
    @CollectionTable(name = "job_dependencies", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "dependency_job_id")
    private List<Long> dependencyJobIds = new ArrayList<>();
    
    // Worker assignment information
    @Column(name = "assigned_worker_id")
    private String assignedWorkerId;
    
    @Column(name = "assigned_worker_name")
    private String assignedWorkerName;
    
    @Column(name = "worker_assigned_at")
    private LocalDateTime workerAssignedAt;
    
    @Column(name = "worker_started_at")
    private LocalDateTime workerStartedAt;
    
    @Column(name = "worker_host")
    private String workerHost;
    
    @Column(name = "worker_port")
    private Integer workerPort;
    
    // Constructors
    public Job() {}
    
    public Job(String name, String description, JobStatus status) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.priority = 100; // Default priority
        this.dependencyJobIds = new ArrayList<>();
    }
    
    public Job(String name, String description, JobStatus status, Integer priority) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.dependencyJobIds = new ArrayList<>();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public JobStatus getStatus() {
        return status;
    }
    
    public void setStatus(JobStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }
    
    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Integer getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    
    public Integer getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public String getJobType() {
        return jobType;
    }
    
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    
    public String getParameters() {
        return parameters;
    }
    
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
    
    // New getters and setters for enhanced fields
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public Long getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }
    
    public void setEstimatedDurationMinutes(Long estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }
    
    public Long getActualDurationMinutes() {
        return actualDurationMinutes;
    }
    
    public void setActualDurationMinutes(Long actualDurationMinutes) {
        this.actualDurationMinutes = actualDurationMinutes;
    }
    
    public List<Long> getDependencyJobIds() {
        return dependencyJobIds;
    }
    
    public void setDependencyJobIds(List<Long> dependencyJobIds) {
        this.dependencyJobIds = dependencyJobIds != null ? dependencyJobIds : new ArrayList<>();
    }
    
    public void addDependency(Long jobId) {
        if (this.dependencyJobIds == null) {
            this.dependencyJobIds = new ArrayList<>();
        }
        if (!this.dependencyJobIds.contains(jobId)) {
            this.dependencyJobIds.add(jobId);
        }
    }
    
    public void removeDependency(Long jobId) {
        if (this.dependencyJobIds != null) {
            this.dependencyJobIds.remove(jobId);
        }
    }
    
    public boolean hasDependencies() {
        return dependencyJobIds != null && !dependencyJobIds.isEmpty();
    }
    
    public String getAssignedWorkerId() {
        return assignedWorkerId;
    }
    
    public void setAssignedWorkerId(String assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }
    
    public String getAssignedWorkerName() {
        return assignedWorkerName;
    }
    
    public void setAssignedWorkerName(String assignedWorkerName) {
        this.assignedWorkerName = assignedWorkerName;
    }
    
    public LocalDateTime getWorkerAssignedAt() {
        return workerAssignedAt;
    }
    
    public void setWorkerAssignedAt(LocalDateTime workerAssignedAt) {
        this.workerAssignedAt = workerAssignedAt;
    }
    
    public LocalDateTime getWorkerStartedAt() {
        return workerStartedAt;
    }
    
    public void setWorkerStartedAt(LocalDateTime workerStartedAt) {
        this.workerStartedAt = workerStartedAt;
    }
    
    public String getWorkerHost() {
        return workerHost;
    }
    
    public void setWorkerHost(String workerHost) {
        this.workerHost = workerHost;
    }
    
    public Integer getWorkerPort() {
        return workerPort;
    }
    
    public void setWorkerPort(Integer workerPort) {
        this.workerPort = workerPort;
    }
    
    // Utility methods
    
    public boolean isAssignedToWorker() {
        return assignedWorkerId != null && !assignedWorkerId.trim().isEmpty();
    }
    
    public void assignToWorker(String workerId, String workerName, String host, Integer port) {
        this.assignedWorkerId = workerId;
        this.assignedWorkerName = workerName;
        this.workerHost = host;
        this.workerPort = port;
        this.workerAssignedAt = LocalDateTime.now();
    }
    
    public void unassignFromWorker() {
        this.assignedWorkerId = null;
        this.assignedWorkerName = null;
        this.workerHost = null;
        this.workerPort = null;
        this.workerAssignedAt = null;
        this.workerStartedAt = null;
    }
    
    public Duration getEstimatedDuration() {
        return estimatedDurationMinutes != null ? Duration.ofMinutes(estimatedDurationMinutes) : null;
    }
    
    public void setEstimatedDuration(Duration duration) {
        this.estimatedDurationMinutes = duration != null ? duration.toMinutes() : null;
    }
    
    public Duration getActualDuration() {
        return actualDurationMinutes != null ? Duration.ofMinutes(actualDurationMinutes) : null;
    }
    
    public void setActualDuration(Duration duration) {
        this.actualDurationMinutes = duration != null ? duration.toMinutes() : null;
    }
    
    public void calculateActualDuration() {
        if (startedAt != null && completedAt != null) {
            Duration duration = Duration.between(startedAt, completedAt);
            this.actualDurationMinutes = duration.toMinutes();
        }
    }
    
    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", priority=" + priority +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", assignedWorkerId='" + assignedWorkerId + '\'' +
                ", dependencyCount=" + (dependencyJobIds != null ? dependencyJobIds.size() : 0) +
                ", estimatedDurationMinutes=" + estimatedDurationMinutes +
                '}';
    }
}
