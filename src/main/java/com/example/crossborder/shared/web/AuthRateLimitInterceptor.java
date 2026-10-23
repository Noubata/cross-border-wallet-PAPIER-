package com.example.crossborder.shared.web;

import com.example.crossborder.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final int capacity;
    private final long refillPeriodMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitInterceptor(
            @Value("${auth.rate-limit.capacity:10}") int capacity,
            @Value("${auth.rate-limit.refill-period:PT1M}") Duration refillPeriod) {
        if (capacity < 1 || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("Auth rate limit must have a positive capacity and period");
        }
        this.capacity = capacity;
        this.refillPeriodMillis = refillPeriod.toMillis();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= refillPeriodMillis) {
                window.startedAt = now;
                window.requests = 0;
            }
            if (++window.requests > capacity) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "auth.rate_limited");
            }
        }
        return true;
    }

    private static final class Window {
        private long startedAt;
        private int requests;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}