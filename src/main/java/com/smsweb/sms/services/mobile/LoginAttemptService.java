package com.smsweb.sms.services.mobile;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks failed /api/v1/auth/login attempts per mobile number and locks a
 * number out for a cooldown period after too many failures in a row — there
 * was previously no throttling at all, so a mobile number's password could be
 * guessed with unlimited attempts and no slowdown or record of the activity.
 *
 * In-memory only (no DB table) — a restart/redeploy clears all lockouts. That
 * trade-off is intentional for a first pass: the threat this defends against
 * is an external attacker script-guessing passwords, not something a redeploy
 * gives them control over, and it avoids a schema migration for this. Move to
 * a persisted store later if multi-instance deployment makes in-memory state
 * inconsistent across nodes.
 */
@Component
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW   = Duration.ofMinutes(15); // failures older than this don't count
    private static final Duration LOCKOUT  = Duration.ofMinutes(15); // how long a lockout lasts once triggered

    private record Attempts(int count, Instant windowStart, Instant lockedUntil) {}

    private final Map<String, Attempts> byMobile = new ConcurrentHashMap<>();

    /** True if this mobile number is currently locked out. */
    public boolean isLocked(String mobile) {
        Attempts a = byMobile.get(mobile);
        return a != null && a.lockedUntil() != null && Instant.now().isBefore(a.lockedUntil());
    }

    /** Minutes remaining on the current lockout, or 0 if not locked. */
    public long remainingLockoutMinutes(String mobile) {
        Attempts a = byMobile.get(mobile);
        if (a == null || a.lockedUntil() == null) return 0;
        Duration remaining = Duration.between(Instant.now(), a.lockedUntil());
        return remaining.isNegative() ? 0 : remaining.toMinutes() + 1; // round up
    }

    /** Call on a failed login (wrong password OR unknown mobile) — counts either way. */
    public void recordFailure(String mobile) {
        Instant now = Instant.now();
        byMobile.compute(mobile, (m, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(WINDOW))) {
                // First failure, or previous window expired — start fresh.
                return new Attempts(1, now, null);
            }
            int newCount = existing.count() + 1;
            if (newCount >= MAX_ATTEMPTS) {
                log.warn("Mobile {} locked out after {} failed login attempts", mobile, newCount);
                return new Attempts(newCount, existing.windowStart(), now.plus(LOCKOUT));
            }
            return new Attempts(newCount, existing.windowStart(), existing.lockedUntil());
        });
    }

    /** Call on a successful login — clears any prior failure count for this mobile. */
    public void recordSuccess(String mobile) {
        byMobile.remove(mobile);
    }
}
