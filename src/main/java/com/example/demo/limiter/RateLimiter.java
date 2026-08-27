package com.example.demo.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])

            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end

            return count
            """;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult allowRequest(
            String clientId,
            String endpoint,
            int maxRequests,
            long windowSeconds
    ) {

        String key = "rate_limit:" + endpoint + ":" + clientId;

        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>(
                        RATE_LIMIT_SCRIPT,
                        Long.class
                );

        Long count = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(windowSeconds)
        );

        long remaining = Math.max(
                0,
                maxRequests - count
        );

        Long ttl = redisTemplate.getExpire(key);

        long retryAfter = Math.max(
                0,
                ttl
        );

        boolean allowed = count <= maxRequests;

        return new RateLimitResult(
                allowed,
                maxRequests,
                remaining,
                allowed ? 0 : retryAfter
        );
    }
}