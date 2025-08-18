package com.jobscheduler.dto;

import com.jobscheduler.model.JobPriority;
import com.jobscheduler.model.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for Job API endpoints
 */
public class JobDTOs {
    
    /**
     * Request DTO for creating a new job with dependencies
     */
    public static class CreateJobRequest {
        @NotBlank(message = "Job name is required")
        private String name;
        
        @NotBlank(message = "Job description is required")
        private String description;
        
        @NotNull(message = "Job priority is required")
        private JobPriority priority;
        
        private String jobType;
        private String parameters;
        private Integer maxRetries = 3;
        private Integer timeoutMinutes = 60;
        private List<String> tags;
        private List<String> requiredCapabilities;
        private List<Long> dependencies; // Job IDs this job depends on
        private Map<String, Object> metadata;
        private LocalDateTime scheduledAt;
        private String workerId; // Optional: assign to specific worker
        
        // Constructors
        public CreateJobRequest() {}
        
        public CreateJobRequest(String name, String description, JobPriority priority) {
            this.name = name;
            this.description = description;
            this.priority = priority;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public JobPriority getPriority() { return priority; }
        public void setPriority(JobPriority priority) { this.priority = priority; }
        
        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }
        
        public String getParameters() { return parameters; }
        public void setParameters(String parameters) { this.parameters = parameters; }
        
        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
        
        public Integer getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        
        public List<String> getRequiredCapabilities() { return requiredCapabilities; }
        public void setRequiredCapabilities(List<String> requiredCapabilities) { 
            this.requiredCapabilities = requiredCapabilities; 
        }
        
        public List<Long> getDependencies() { return dependencies; }
        public void setDependencies(List<Long> dependencies) { this.dependencies = dependencies; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        
        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
    }
    
    /**
     * Request DTO for updating job priority or dependencies
     */
    public static class UpdateJobRequest {
        private JobPriority priority;
        private List<Long> dependencies;
        private String parameters;
        private Integer maxRetries;
        private Integer timeoutMinutes;
        private List<String> tags;
        private Map<String, Object> metadata;
        private LocalDateTime scheduledAt;
        
        // Constructors
        public UpdateJobRequest() {}
        
        // Getters and Setters
        public JobPriority getPriority() { return priority; }
        public void setPriority(JobPriority priority) { this.priority = priority; }
        
        public List<Long> getDependencies() { return dependencies; }
        public void setDependencies(List<Long> dependencies) { this.dependencies = dependencies; }
        
        public String getParameters() { return parameters; }
        public void setParameters(String parameters) { this.parameters = parameters; }
        
        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
        
        public Integer getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    }
    
    /**
     * Response DTO for job details with status and dependencies
     */
    public static class JobDetailsResponse {
        private Long id;
        private String jobId;
        private String name;
        private String description;
        private JobStatus status;
        private JobPriority priority;
        private String jobType;
        private String parameters;
        private Integer maxRetries;
        private Integer currentRetryCount;
        private Integer timeoutMinutes;
        private List<String> tags;
        private String assignedWorkerId;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime scheduledAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Long executionTimeMs;
        private List<JobDependencyInfo> dependencies;
        private List<JobDependencyInfo> dependents;
        private Map<String, Object> metadata;
        private JobExecutionInfo executionInfo;
        
        // Constructors
        public JobDetailsResponse() {}
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public JobStatus getStatus() { return status; }
        public void setStatus(JobStatus status) { this.status = status; }
        
        public JobPriority getPriority() { return priority; }
        public void setPriority(JobPriority priority) { this.priority = priority; }
        
        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }
        
        public String getParameters() { return parameters; }
        public void setParameters(String parameters) { this.parameters = parameters; }
        
        public Integer getMaxRetries() { return maxRetries; }
        public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
        
        public Integer getCurrentRetryCount() { return currentRetryCount; }
        public void setCurrentRetryCount(Integer currentRetryCount) { this.currentRetryCount = currentRetryCount; }
        
        public Integer getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        
        public String getAssignedWorkerId() { return assignedWorkerId; }
        public void setAssignedWorkerId(String assignedWorkerId) { this.assignedWorkerId = assignedWorkerId; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        
        public LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        
        public Long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        
        public List<JobDependencyInfo> getDependencies() { return dependencies; }
        public void setDependencies(List<JobDependencyInfo> dependencies) { this.dependencies = dependencies; }
        
        public List<JobDependencyInfo> getDependents() { return dependents; }
        public void setDependents(List<JobDependencyInfo> dependents) { this.dependents = dependents; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public JobExecutionInfo getExecutionInfo() { return executionInfo; }
        public void setExecutionInfo(JobExecutionInfo executionInfo) { this.executionInfo = executionInfo; }
    }
    
    /**
     * Information about job dependencies
     */
    public static class JobDependencyInfo {
        private Long jobId;
        private String jobName;
        private JobStatus status;
        private String dependencyType;
        
        // Constructors
        public JobDependencyInfo() {}
        
        public JobDependencyInfo(Long jobId, String jobName, JobStatus status, String dependencyType) {
            this.jobId = jobId;
            this.jobName = jobName;
            this.status = status;
            this.dependencyType = dependencyType;
        }
        
        // Getters and Setters
        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }
        
        public String getJobName() { return jobName; }
        public void setJobName(String jobName) { this.jobName = jobName; }
        
        public JobStatus getStatus() { return status; }
        public void setStatus(JobStatus status) { this.status = status; }
        
        public String getDependencyType() { return dependencyType; }
        public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
    }
    
    /**
     * Information about job execution
     */
    public static class JobExecutionInfo {
        private String workerId;
        private String workerName;
        private String workerHost;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private Integer cpuUsage;
        private Integer memoryUsage;
        private Map<String, Object> performanceMetrics;
        
        // Constructors
        public JobExecutionInfo() {}
        
        // Getters and Setters
        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        
        public String getWorkerName() { return workerName; }
        public void setWorkerName(String workerName) { this.workerName = workerName; }
        
        public String getWorkerHost() { return workerHost; }
        public void setWorkerHost(String workerHost) { this.workerHost = workerHost; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public Long getDurationMs() { return durationMs; }
        public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
        
        public Integer getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(Integer cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public Integer getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(Integer memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public Map<String, Object> getPerformanceMetrics() { return performanceMetrics; }
        public void setPerformanceMetrics(Map<String, Object> performanceMetrics) { 
            this.performanceMetrics = performanceMetrics; 
        }
    }
}
