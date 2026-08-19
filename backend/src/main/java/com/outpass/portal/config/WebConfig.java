package com.outpass.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.outpass.portal.interceptor.RateLimitInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final RateLimitInterceptor rateLimitInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply rate limiting to all API endpoints
        // You can customize the paths as needed
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/student/**", "/warden/**", "/security/**")
                .excludePathPatterns("/auth/**"); // Exclude auth endpoints from rate limiting
    }
}
