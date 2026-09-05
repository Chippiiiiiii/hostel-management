package com.outpass.portal.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the 400-vs-403-vs-401 semantics fix: Spring Security's AnonymousAuthenticationFilter
 * means both "no token at all" and "valid token, wrong role" normally raise the same
 * AccessDeniedException and both returned 403 -- these tests exercise the real
 * AccessDeniedHandler/AuthenticationEntryPoint beans directly to confirm they now split that
 * into 401 ("never authenticated") vs 403 ("authenticated but not permitted").
 */
class SecurityConfigAccessDeniedTest {

    private final SecurityConfig securityConfig =
            new SecurityConfig(null, null);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessDeniedHandler_anonymousPrincipal_returns401() throws Exception {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", authorities));

        AccessDeniedHandler handler = securityConfig.accessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new org.springframework.security.access.AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("Authentication required");
    }

    @Test
    void accessDeniedHandler_noAuthenticationAtAll_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        AccessDeniedHandler handler = securityConfig.accessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new org.springframework.security.access.AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void accessDeniedHandler_authenticatedWrongRole_returns403() throws Exception {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@test.local", null, authorities));

        AccessDeniedHandler handler = securityConfig.accessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/warden/outpasses/pending");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new org.springframework.security.access.AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("Access denied");
    }

    @Test
    void authenticationEntryPoint_alwaysReturns401() throws Exception {
        AuthenticationEntryPoint entryPoint = securityConfig.authenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response,
                new org.springframework.security.core.AuthenticationException("no auth") {});

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("Authentication required");
    }
}
