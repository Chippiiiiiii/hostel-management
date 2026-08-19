package com.outpass.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT Configuration Properties
 * Maps jwt.* properties from application.properties
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    /**
     * Secret key for JWT signing (256-bit recommended)
     */
    private String secret;

    /**
     * JWT token expiration time in milliseconds
     */
    private Long expiration;

    /**
     * Access token expiration time in milliseconds (default: 24 hours)
     */
    private Long accessTokenExpiration;

    /**
     * Refresh token expiration time in milliseconds (default: 7 days)
     */
    private Long refreshTokenExpiration;

    /**
     * JWT issuer identifier
     */
    private String issuer;
}

