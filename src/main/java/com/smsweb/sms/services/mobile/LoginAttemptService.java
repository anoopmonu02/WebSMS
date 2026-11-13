package com.smsweb.sms.services.mobile;

import com.smsweb.sms.services.security.ExpiringStore;
import com.smsweb.sms.services.security.Masking;
import com.smsweb.sms.services.security.SecurityActivityLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
 *
 * Phase 1: bounded memory and masked numbers in the log. Phase 2: repeat locks get longer. 5 wrong attempts lock the
 * number for 15 minutes (as before); after it unlocks, 2 more wrong attempts lock it for 30 minutes, then 2 more for
 * 60 minutes, then 60 minutes each time. The step is forgotten after 24 hours without a lock, and a correct login
 * (or an admin password reset, see {@link #clear}) starts again from the beginning.
 */
@Component
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW   = Duration.ofMinutes(15); // failures older than this don't count
    private static final long[] LOCK_MINUTES = {15, 30, 60};           // 1st, 2nd, 3rd and later lock
    private static final int TRIES_AFTER_UNLOCK = 2;                      // wrong attempts that re-lock after an unlock
    private static final Duration LEVEL_MEMORY = Duration.ofHours(24);    // the step is forgotten after this

    private final Clock clock;
    private volatile SecurityActivityLog activity;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    /** For tests: a clock that can be moved forward. */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Optional: the short list of recent events shown to admins. Without it nothing is listed. */
    @Autowired(required = false)
    public void setActivityLog(SecurityActivityLog activity) {
        this.activity = activity;
    }

    private record Attempts(int count, Instant windowStart, Instant lockedUntil, int level, Instant lastLock) {}

    private static final int MAX_ENTRIES = 50_000;

    // An entry is useless once its window and any lock are over, so it can be dropped.
    private final ExpiringStore<Attempts> byMobile = new ExpiringStore<>(MAX_ENTRIES,
            a -> Math.max(a.windowStart().toEpochMilli(),
                    a.lockedUntil() == null ? 0L : a.lockedUntil().toEpochMilli()),
            (a, now) -> now > a.windowStart().plus(WINDOW).toEpochMilli()
                    && (a.lockedUntil() == null || now > a.lockedUntil().toEpochMilli())
                    && (a.lastLock() == null || now > a.lastLock().plus(LEVEL_MEMORY).toEpochMilli()));

    /** True if this mobile number is currently locked out. */
    public boolean isLocked(String mobile) {
        Attempts a = byMobile.get(mobile);
        return a != null && a.lockedUntil() != null && Instant.now(clock).isBefore(a.lockedUntil());
    }

    /** Minutes remaining on the current lockout, or 0 if not locked. */
    public long remainingLockoutMinutes(String mobile) {
        Attempts a = byMobile.get(mobile);
        if (a == null || a.lockedUntil() == null) return 0;
        Duration remaining = Duration.between(Instant.now(clock), a.lockedUntil());
        return remaining.isNegative() ? 0 : remaining.toMinutes() + 1; // round up
    }

    /** Call on a failed login (wrong password OR unknown mobile) — counts either way. */
    public void recordFailure(String mobile) {
        Instant now = Instant.now(clock);
        byMobile.compute(mobile, now.toEpochMilli(), (m, existing) -> {
            if (existing != null && existing.lockedUntil() != null && now.isBefore(existing.lockedUntil())) {
                return existing; // still locked: nothing more to count
            }
            int level = existing == null ? 0 : existing.level();
            Instant lastLock = existing == null ? null : existing.lastLock();
            if (level > 0 && (lastLock == null || now.isAfter(lastLock.plus(LEVEL_MEMORY)))) {
                level = 0;
                lastLock = null;
            }
            int count;
            Instant windowStart;
            if (existing == null || now.isAfter(existing.windowStart().plus(WINDOW))) {
                count = 1;                 // first failure, or previous window expired — start fresh
                windowStart = now;
            } else {
                count = existing.count() + 1;
                windowStart = existing.windowStart();
            }
            int needed = level == 0 ? MAX_ATTEMPTS : TRIES_AFTER_UNLOCK;
            if (count >= needed) {
                int newLevel = level + 1;
                long minutes = LOCK_MINUTES[Math.min(newLevel, LOCK_MINUTES.length) - 1];
                log.warn("Mobile {} locked out after {} failed login attempts (lock {}, {} minutes)",
                        Masking.mobile(mobile), count, newLevel, minutes);
                SecurityActivityLog a = activity;
                if (a != null) {
                    a.parentLock(Masking.mobile(mobile), newLevel, minutes);
                }
                return new Attempts(count, windowStart, now.plus(Duration.ofMinutes(minutes)), newLevel, now);
            }
            return new Attempts(count, windowStart, null, level, lastLock);
        });
    }

    /** Call on a successful login — clears any prior failure count for this mobile. */
    public void recordSuccess(String mobile) {
        byMobile.remove(mobile);
    }

    /** Forgets this number's failures and lock step (an admin reset the password). Same as a successful login. */
    public void clear(String mobile) {
        byMobile.remove(mobile);
    }
}
