package com.outpass.portal.util;

import com.outpass.portal.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Account-level exponential backoff against repeated failed logins, keyed by normalized
// email ONLY (never IP or role) -- see AuthService.login. This means switching IPs or
// switching between the student/warden/security/admin login endpoints for the same
// account shares the same counter and can't be used to dodge it. Complements the IP(+email)
// sliding-window request-volume limits in AuthController, which bound overall attempt
// volume regardless of success/failure rather than tracking consecutive failures.
//
// In-memory by design, matching RateLimiterService -- correct for this app's current
// single-instance Render deployment (see render.yaml). A multi-instance deployment would
// need this state moved to a shared store (e.g. Redis) since each instance would otherwise
// track failures independently and undercount an attacker's true attempt volume.
@Component
public class LoginAttemptService {

    @Value("${security.login-backoff.enabled:true}")
    private boolean enabled;

    @Value("${security.login-backoff.threshold:5}")
    private int threshold;

    @Value("${security.login-backoff.base-seconds:30}")
    private long baseSeconds;

    @Value("${security.login-backoff.max-seconds:900}")
    private long maxSeconds;

    private Clock clock = Clock.systemUTC();

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    // Throws RateLimitExceededException if this account is currently within a throttle
    // window from prior failures; a no-op otherwise (including for an account that has
    // never failed, or that has since succeeded and been cleared).
    public void checkNotBlocked(String key) {
        if (!enabled || key == null) {
            return;
        }
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return;
        }
        synchronized (info) {
            Instant now = clock.instant();
            if (info.blockedUntil != null && now.isBefore(info.blockedUntil)) {
                long secondsLeft = Duration.between(now, info.blockedUntil).getSeconds() + 1;
                throw new RateLimitExceededException(
                        "Too many failed login attempts. Please try again in " + secondsLeft + " seconds.");
            }
        }
    }

    // Increments the consecutive-failure count and, once it reaches `threshold`, sets (or
    // extends) a temporary throttle window that doubles with every further failure --
    // never a permanent lock. The account recovers on its own once the window elapses, or
    // immediately on the next successful login (see recordSuccess).
    public void recordFailure(String key) {
        if (!enabled || key == null) {
            return;
        }
        AttemptInfo info = attempts.computeIfAbsent(key, k -> new AttemptInfo());
        synchronized (info) {
            info.failureCount++;
            if (info.failureCount >= threshold) {
                long exponent = Math.min(info.failureCount - threshold, 20);
                long delaySeconds = Math.min(maxSeconds, baseSeconds * (1L << exponent));
                info.blockedUntil = clock.instant().plusSeconds(delaySeconds);
            }
        }
    }

    // Clears all tracked failures for this account. Called on every successful login so a
    // legitimate user who eventually gets their password right isn't left throttled by
    // their own earlier typos.
    public void recordSuccess(String key) {
        if (key == null) {
            return;
        }
        attempts.remove(key);
    }

    // Test-only hooks -- package-private, no production caller.
    void setClock(Clock clock) {
        this.clock = clock;
    }

    void configureForTest(boolean enabled, int threshold, long baseSeconds, long maxSeconds) {
        this.enabled = enabled;
        this.threshold = threshold;
        this.baseSeconds = baseSeconds;
        this.maxSeconds = maxSeconds;
    }

    private static class AttemptInfo {
        int failureCount = 0;
        Instant blockedUntil;
    }
}
