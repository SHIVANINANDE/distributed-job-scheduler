package com.jobscheduler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_dependencies", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "dependency_job_id"}))
public class JobDependency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    
    @NotNull
    @Column(name = "dependency_job_id", nullable = false)
    private Long dependencyJobId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false)
    private DependencyType dependencyType = DependencyType.MUST_COMPLETE;
    
    @Column(name = "is_satisfied")
    private Boolean isSatisfied = false;
    
    @Column(name = "satisfied_at")
    private LocalDateTime satisfiedAt;
    
    // Constructors
    public JobDependency() {}
    
    public JobDependency(Long jobId, Long dependencyJobId) {
        this.jobId = jobId;
        this.dependencyJobId = dependencyJobId;
        this.dependencyType = DependencyType.MUST_COMPLETE;
        this.isSatisfied = false;
    }
    
    public JobDependency(Long jobId, Long dependencyJobId, DependencyType dependencyType) {
        this.jobId = jobId;
        this.dependencyJobId = dependencyJobId;
        this.dependencyType = dependencyType;
        this.isSatisfied = false;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getJobId() {
        return jobId;
    }
    
    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
    
    public Long getDependencyJobId() {
        return dependencyJobId;
    }
    
    public void setDependencyJobId(Long dependencyJobId) {
        this.dependencyJobId = dependencyJobId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public DependencyType getDependencyType() {
        return dependencyType;
    }
    
    public void setDependencyType(DependencyType dependencyType) {
        this.dependencyType = dependencyType;
    }
    
    public Boolean getIsSatisfied() {
        return isSatisfied;
    }
    
    public void setIsSatisfied(Boolean isSatisfied) {
        this.isSatisfied = isSatisfied;
        if (Boolean.TRUE.equals(isSatisfied) && this.satisfiedAt == null) {
            this.satisfiedAt = LocalDateTime.now();
        }
    }
    
    public LocalDateTime getSatisfiedAt() {
        return satisfiedAt;
    }
    
    public void setSatisfiedAt(LocalDateTime satisfiedAt) {
        this.satisfiedAt = satisfiedAt;
    }
    
    // Utility methods
    public void markAsSatisfied() {
        this.isSatisfied = true;
        this.satisfiedAt = LocalDateTime.now();
    }
    
    public boolean isNotSatisfied() {
        return !Boolean.TRUE.equals(isSatisfied);
    }
    
    @Override
    public String toString() {
        return "JobDependency{" +
                "id=" + id +
                ", jobId=" + jobId +
                ", dependencyJobId=" + dependencyJobId +
                ", dependencyType=" + dependencyType +
                ", isSatisfied=" + isSatisfied +
                ", createdAt=" + createdAt +
                '}';
    }
    
    // Dependency Types Enum
    public enum DependencyType {
        MUST_COMPLETE("Must Complete"),
        MUST_START("Must Start"),
        MUST_SUCCEED("Must Succeed"),
        CONDITIONAL("Conditional");
        
        private final String description;
        
        DependencyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
