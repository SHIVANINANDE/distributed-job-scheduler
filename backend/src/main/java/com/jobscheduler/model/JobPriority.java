package com.jobscheduler.model;

public enum JobPriority {
    LOW(1),
    MEDIUM(50),
    HIGH(100);
    
    private final int value;
    
    JobPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static JobPriority fromValue(int value) {
        for (JobPriority priority : JobPriority.values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        // Default to closest priority
        if (value <= LOW.value) return LOW;
        if (value <= MEDIUM.value) return MEDIUM;
        return HIGH;
    }
    
    public static JobPriority fromString(String priority) {
        try {
            return JobPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM; // Default priority
        }
    }
}
