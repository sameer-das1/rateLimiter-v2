package com.example.demo.limiter;

public class RateLimitResult {

    private final boolean allowed;
    private final int limit;
    private final long remaining;
    private final long retryAfter;

    public RateLimitResult(
            boolean allowed,
            int limit,
            long remaining,
            long retryAfter
    ) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = remaining;
        this.retryAfter = retryAfter;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}