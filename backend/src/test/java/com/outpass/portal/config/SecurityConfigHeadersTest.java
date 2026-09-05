package com.outpass.portal.config;

import com.outpass.portal.controller.HealthController;
import com.outpass.portal.security.JwtAuthenticationFilter;
import com.outpass.portal.service.CustomUserDetailsService;
import com.outpass.portal.util.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the CSP-header fix: a full Spring Security filter chain (via SecurityConfig, not a
 * mocked/inspected config object) must actually emit Content-Security-Policy on a real
 * response, not just be declared in code that never runs.
 */
@WebMvcTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class SecurityConfigHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    // Real dependencies of SecurityConfig that would otherwise need a live DB/JWT secret --
    // mocked here since this test only exercises the filter chain's header/response behavior
    // on a permitAll endpoint, not authentication itself.
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // @WebMvcTest also picks up WebConfig (a WebMvcConfigurer), which needs
    // RateLimitInterceptor -> RateLimiterService; irrelevant to this header-focused test but
    // required to satisfy the bean graph.
    @MockitoBean
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void jwtFilterPassesRequestsThrough() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void healthEndpoint_emitsStrictContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }

    @Test
    void healthEndpoint_stillEmitsOtherDefaultSecurityHeaders() throws Exception {
        // Spring Security's other default header protections must survive this change --
        // the CSP addition must not have replaced or disabled them.
        mockMvc.perform(get("/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"));
    }
}
