
    //"Given an IP address, decide whether this request is allowed."

package com.example.demo.limiter;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SIZE = 60_000;

    private final Map<String, RequestInfo> requests =
            new ConcurrentHashMap<>();

    public boolean allowRequest(String clientIp) {

        long currentTime = System.currentTimeMillis();

        RequestInfo info = requests.get(clientIp);

        // First request from this IP
        if (info == null) {

            requests.put(
                    clientIp,
                    new RequestInfo(currentTime, 1)
            );

            return true;
        }

        // 60 seconds have passed
        if (currentTime - info.startTime >= WINDOW_SIZE) {

            info.startTime = currentTime;
            info.count = 1;

            return true;
        }

        // Limit exceeded
        if (info.count >= MAX_REQUESTS) {
            return false;
        }

        info.count++;

        return true;
    }

    private static class RequestInfo {

        long startTime;
        int count;

        RequestInfo(long startTime, int count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
