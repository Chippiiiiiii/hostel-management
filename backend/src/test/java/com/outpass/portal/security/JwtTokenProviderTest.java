package com.outpass.portal.security;

import com.outpass.portal.config.JwtConfig;
import com.outpass.portal.model.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real (non-mocked) JWT round-trip tests against the actual jjwt-backed JwtTokenProvider --
 * exercising the token-security scenarios from the audit's regression-testing checklist
 * (expired/malformed/tampered/alg:none/wrong-key/missing token) against the real signing and
 * parsing code, not just re-reading it. No vulnerability was found here in the prior audit
 * (symmetric-only HMAC, no algorithm-negotiation surface) -- these tests confirm that holds by
 * actually attempting each attack, rather than declaring the area secure from inspection alone.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256!!";

    private JwtConfig jwtConfig;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret(SECRET);
        jwtConfig.setAccessTokenExpiration(3600000L);
        jwtConfig.setRefreshTokenExpiration(604800000L);
        jwtConfig.setIssuer("outpass-portal-test");

        provider = new JwtTokenProvider(jwtConfig);
        provider.init();
    }

    private Authentication studentAuthentication() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(42L).email("student@x.com").password("hashed").role(Role.STUDENT)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .enabled(true).build();
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void validTokenRoundTripsCorrectly() {
        String token = provider.generateAccessToken(studentAuthentication());

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(provider.getEmailFromToken(token)).isEqualTo("student@x.com");
    }

    @Test
    void expiredTokenIsRejected() {
        jwtConfig.setAccessTokenExpiration(-1000L); // already expired the instant it's minted
        String token = provider.generateAccessToken(studentAuthentication());

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(provider.validateToken("not-a-jwt-at-all")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = provider.generateAccessToken(studentAuthentication());
        // Flip the last character of the signature segment.
        String[] parts = token.split("\\.");
        char lastChar = parts[2].charAt(parts[2].length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 1) + replacement;
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void tamperedPayloadIsRejectedEvenIfOnlyTheRoleClaimChanges() {
        // Simulates a privilege-escalation attempt: decode the payload, change "role":"STUDENT"
        // to "role":"ADMIN", re-encode, and re-attach the ORIGINAL (now-mismatched) signature.
        String token = provider.generateAccessToken(studentAuthentication());
        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayloadJson = payloadJson.replace("\"STUDENT\"", "\"ADMIN\"");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void algNoneTokenIsRejected() {
        // Classic alg:none bypass attempt: a header claiming no signature algorithm, with an
        // empty signature segment.
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"42\",\"role\":\"ADMIN\",\"exp\":" + (new Date().getTime() / 1000 + 3600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String algNoneToken = header + "." + payload + ".";

        assertThat(provider.validateToken(algNoneToken)).isFalse();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-that-nobody-shares!!".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject("42")
                .claim("email", "student@x.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .issuer("outpass-portal-test")
                .signWith(wrongKey)
                .compact();

        assertThat(provider.validateToken(foreignToken)).isFalse();
    }
}
