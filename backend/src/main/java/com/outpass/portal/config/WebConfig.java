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
        //
        // /warden/rooms/** (building/floor/room CRUD, bulk-allocate) is deliberately NOT
        // excluded: it requires WARDEN/ADMIN auth, but a compromised or malicious staff
        // account previously had zero throttle on destructive operations here (e.g. repeated
        // removeBuilding/removeFloor calls). The existing CREATE(10/hr)/UPDATE(20/hr) buckets
        // are generous enough for legitimate use -- bulk-allocate is a single POST per run
        // regardless of how many students it assigns, and building/floor/room setup is an
        // infrequent administrative action, not a per-request bottleneck.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/student/**", "/warden/**", "/security/**", "/admin/**")
                .excludePathPatterns("/auth/**");
    }
}
