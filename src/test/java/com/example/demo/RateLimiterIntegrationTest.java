package com.example.demo;

import com.example.demo.limiter.RateLimitResult;
import com.example.demo.limiter.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RateLimiterIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void shouldAllowRequestWhenLimitIsNotReached() {

        String clientId = "test-user-1";
        String endpoint = "test";

        RateLimitResult result = rateLimiter.allowRequest(
                clientId,
                endpoint,
                5,
                60);

        assertTrue(result.isAllowed());
        assertEquals(5, result.getLimit());
    }

    @Test
    void shouldRejectRequestWhenLimitIsExceeded() {

        String clientId = "test-user-limit";
        String endpoint = "test-limit";

        RateLimitResult result = null;

        for (int i = 0; i < 6; i++) {
            result = rateLimiter.allowRequest(
                    clientId,
                    endpoint,
                    5,
                    60);
        }

        assertNotNull(result);
        assertFalse(result.isAllowed());
        assertEquals(5, result.getLimit());
        assertEquals(0, result.getRemaining());
    }

    @Test
    void differentApiKeysShouldHaveIndependentLimits() {

        String endpoint = "api-key-test";

        // Sameer uses 5 requests
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = rateLimiter.allowRequest(
                    "sameer-123",
                    endpoint,
                    5,
                    60);

            assertTrue(result.isAllowed());
        }

        // Sameer's 6th request should be rejected
        RateLimitResult sameerResult = rateLimiter.allowRequest(
                "sameer-123",
                endpoint,
                5,
                60);

        assertFalse(sameerResult.isAllowed());

        // Rahul should still get his own fresh limit
        RateLimitResult rahulResult = rateLimiter.allowRequest(
                "rahul-456",
                endpoint,
                5,
                60);

        assertTrue(rahulResult.isAllowed());
        assertEquals(5, rahulResult.getLimit());
        assertEquals(4, rahulResult.getRemaining());
    }

    @Test
    void shouldExpireRateLimitKey() throws InterruptedException {

        String clientId = "expiry-test";
        String endpoint = "expiry";

        RateLimitResult result = rateLimiter.allowRequest(
                clientId,
                endpoint,
                5,
                2);

        assertTrue(result.isAllowed());

        Thread.sleep(2500);

        RateLimitResult newResult = rateLimiter.allowRequest(
                clientId,
                endpoint,
                5,
                2);

        assertTrue(newResult.isAllowed());
        assertEquals(4, newResult.getRemaining());
    }
}