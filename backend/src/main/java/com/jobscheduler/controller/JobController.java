package com.jobscheduler.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    @GetMapping("/health")
    public String health() {
        return "Job Scheduler API is running!";
    }
}
