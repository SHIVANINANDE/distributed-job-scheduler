package com.jobscheduler.scheduler;

import com.jobscheduler.service.FailureHandlingService;
import com.jobscheduler.service.DeadLetterQueueService;
import com.jobscheduler.service.JobExecutionHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for failure handling and recovery operations
 */
@Component
public class FailureHandlingScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(FailureHandlingScheduler.class);
    
    @Autowired
    private FailureHandlingService failureHandlingService;
    
    @Autowired
    private DeadLetterQueueService deadLetterQueueService;
    
    @Autowired
    private JobExecutionHistoryService executionHistoryService;
    
    /**
     * Detect and handle worker failures every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void detectWorkerFailures() {
        try {
            logger.debug("Running scheduled worker failure detection");
            failureHandlingService.detectAndHandleWorkerFailures();
        } catch (Exception e) {
            logger.error("Error in scheduled worker failure detection", e);
        }
    }
    
    /**
     * Clean up dead letter queue every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupDeadLetterQueue() {
        try {
            logger.debug("Running scheduled dead letter queue cleanup");
            deadLetterQueueService.cleanupExpiredEntries();
        } catch (Exception e) {
            logger.error("Error in scheduled dead letter queue cleanup", e);
        }
    }
    
    /**
     * Clean up old execution history every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
    public void cleanupExecutionHistory() {
        try {
            logger.debug("Running scheduled execution history cleanup");
            executionHistoryService.cleanupOldHistory();
        } catch (Exception e) {
            logger.error("Error in scheduled execution history cleanup", e);
        }
    }
    
    /**
     * Monitor and recover stuck jobs every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void monitorStuckJobs() {
        try {
            logger.debug("Running scheduled stuck job monitoring");
            failureHandlingService.handleStuckJobs();
        } catch (Exception e) {
            logger.error("Error in scheduled stuck job monitoring", e);
        }
    }
}
