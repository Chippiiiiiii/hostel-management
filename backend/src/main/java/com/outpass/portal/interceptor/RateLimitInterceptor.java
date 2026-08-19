package com.outpass.portal.interceptor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.util.RateLimiterService;
import com.outpass.portal.util.RateLimiterService.RateLimitType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimiterService rateLimiterService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Only apply rate limiting to authenticated users
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String userId = userPrincipal.getId().toString();
            
            RateLimitType limitType = determineRateLimitType(request);
            
            if (!rateLimiterService.isAllowed(userId, limitType)) {
                String message = getLimitMessage(limitType);
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"" + message + "\",\"error\":\"TOO_MANY_REQUESTS\"}"
                );
                return false;
            }
            
            // Add rate limit headers
            int remaining = rateLimiterService.getRemainingRequests(userId, limitType);
            int maxLimit = getMaxLimit(limitType);
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxLimit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Type", limitType.name());
        }
        
        return true;
    }
    
    private RateLimitType determineRateLimitType(HttpServletRequest request) {
        String method = request.getMethod();
        
        // POST methods are typically CREATE
        if ("POST".equals(method)) {
            return RateLimitType.CREATE;
        }
        
        // PUT/PATCH are UPDATE
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return RateLimitType.UPDATE;
        }
        
        // GET, DELETE, and other methods are READ
        return RateLimitType.READ;
    }
    
    private String getLimitMessage(RateLimitType type) {
        return switch (type) {
            case CREATE -> "Rate limit exceeded. You can create 10 outpasses per hour. Please try again later.";
            case UPDATE -> "Rate limit exceeded. You can make 20 updates per hour. Please try again later.";
            case READ -> "Rate limit exceeded. You can make 200 read requests per minute. Please try again later.";
        };
    }
    
    private int getMaxLimit(RateLimitType type) {
        return switch (type) {
            case CREATE -> 10;
            case UPDATE -> 20;
            case READ -> 200;
        };
    }
}

