package com.jobscheduler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workers")
public class Worker {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Worker ID is required")
    @Column(name = "worker_id", unique = true, nullable = false)
    private String workerId;
    
    @NotBlank(message = "Worker name is required")
    @Column(nullable = false)
    private String name;
    
    @NotNull(message = "Worker status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerStatus status = WorkerStatus.INACTIVE;
    
    @Column(name = "host_name")
    private String hostName;
    
    @Column(name = "host_address")
    private String hostAddress;
    
    @Column(name = "port")
    private Integer port;
    
    @Column(name = "max_concurrent_jobs")
    private Integer maxConcurrentJobs = 1;
    
    @Column(name = "current_job_count")
    private Integer currentJobCount = 0;
    
    @Column(name = "total_jobs_processed")
    private Long totalJobsProcessed = 0L;
    
    @Column(name = "total_jobs_successful")
    private Long totalJobsSuccessful = 0L;
    
    @Column(name = "total_jobs_failed")
    private Long totalJobsFailed = 0L;
    
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;
    
    @Column(name = "last_job_completed")
    private LocalDateTime lastJobCompleted;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "version")
    private String version;
    
    @Column(name = "capabilities", columnDefinition = "TEXT")
    private String capabilities; // JSON string of worker capabilities
    
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // JSON array of tags for job matching
    
    // Current Job Assignment Information
    @Column(name = "current_job_ids", columnDefinition = "TEXT")
    private String currentJobIds; // JSON array of currently assigned job IDs
    
    @Column(name = "available_capacity")
    private Integer availableCapacity; // Calculated available capacity
    
    @Column(name = "reserved_capacity")
    private Integer reservedCapacity = 0; // Capacity reserved for high-priority jobs
    
    @Column(name = "processing_capacity")
    private Integer processingCapacity; // Current processing capacity in use
    
    @Column(name = "queue_capacity")
    private Integer queueCapacity = 0; // Number of jobs in worker's queue
    
    @Column(name = "priority_threshold")
    private Integer priorityThreshold = 100; // Minimum priority for this worker
    
    @Column(name = "worker_load_factor")
    private Double workerLoadFactor = 1.0; // Load adjustment factor (0.1 to 2.0)
    
    // Constructors
    public Worker() {
        updateAvailableCapacity();
    }
    
