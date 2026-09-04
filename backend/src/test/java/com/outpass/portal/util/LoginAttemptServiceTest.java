package com.outpass.portal.util;

import com.outpass.portal.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * threshold=3, base=10s, max=100s for all tests below (via configureForTest), so the
 * math is easy to reason about: failures 1-2 are free, failure 3 blocks for 10s, failure
 * 4 for 20s, failure 5 for 40s, etc., capped at 100s.
 */
class LoginAttemptServiceTest {

    private static final long BASE_SECONDS = 10;
    private static final long MAX_SECONDS = 100;
    private static final int THRESHOLD = 3;

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new LoginAttemptService();
        service.setClock(clock);
        service.configureForTest(true, THRESHOLD, BASE_SECONDS, MAX_SECONDS);
    }

    @Test
    void allowsAttemptsBelowThreshold() {
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
        service.recordFailure("a@x.com");
        service.recordFailure("a@x.com");
        // Only 2 failures so far (< threshold of 3) -- must still be allowed.
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void blocksOnceThresholdReached() {
        service.recordFailure("a@x.com");
        service.recordFailure("a@x.com");
        service.recordFailure("a@x.com"); // 3rd failure == threshold

        assertThatThrownBy(() -> service.checkNotBlocked("a@x.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void backoffDurationDoublesWithEachFurtherFailure() {
        for (int i = 0; i < THRESHOLD; i++) {
            service.recordFailure("a@x.com"); // reaches threshold -> blockedUntil = now + 10s
        }
        clock.advance(9); // still within the 10s window
        assertThatThrownBy(() -> service.checkNotBlocked("a@x.com"))
                .isInstanceOf(RateLimitExceededException.class);

        clock.advance(2); // now 11s after the 3rd failure -- window elapsed
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();

        service.recordFailure("a@x.com"); // 4th failure -> exponent 1 -> 20s block
        clock.advance(19);
        assertThatThrownBy(() -> service.checkNotBlocked("a@x.com"))
                .isInstanceOf(RateLimitExceededException.class);
        clock.advance(2);
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void backoffDurationIsCappedAtMaxSeconds() {
        // Drive failureCount way past threshold so the uncapped doubling would exceed
        // MAX_SECONDS; the actual block must never exceed it.
        for (int i = 0; i < THRESHOLD + 10; i++) {
            service.recordFailure("a@x.com");
        }
        clock.advance(MAX_SECONDS - 1);
        assertThatThrownBy(() -> service.checkNotBlocked("a@x.com"))
                .isInstanceOf(RateLimitExceededException.class);
        clock.advance(2);
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void throttleIsTemporaryNotPermanent() {
        for (int i = 0; i < THRESHOLD; i++) {
            service.recordFailure("a@x.com");
        }
        assertThatThrownBy(() -> service.checkNotBlocked("a@x.com"))
                .isInstanceOf(RateLimitExceededException.class);

        clock.advance(BASE_SECONDS + 1);

        // No admin action, no manual reset -- the window simply elapses.
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginResetsFailureCountAndClearsBlock() {
        for (int i = 0; i < THRESHOLD; i++) {
            service.recordFailure("a@x.com");
        }
        service.recordSuccess("a@x.com");

        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();

        // Failure count was reset to zero, not just the block cleared -- confirm a single
        // subsequent failure alone doesn't immediately re-trigger the threshold.
        service.recordFailure("a@x.com");
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void trackingIsPerAccountKeyIndependentOfCallerSuppliedIpOrRole() {
        // A different IP or role never reaches this service at all -- the caller
        // (AuthService.login) always passes the normalized email alone as the key. Confirm
        // one account's block doesn't leak onto (or get bypassed via) a different key.
        for (int i = 0; i < THRESHOLD; i++) {
            service.recordFailure("victim@x.com");
        }
        assertThatThrownBy(() -> service.checkNotBlocked("victim@x.com"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> service.checkNotBlocked("someone-else@x.com")).doesNotThrowAnyException();
    }

    @Test
    void disabledConfigurationNeverBlocks() {
        service.configureForTest(false, THRESHOLD, BASE_SECONDS, MAX_SECONDS);
        for (int i = 0; i < THRESHOLD + 5; i++) {
            service.recordFailure("a@x.com");
        }
        assertThatCode(() -> service.checkNotBlocked("a@x.com")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
