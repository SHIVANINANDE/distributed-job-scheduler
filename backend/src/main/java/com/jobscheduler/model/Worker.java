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
    
    // Constructors
    public Worker() {}
    
    public Worker(String workerId, String name) {
        this.workerId = workerId;
        this.name = name;
        this.status = WorkerStatus.INACTIVE;
        this.maxConcurrentJobs = 1;
        this.currentJobCount = 0;
        this.totalJobsProcessed = 0L;
        this.totalJobsSuccessful = 0L;
        this.totalJobsFailed = 0L;
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
    
    // Utility methods
    public boolean isActive() {
        return status == WorkerStatus.ACTIVE;
    }
    
    public boolean isAvailable() {
        return isActive() && currentJobCount < maxConcurrentJobs;
    }
    
    public boolean canAcceptJob() {
        return isAvailable();
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
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
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
