package com.example.demo.controller;

import com.example.demo.metrics.RateLimitMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MetricsController {

    private final RateLimitMetrics metrics;

    public MetricsController(RateLimitMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public Map<String, Long> getMetrics() {

        return Map.of(
                "totalRequests", metrics.getTotalRequests(),
                "allowedRequests", metrics.getAllowedRequests(),
                "rejectedRequests", metrics.getRejectedRequests()
        );
    }
}