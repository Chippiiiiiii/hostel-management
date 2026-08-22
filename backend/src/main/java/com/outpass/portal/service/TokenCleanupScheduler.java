package com.outpass.portal.service;

import com.outpass.portal.repository.EmailVerificationTokenRepository;
import com.outpass.portal.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Purges expired, already-unusable token rows so they don't accumulate forever.
 * Expiration itself is always re-checked at the point of use (isExpired()) — this
 * job is housekeeping only, never the source of truth for whether a token is valid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${token.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        try {
            emailVerificationTokenRepository.deleteExpiredTokens(now);
        } catch (Exception e) {
            log.error("Failed to purge expired email verification tokens: {}", e.getMessage(), e);
        }

        try {
            passwordResetTokenRepository.deleteExpiredTokens(now);
        } catch (Exception e) {
            log.error("Failed to purge expired password reset tokens: {}", e.getMessage(), e);
        }

        try {
            refreshTokenService.deleteExpiredTokens();
        } catch (Exception e) {
            log.error("Failed to purge expired refresh tokens: {}", e.getMessage(), e);
        }

        log.info("Expired token cleanup run completed");
    }
}