    public Worker(String workerId, String name) {
        this.workerId = workerId;
        this.name = name;
        this.status = WorkerStatus.INACTIVE;
        this.maxConcurrentJobs = 1;
        this.currentJobCount = 0;
        this.totalJobsProcessed = 0L;
        this.totalJobsSuccessful = 0L;
        this.totalJobsFailed = 0L;
        this.reservedCapacity = 0;
        this.queueCapacity = 0;
        this.priorityThreshold = 100;
        this.workerLoadFactor = 1.0;
        updateAvailableCapacity();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getWorkerId() {
        return workerId;
    }
    
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public WorkerStatus getStatus() {
        return status;
    }
    
    public void setStatus(WorkerStatus status) {
        this.status = status;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
    
    public String getHostAddress() {
        return hostAddress;
    }
    
    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public Integer getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }
    
    public void setMaxConcurrentJobs(Integer maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
    }
    
    public Integer getCurrentJobCount() {
        return currentJobCount;
    }
    
    public void setCurrentJobCount(Integer currentJobCount) {
        this.currentJobCount = currentJobCount;
    }
    
    public Long getTotalJobsProcessed() {
        return totalJobsProcessed;
    }
    
    public void setTotalJobsProcessed(Long totalJobsProcessed) {
        this.totalJobsProcessed = totalJobsProcessed;
    }
    
    public Long getTotalJobsSuccessful() {
        return totalJobsSuccessful;
    }
    
    public void setTotalJobsSuccessful(Long totalJobsSuccessful) {
        this.totalJobsSuccessful = totalJobsSuccessful;
    }
    
    public Long getTotalJobsFailed() {
        return totalJobsFailed;
    }
    
    public void setTotalJobsFailed(Long totalJobsFailed) {
        this.totalJobsFailed = totalJobsFailed;
    }
    
    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
    
    public LocalDateTime getLastJobCompleted() {
        return lastJobCompleted;
    }
    
    public void setLastJobCompleted(LocalDateTime lastJobCompleted) {
        this.lastJobCompleted = lastJobCompleted;
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
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getCurrentJobIds() {
        return currentJobIds;
    }
    
    public void setCurrentJobIds(String currentJobIds) {
        this.currentJobIds = currentJobIds;
    }
    
    public Integer getAvailableCapacity() {
        return availableCapacity;
    }
    
    public void setAvailableCapacity(Integer availableCapacity) {
        this.availableCapacity = availableCapacity;
    }
    
    public Integer getReservedCapacity() {
        return reservedCapacity;
    }
    
    public void setReservedCapacity(Integer reservedCapacity) {
        this.reservedCapacity = reservedCapacity;
        updateAvailableCapacity();
    }
    
    public Integer getProcessingCapacity() {
        return processingCapacity;
    }
    
    public void setProcessingCapacity(Integer processingCapacity) {
        this.processingCapacity = processingCapacity;
    }
    
    public Integer getQueueCapacity() {
        return queueCapacity;
    }
    
    public void setQueueCapacity(Integer queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    
    public Integer getPriorityThreshold() {
        return priorityThreshold;
    }
    
    public void setPriorityThreshold(Integer priorityThreshold) {
        this.priorityThreshold = priorityThreshold;
    }
    
    public Double getWorkerLoadFactor() {
        return workerLoadFactor;
    }
    
    public void setWorkerLoadFactor(Double workerLoadFactor) {
        this.workerLoadFactor = Math.max(0.1, Math.min(2.0, workerLoadFactor));
    }
    
    // Utility methods
    public boolean isActive() {
        return status == WorkerStatus.ACTIVE;
    }
    
    public boolean isAvailable() {
        return isActive() && currentJobCount < maxConcurrentJobs && hasAvailableCapacity();
    }
    
    public boolean canAcceptJob() {
        return isAvailable();
    }
    
    public boolean canAcceptJob(int priority) {
        return isAvailable() && priority >= priorityThreshold;
    }
    
    public boolean hasAvailableCapacity() {
        return availableCapacity != null && availableCapacity > 0;
    }
    
    public boolean canAcceptHighPriorityJob(int priority) {
        return isActive() && priority >= 500 && (currentJobCount + reservedCapacity) < maxConcurrentJobs;
    }
    
    public void updateAvailableCapacity() {
        if (maxConcurrentJobs != null) {
            int reserved = (reservedCapacity != null) ? reservedCapacity : 0;
            int current = (currentJobCount != null) ? currentJobCount : 0;
            this.availableCapacity = Math.max(0, maxConcurrentJobs - current - reserved);
            this.processingCapacity = current;
        } else {
            this.availableCapacity = 0;
            this.processingCapacity = 0;
        }
    }
    
    public void assignJob(Long jobId) {
        incrementJobCount();
        addJobToCurrentAssignments(jobId);
        updateAvailableCapacity();
    }
    
    public void unassignJob(Long jobId) {
        decrementJobCount();
        removeJobFromCurrentAssignments(jobId);
        updateAvailableCapacity();
    }
    
    private void addJobToCurrentAssignments(Long jobId) {
        if (currentJobIds == null || currentJobIds.isEmpty()) {
            this.currentJobIds = "[" + jobId + "]";
        } else {
            // Simple JSON array manipulation - in production, use proper JSON library
            String newJobIds = currentJobIds.substring(0, currentJobIds.length() - 1);
            if (!newJobIds.equals("[")) {
                newJobIds += ",";
            }
            newJobIds += jobId + "]";
            this.currentJobIds = newJobIds;
        }
    }
    
    private void removeJobFromCurrentAssignments(Long jobId) {
        if (currentJobIds != null && !currentJobIds.isEmpty()) {
            // Simple JSON array manipulation - in production, use proper JSON library
            this.currentJobIds = currentJobIds.replace("," + jobId, "").replace(jobId + ",", "").replace("[" + jobId + "]", "[]");
            if (this.currentJobIds.equals("[,]") || this.currentJobIds.equals("[]")) {
                this.currentJobIds = "[]";
            }
        }
    }
    
    public void incrementJobCount() {
        this.currentJobCount = (this.currentJobCount != null ? this.currentJobCount : 0) + 1;
    }
    
    public void decrementJobCount() {
        this.currentJobCount = Math.max(0, (this.currentJobCount != null ? this.currentJobCount : 0) - 1);
    }
    
    public void recordJobCompletion(boolean successful) {
        this.totalJobsProcessed = (this.totalJobsProcessed != null ? this.totalJobsProcessed : 0L) + 1;
        if (successful) {
            this.totalJobsSuccessful = (this.totalJobsSuccessful != null ? this.totalJobsSuccessful : 0L) + 1;
        } else {
            this.totalJobsFailed = (this.totalJobsFailed != null ? this.totalJobsFailed : 0L) + 1;
        }
        this.lastJobCompleted = LocalDateTime.now();
        decrementJobCount();
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
        if (this.status == WorkerStatus.INACTIVE) {
            this.status = WorkerStatus.ACTIVE;
        }
    }
    
    public double getSuccessRate() {
        if (totalJobsProcessed == null || totalJobsProcessed == 0) {
            return 0.0;
        }
        return (double) (totalJobsSuccessful != null ? totalJobsSuccessful : 0L) / totalJobsProcessed * 100.0;
    }
    
    public double getLoadPercentage() {
        if (maxConcurrentJobs == null || maxConcurrentJobs == 0) {
            return 100.0;
        }
        return (double) (currentJobCount != null ? currentJobCount : 0) / maxConcurrentJobs * 100.0;
    }
    
    public double getEffectiveLoadFactor() {
        return getLoadPercentage() * (workerLoadFactor != null ? workerLoadFactor : 1.0);
    }
    
    public int getEffectiveCapacity() {
        if (maxConcurrentJobs == null) return 0;
        return (int) Math.floor(maxConcurrentJobs * (workerLoadFactor != null ? workerLoadFactor : 1.0));
    }
    
    public boolean isOverloaded() {
        return getLoadPercentage() > 90.0;
    }
    
    public boolean isUnderUtilized() {
        return getLoadPercentage() < 20.0 && isActive();
    }
    
    @Override
    public String toString() {
        return "Worker{" +
                "id=" + id +
                ", workerId='" + workerId + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", hostAddress='" + hostAddress + '\'' +
                ", port=" + port +
                ", currentJobCount=" + currentJobCount +
                ", maxConcurrentJobs=" + maxConcurrentJobs +
                ", availableCapacity=" + availableCapacity +
                ", reservedCapacity=" + reservedCapacity +
                ", loadFactor=" + workerLoadFactor +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }
    
    public void addCurrentJob(Long jobId) {
        assignJob(jobId);
    }
    
    public void removeCurrentJob(Long jobId) {
        unassignJob(jobId);
    }
    
    public void incrementSuccessfulJobs() {
        this.totalJobsSuccessful = (this.totalJobsSuccessful != null ? this.totalJobsSuccessful : 0L) + 1;
    }
    
    public void incrementFailedJobs() {
        this.totalJobsFailed = (this.totalJobsFailed != null ? this.totalJobsFailed : 0L) + 1;
    }
    
    public void setAssignedWorker(String workerId) {
        // This method exists for compatibility - workers don't assign themselves
        // In actual implementation, this might be used for worker-to-worker delegation
    }
    
    // Additional helper methods for job management
    public void clearCurrentJobs() {
        this.currentJobIds = "[]";
        this.currentJobCount = 0;
        updateAvailableCapacity();
    }
    
    // Additional methods for job assignment and load balancing
    public boolean canAcceptMoreJobs() {
        return isAvailable() && hasAvailableCapacity();
    }
    
    public double getAverageExecutionTime() {
        // This would be calculated from job execution history
        // For now, returning a default value - in real implementation, 
        // this would be tracked per worker
        return 5000.0; // 5 seconds default
    }
    
    // Worker Status Enum
    public enum WorkerStatus {
        ACTIVE("Active"),
        INACTIVE("Inactive"), 
        BUSY("Busy"),
        ERROR("Error"),
        MAINTENANCE("Maintenance");
        
        private final String description;
        
        WorkerStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
