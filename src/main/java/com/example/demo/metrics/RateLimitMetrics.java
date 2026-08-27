package com.example.demo.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitMetrics {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    public void recordAllowedRequest() {
        allowedRequests.incrementAndGet();
    }

    public void recordRejectedRequest() {
        rejectedRequests.incrementAndGet();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    public long getRejectedRequests() {
        return rejectedRequests.get();
    }
}