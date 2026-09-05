package com.outpass.portal.config;

import com.outpass.portal.security.JwtAuthenticationFilter;
import com.outpass.portal.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Comma-separated list, overridable via the CORS_ALLOWED_ORIGINS env var.
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://outpass-portal.vercel.app}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Pure JSON API -- the backend never serves HTML/JS/CSS of its own (the React
                // frontend is a separate deployment), so the tightest possible CSP ("no content
                // source is trusted") is safe here and costs nothing: browsers only enforce CSP
                // against document/subresource loads, and this backend never returns a document
                // for a browser to render. Kept as an explicit directive string (rather than
                // relying on Spring Security's other header defaults) so it's visible and
                // intentional, not an accidental side effect of some other setting.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Student endpoints
                        .requestMatchers("/student/**").hasRole("STUDENT")

                        // Admin-only endpoints (warden/security-guard account management)
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Room management is shared: ADMIN has full room control too, reusing
                        // the warden room endpoints rather than a duplicate admin API. Also
                        // covers the registered-students roster (used to pick a student when
                        // assigning them to a room). These matchers must come before the
                        // general "/warden/**" -> WARDEN-only rule below, since Spring Security
                        // uses the first matching rule.
                        .requestMatchers("/warden/rooms/**").hasAnyRole("WARDEN", "ADMIN")
                        .requestMatchers("/warden/students").hasAnyRole("WARDEN", "ADMIN")

                        // Warden endpoints
                        .requestMatchers("/warden/**").hasRole("WARDEN")

                        // Security guard endpoints
                        .requestMatchers("/security/**").hasRole("SECURITY_GUARD")

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Every request with no/invalid/expired JWT reaches here as an anonymous principal
    // (Spring Security's AnonymousAuthenticationFilter always inserts one), so a genuine
    // "you never authenticated" case surfaces as an AccessDeniedException, not an
    // AuthenticationException -- Spring Security only routes to the AuthenticationEntryPoint
    // for the narrower set of cases (e.g. .authenticated() against an anonymous principal),
    // not for role/hasRole() checks. Without this, both "no token" and "valid token, wrong
    // role" returned 403, which is blocking-correct but semantically wrong for the former.
    // This entry point (used when Spring *does* raise a genuine AuthenticationException) and
    // the anonymous-aware branch in accessDeniedHandler() below together make "not
    // authenticated at all" consistently 401 and "authenticated but not permitted" consistently
    // 403, without changing which requests are allowed through.
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeErrorResponse(
                response, 401, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean notAuthenticated = authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken;
            if (notAuthenticated) {
                writeErrorResponse(response, 401, "Authentication required");
            } else {
                writeErrorResponse(response, 403, "Access denied");
            }
        };
    }

    // Jackson (jackson-databind) is only a runtime-scope transitive dependency in this
    // project (pulled in by jjwt-jackson), not a compile-scope one, so an ObjectMapper can't
    // be referenced from source here -- this hand-builds the same {"success":false,
    // "message":"..."} shape ApiResponse.error(...) would normally serialize to (matching
    // spring.jackson.default-property-inclusion=non_null, which already omits the null
    // "data" field), escaping only what JSON string values require.
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escaped + "\"}");
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        // Explicit (this is also Spring Security's default): makes an unknown email and a
        // wrong password indistinguishable to the client -- both surface as
        // BadCredentialsException rather than leaking UsernameNotFoundException, so the
        // login endpoints can't be used to enumerate which emails have accounts.
        authProvider.setHideUserNotFoundExceptions(true);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}



