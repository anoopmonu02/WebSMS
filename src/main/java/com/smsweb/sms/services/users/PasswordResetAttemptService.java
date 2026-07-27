package com.smsweb.sms.services.users;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles POST /auth/forgot-password per email address. Previously a
 * caller could submit unlimited reset requests for the same email with no
 * slowdown at all — enabling mailbox-spamming (repeated reset emails to a
 * victim) and making timing-based enumeration easier.
 *
 * In-memory only, same trade-off as services.mobile.LoginAttemptService: a
 * restart clears all counters, which is acceptable for the threat this
 * defends against (a script hammering the endpoint), not something worth a
 * schema migration for.
 */
@Component
public class PasswordResetAttemptService {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant windowStart) {}

    private final Map<String, Attempts> byEmail = new ConcurrentHashMap<>();

    /** True if this email has already hit the request limit within the current window. */
    public boolean isLocked(String email) {
        Attempts a = byEmail.get(normalize(email));
        if (a == null) return false;
        if (Instant.now().isAfter(a.windowStart().plus(WINDOW))) return false; // window expired
        return a.count() >= MAX_ATTEMPTS;
    }

    /** Call on every forgot-password submission, regardless of outcome. */
    public void recordAttempt(String email) {
        String key = normalize(email);
        Instant now = Instant.now();
        byEmail.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(WINDOW))) {
                return new Attempts(1, now);
            }
            return new Attempts(existing.count() + 1, existing.windowStart());
        });
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
