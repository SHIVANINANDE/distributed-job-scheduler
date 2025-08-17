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
    private Long jobId; // Child job ID (the job that depends on another)
    
    @NotNull
    @Column(name = "dependency_job_id", nullable = false)  
    private Long dependencyJobId; // Parent job ID (the job that must be satisfied)
    
    // Alternative naming for clarity
    @NotNull
    @Column(name = "parent_job_id", nullable = false, insertable = false, updatable = false)
    private Long parentJobId; // Same as dependencyJobId, for clarity
    
    @NotNull  
    @Column(name = "child_job_id", nullable = false, insertable = false, updatable = false)
    private Long childJobId; // Same as jobId, for clarity
    
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
    
    // Constraint and validation fields
    @Column(name = "constraint_expression", columnDefinition = "TEXT")
    private String constraintExpression; // Custom constraint logic (JSON or expression)
    
    @Column(name = "validation_rule", columnDefinition = "TEXT") 
    private String validationRule; // Validation rule for conditional dependencies
    
    @Column(name = "dependency_priority")
    private Integer dependencyPriority = 1; // Priority of this dependency (1-10)
    
    @Column(name = "is_blocking")
    private Boolean isBlocking = true; // Whether this dependency blocks execution
    
    @Column(name = "is_optional")
    private Boolean isOptional = false; // Whether this dependency is optional
    
    @Column(name = "timeout_minutes")
    private Integer timeoutMinutes; // Timeout for dependency satisfaction
    
    @Column(name = "retry_count")
    private Integer retryCount = 0; // Number of times dependency check was retried
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3; // Maximum retry attempts
    
    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt; // Last time dependency was checked
    
    @Column(name = "check_interval_seconds")
    private Integer checkIntervalSeconds = 30; // How often to check dependency
    
    @Column(name = "failure_action")
    @Enumerated(EnumType.STRING)
    private FailureAction failureAction = FailureAction.BLOCK; // What to do if dependency fails
    
    @Column(name = "dependency_group")
    private String dependencyGroup; // Group dependencies (AND/OR logic)
    
    @Column(name = "condition_met")
    private Boolean conditionMet = false; // For conditional dependencies
    
    // Constructors
    public JobDependency() {}
    
    public JobDependency(Long jobId, Long dependencyJobId) {
        this.jobId = jobId;
        this.dependencyJobId = dependencyJobId;
        this.childJobId = jobId;
        this.parentJobId = dependencyJobId;
        this.dependencyType = DependencyType.MUST_COMPLETE;
        this.isSatisfied = false;
        this.isBlocking = true;
        this.isOptional = false;
        this.dependencyPriority = 1;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.checkIntervalSeconds = 30;
        this.failureAction = FailureAction.BLOCK;
        this.conditionMet = false;
    }
    
    public JobDependency(Long jobId, Long dependencyJobId, DependencyType dependencyType) {
        this.jobId = jobId;
        this.dependencyJobId = dependencyJobId;
        this.childJobId = jobId;
        this.parentJobId = dependencyJobId;
        this.dependencyType = dependencyType;
        this.isSatisfied = false;
        this.isBlocking = true;
        this.isOptional = false;
        this.dependencyPriority = 1;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.checkIntervalSeconds = 30;
        this.failureAction = FailureAction.BLOCK;
        this.conditionMet = false;
    }
    
    public JobDependency(Long jobId, Long dependencyJobId, DependencyType dependencyType, boolean isOptional) {
        this(jobId, dependencyJobId, dependencyType);
        this.isOptional = isOptional;
        this.isBlocking = !isOptional;
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
        this.parentJobId = dependencyJobId;
    }
    
    public Long getParentJobId() {
        return parentJobId != null ? parentJobId : dependencyJobId;
    }
    
    public void setParentJobId(Long parentJobId) {
        this.parentJobId = parentJobId;
        this.dependencyJobId = parentJobId;
    }
    
    public Long getChildJobId() {
        return childJobId != null ? childJobId : jobId;
    }
    
    public void setChildJobId(Long childJobId) {
        this.childJobId = childJobId;
        this.jobId = childJobId;
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
    
    public String getConstraintExpression() {
        return constraintExpression;
    }
    
    public void setConstraintExpression(String constraintExpression) {
        this.constraintExpression = constraintExpression;
    }
    
    public String getValidationRule() {
        return validationRule;
    }
    
    public void setValidationRule(String validationRule) {
        this.validationRule = validationRule;
    }
    
    public Integer getDependencyPriority() {
        return dependencyPriority;
    }
    
    public void setDependencyPriority(Integer dependencyPriority) {
        this.dependencyPriority = Math.max(1, Math.min(10, dependencyPriority));
    }
    
    public Boolean getIsBlocking() {
        return isBlocking;
    }
    
    public void setIsBlocking(Boolean isBlocking) {
        this.isBlocking = isBlocking;
    }
    
    public Boolean getIsOptional() {
        return isOptional;
    }
    
    public void setIsOptional(Boolean isOptional) {
        this.isOptional = isOptional;
        // Optional dependencies should not be blocking
        if (Boolean.TRUE.equals(isOptional)) {
            this.isBlocking = false;
        }
    }
    
    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }
    
    public void setTimeoutMinutes(Integer timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
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
    
    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }
    
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
    
    public Integer getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }
    
    public void setCheckIntervalSeconds(Integer checkIntervalSeconds) {
        this.checkIntervalSeconds = Math.max(5, checkIntervalSeconds);
    }
    
    public FailureAction getFailureAction() {
        return failureAction;
    }
    
    public void setFailureAction(FailureAction failureAction) {
        this.failureAction = failureAction;
    }
    
    public String getDependencyGroup() {
        return dependencyGroup;
    }
    
    public void setDependencyGroup(String dependencyGroup) {
        this.dependencyGroup = dependencyGroup;
    }
    
    public Boolean getConditionMet() {
        return conditionMet;
    }
    
    public void setConditionMet(Boolean conditionMet) {
        this.conditionMet = conditionMet;
    }
    
    // Utility methods
    public void markAsSatisfied() {
        this.isSatisfied = true;
        this.satisfiedAt = LocalDateTime.now();
        this.conditionMet = true;
    }
    
    public boolean isNotSatisfied() {
        return !Boolean.TRUE.equals(isSatisfied);
    }
    
    public boolean isBlocking() {
        return Boolean.TRUE.equals(isBlocking) && !Boolean.TRUE.equals(isOptional);
    }
    
    public boolean isOptional() {
        return Boolean.TRUE.equals(isOptional);
    }
    
    public boolean hasTimedOut() {
        if (timeoutMinutes == null || createdAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(createdAt.plusMinutes(timeoutMinutes));
    }
    
    public boolean shouldRetry() {
        return retryCount < maxRetries && !hasTimedOut();
    }
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
        this.lastCheckedAt = LocalDateTime.now();
    }
    
    public boolean needsRecheck() {
        if (lastCheckedAt == null) {
            return true;
        }
        if (isSatisfied) {
            return false;
        }
        int interval = checkIntervalSeconds != null ? checkIntervalSeconds : 30;
        return LocalDateTime.now().isAfter(lastCheckedAt.plusSeconds(interval));
    }
    
    public boolean isHighPriority() {
        return dependencyPriority != null && dependencyPriority >= 8;
    }
    
    public boolean isLowPriority() {
        return dependencyPriority != null && dependencyPriority <= 3;
    }
    
    public boolean canProceedOnFailure() {
        return failureAction == FailureAction.PROCEED || failureAction == FailureAction.WARN;
    }
    
    public String getRelationshipDescription() {
        return "Job " + childJobId + " depends on Job " + parentJobId + 
               " (" + dependencyType.getDescription() + ")";
    }
    
    @Override
    public String toString() {
        return "JobDependency{" +
                "id=" + id +
                ", childJobId=" + childJobId +
                ", parentJobId=" + parentJobId +
                ", dependencyType=" + dependencyType +
                ", isSatisfied=" + isSatisfied +
                ", isBlocking=" + isBlocking +
                ", isOptional=" + isOptional +
                ", priority=" + dependencyPriority +
                ", failureAction=" + failureAction +
                ", createdAt=" + createdAt +
                '}';
    }
    
    // Dependency Types Enum
    public enum DependencyType {
        MUST_COMPLETE("Must Complete Successfully"),
        MUST_START("Must Start Execution"),
        MUST_SUCCEED("Must Succeed Without Errors"),
        CONDITIONAL("Conditional Based on Rules"),
        SOFT_DEPENDENCY("Soft Dependency (Warning Only)"),
        TIME_BASED("Time-Based Dependency"),
        RESOURCE_BASED("Resource Availability Based");
        
        private final String description;
        
        DependencyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Failure Action Enum
    public enum FailureAction {
        BLOCK("Block job execution"),
        PROCEED("Proceed with warning"),
        WARN("Log warning and proceed"),
        RETRY("Retry dependency check"),
        SKIP("Skip this dependency"),
        ESCALATE("Escalate to admin");
        
        private final String description;
        
        FailureAction(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
