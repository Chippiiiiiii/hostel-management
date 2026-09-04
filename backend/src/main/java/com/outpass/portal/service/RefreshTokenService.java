package com.outpass.portal.service;

import com.outpass.portal.config.JwtConfig;
import com.outpass.portal.model.entity.RefreshToken;
import com.outpass.portal.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;

    public RefreshToken createRefreshToken(Long userId, String userType) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .userType(userType)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiration()))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expired. Please login again.");
        }
        return token;
    }

    // Single-use rotation, made atomic: AuthService.refreshToken calls this to consume the
    // presented token before issuing a new one in its place. Returns the number of rows the
    // DELETE actually affected (0 or 1) rather than taking a previously-fetched entity, so the
    // delete itself -- not a prior findByToken() lookup -- is the single source of truth for
    // "did I win the race to use this token". Two concurrent requests presenting the identical
    // token string both call this; the database guarantees only one of them can ever see 1
    // (the other sees 0 once the row is already gone) even if both requests observed the token
    // as present via findByToken() first. The caller must reject the request when this returns
    // anything other than 1.
    @Transactional
    public int consumeToken(String token) {
        return refreshTokenRepository.deleteByTokenAtomic(token);
    }

    @Transactional
    public void deleteByUserIdAndUserType(Long userId, String userType) {
        refreshTokenRepository.deleteByUserIdAndUserType(userId, userType);
    }

    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(Instant.now());
    }
}

