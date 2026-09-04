package com.outpass.portal.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the generic key-based limiter the way AuthController.enforceLoginRateLimit
 * actually uses it for login: an "IP+email" bucket per targeted account, and a separate,
 * higher-ceiling "IP only" bucket that stays shared no matter which email is guessed or
 * which role-specific login endpoint (student/warden/security/admin) is hit -- since none
 * of those are part of either key.
 */
class RateLimiterServiceTest {

    @Test
    void ipPlusEmailBucketIsIndependentPerAccount() {
        RateLimiterService limiter = new RateLimiterService();
        String ip = "1.2.3.4";

        // Two different targeted accounts from the same IP get independent buckets --
        // exhausting one must not affect the other.
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isAllowed("authrl:login:" + ip + ":victim1@x.com", 3, 3600)).isTrue();
        }
        assertThat(limiter.isAllowed("authrl:login:" + ip + ":victim1@x.com", 3, 3600)).isFalse();

        assertThat(limiter.isAllowed("authrl:login:" + ip + ":victim2@x.com", 3, 3600)).isTrue();
    }

    @Test
    void ipOnlyBucketIsSharedAcrossDifferentGuessedEmails() {
        RateLimiterService limiter = new RateLimiterService();
        String ip = "5.6.7.8";
        String key = "authrl:login:ip:" + ip;

        // A credential-stuffing sweep tries a different email on every request, so the
        // IP+email bucket alone (see above) never fires for any single one of them -- the
        // IP-only bucket must still catch the aggregate volume from this one IP.
        assertThat(limiter.isAllowed(key, 5, 3600)).isTrue();
        assertThat(limiter.isAllowed(key, 5, 3600)).isTrue();
        assertThat(limiter.isAllowed(key, 5, 3600)).isTrue();
        assertThat(limiter.isAllowed(key, 5, 3600)).isTrue();
        assertThat(limiter.isAllowed(key, 5, 3600)).isTrue();
        assertThat(limiter.isAllowed(key, 5, 3600)).isFalse(); // 6th attempt, still same IP
    }

    @Test
    void sameAccountAndIpBucketIsSharedRegardlessOfWhichRoleLoginEndpointIsUsed() {
        // AuthController.enforceLoginRateLimit builds this exact key from (ip, normalized
        // email) with no role/endpoint segment -- so /auth/student/login, /auth/warden/login,
        // /auth/security/login, and /auth/admin/login for the same email+IP all resolve to
        // the identical key string below and therefore share one counter.
        RateLimiterService limiter = new RateLimiterService();
        String key = "authrl:login:9.9.9.9:target@x.com";

        assertThat(limiter.isAllowed(key, 2, 3600)).isTrue();  // e.g. attempt via /auth/student/login
        assertThat(limiter.isAllowed(key, 2, 3600)).isTrue();  // e.g. attempt via /auth/warden/login
        // e.g. attempt via /auth/admin/login -- blocked by the SAME bucket, proving
        // switching endpoints doesn't grant a fresh allowance.
        assertThat(limiter.isAllowed(key, 2, 3600)).isFalse();
    }

    @Test
    void bucketEventuallyExpiresAfterItsWindow() throws InterruptedException {
        RateLimiterService limiter = new RateLimiterService();
        String key = "authrl:login:1.1.1.1:expiry@x.com";

        assertThat(limiter.isAllowed(key, 1, 1)).isTrue();
        assertThat(limiter.isAllowed(key, 1, 1)).isFalse();

        Thread.sleep(1100); // wait out the 1-second window

        assertThat(limiter.isAllowed(key, 1, 1)).isTrue();
    }
}
