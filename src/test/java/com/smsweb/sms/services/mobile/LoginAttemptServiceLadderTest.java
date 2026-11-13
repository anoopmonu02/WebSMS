package com.smsweb.sms.services.mobile;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceLadderTest {

    /** A clock the test can move forward. */
    static final class Moving extends Clock {
        Instant now = Instant.parse("2026-11-03T10:00:00Z");

        void plusMinutes(long m) {
            now = now.plus(Duration.ofMinutes(m));
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private void fail(LoginAttemptService s, int n) {
        for (int i = 0; i < n; i++) s.recordFailure("9876543210");
    }

    @Test
    void locksGrowFrom15To30To60Minutes() {
        Moving c = new Moving();
        LoginAttemptService s = new LoginAttemptService(c);
        fail(s, 5);
        assertTrue(s.isLocked("9876543210"));
        assertTrue(s.remainingLockoutMinutes("9876543210") <= 16);
        c.plusMinutes(16);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 1);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 1);                       // 2 tries after unlock -> 30 minutes
        assertTrue(s.isLocked("9876543210"));
        c.plusMinutes(29);
        assertTrue(s.isLocked("9876543210"));
        c.plusMinutes(2);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 2);                       // -> 60 minutes
        c.plusMinutes(59);
        assertTrue(s.isLocked("9876543210"));
        c.plusMinutes(2);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 2);                       // stays at 60
        c.plusMinutes(59);
        assertTrue(s.isLocked("9876543210"));
    }

    @Test
    void successOrAdminResetStartsOver() {
        Moving c = new Moving();
        LoginAttemptService s = new LoginAttemptService(c);
        fail(s, 5);
        c.plusMinutes(16);
        s.clear("9876543210");
        fail(s, 4);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 1);
        assertTrue(s.isLocked("9876543210"));
        assertTrue(s.remainingLockoutMinutes("9876543210") <= 16);
    }

    @Test
    void stepIsForgottenAfter24Hours() {
        Moving c = new Moving();
        LoginAttemptService s = new LoginAttemptService(c);
        fail(s, 5);
        c.plusMinutes(25 * 60);
        fail(s, 4);
        assertFalse(s.isLocked("9876543210"));
        fail(s, 1);
        assertTrue(s.remainingLockoutMinutes("9876543210") <= 16);
    }
}
