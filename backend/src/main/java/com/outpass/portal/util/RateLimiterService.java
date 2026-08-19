package com.outpass.portal.util;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class RateLimiterService {
    
    private final Map<String, RateLimitInfo> requestCounts = new ConcurrentHashMap<>();
    
    // Different limits for different operation types
    // For 2000+ users during peak times, these are more realistic
    
    // CREATE operations (outpass creation) - More strict
    private static final int MAX_CREATE_REQUESTS = 10;
    private static final long CREATE_TIME_WINDOW_SECONDS = 3600; // 1 hour
    
    // UPDATE operations (profile update, etc.) - Moderate
    private static final int MAX_UPDATE_REQUESTS = 20;
    private static final long UPDATE_TIME_WINDOW_SECONDS = 3600; // 1 hour
    
    // READ operations (viewing data) - More lenient
    private static final int MAX_READ_REQUESTS = 200;
    private static final long READ_TIME_WINDOW_SECONDS = 60; // 1 minute
    
    public boolean isAllowed(String userId, RateLimitType type) {
        String key = "user:" + userId + ":" + type.name();
        
        int maxRequests = getMaxRequests(type);
        long timeWindowSeconds = getTimeWindow(type);
        
        Instant now = Instant.now();
        RateLimitInfo info = requestCounts.computeIfAbsent(key, k -> new RateLimitInfo());
        
        synchronized (info) {
            // Reset counter if time window has passed
            if (now.getEpochSecond() - info.windowStart.getEpochSecond() >= timeWindowSeconds) {
                info.requestCount = 0;
                info.windowStart = now;
            }
            
            // Check if limit exceeded
            if (info.requestCount >= maxRequests) {
                return false;
            }
            
            info.requestCount++;
            return true;
        }
    }
    
    public void reset(String userId, RateLimitType type) {
        String key = "user:" + userId + ":" + type.name();
        requestCounts.remove(key);
    }
    
    public int getRemainingRequests(String userId, RateLimitType type) {
        String key = "user:" + userId + ":" + type.name();
        RateLimitInfo info = requestCounts.get(key);
        
        int maxRequests = getMaxRequests(type);
        long timeWindowSeconds = getTimeWindow(type);
        
        if (info == null) {
            return maxRequests;
        }
        
        Instant now = Instant.now();
        synchronized (info) {
            // Reset if time window has passed
            if (now.getEpochSecond() - info.windowStart.getEpochSecond() >= timeWindowSeconds) {
                return maxRequests;
            }
            
            return Math.max(0, maxRequests - info.requestCount);
        }
    }
    
    private int getMaxRequests(RateLimitType type) {
        return switch (type) {
            case CREATE -> MAX_CREATE_REQUESTS;
            case UPDATE -> MAX_UPDATE_REQUESTS;
            case READ -> MAX_READ_REQUESTS;
        };
    }
    
    private long getTimeWindow(RateLimitType type) {
        return switch (type) {
            case CREATE -> CREATE_TIME_WINDOW_SECONDS;
            case UPDATE -> UPDATE_TIME_WINDOW_SECONDS;
            case READ -> READ_TIME_WINDOW_SECONDS;
        };
    }
    
    public enum RateLimitType {
        CREATE,  // Creating new resources (outpass creation)
        UPDATE,  // Updating resources (profile, status changes)
        READ     // Reading/viewing data
    }
    
    private static class RateLimitInfo {
        int requestCount = 0;
        Instant windowStart = Instant.now();
    }
}
