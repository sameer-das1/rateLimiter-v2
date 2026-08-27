package com.example.demo.filter;

import org.springframework.beans.factory.annotation.Value;

import com.example.demo.limiter.RateLimitResult;
import com.example.demo.limiter.RateLimiter;

import com.example.demo.metrics.RateLimitMetrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitMetrics metrics;

    @Value("${rate-limit.login.max-requests}")
    private int loginMaxRequests;

    @Value("${rate-limit.login.window-seconds}")
    private long loginWindowSeconds;

    @Value("${rate-limit.products.max-requests}")
    private int productsMaxRequests;

    @Value("${rate-limit.products.window-seconds}")
    private long productsWindowSeconds;

    @Value("${rate-limit.users.max-requests}")
    private int usersMaxRequests;

    @Value("${rate-limit.users.window-seconds}")
    private long usersWindowSeconds;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            RateLimitMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();

        String apiKey = request.getHeader("X-API-Key");

        String clientId;

        if (apiKey != null && !apiKey.isBlank()) {
            clientId = apiKey;
        } else {
            clientId = clientIp;
        }

        String path = request.getRequestURI();

        int maxRequests;
        long windowSeconds;
        String endpoint;

        if (path.equals("/api/login")) {

            endpoint = "login";
            maxRequests = loginMaxRequests;
            windowSeconds = loginWindowSeconds;

        } else if (path.equals("/api/products")) {

            endpoint = "products";
            maxRequests = productsMaxRequests;
            windowSeconds = productsWindowSeconds;

        } else if (path.equals("/api/users")) {

            endpoint = "users";
            maxRequests = usersMaxRequests;
            windowSeconds = usersWindowSeconds;

        } else {

            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = rateLimiter.allowRequest(
                clientId,
                endpoint,
                maxRequests,
                windowSeconds);
        
        metrics.recordRequest();

        response.setHeader(
                "X-RateLimit-Limit",
                String.valueOf(result.getLimit()));

        response.setHeader(
                "X-RateLimit-Remaining",
                String.valueOf(result.getRemaining()));

        if (!result.isAllowed()) {

            metrics.recordRejectedRequest();

            response.setStatus(429);

            response.setHeader(
                    "Retry-After",
                    String.valueOf(result.getRetryAfter()));

            response.setContentType("application/json");

            response.getWriter().write(
                    "{\"error\":\"Too many requests. Try again later.\"}");

            return;
        }

        metrics.recordAllowedRequest();

        filterChain.doFilter(request, response);
    }
}