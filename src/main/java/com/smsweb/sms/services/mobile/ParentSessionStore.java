package com.smsweb.sms.services.mobile;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds short-lived parent session tokens issued after mobile+password auth
 * when multiple children are found.
 *
 * Token TTL: 10 minutes.  Expired entries are lazily evicted on every read/write.
 */
@Component
public class ParentSessionStore {

    private static final long TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private record Entry(String mobile, Instant expiry) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Creates a new temp token for the given mobile number. */
    public String createToken(String mobile) {
        evictExpired();
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(mobile, Instant.now().plusMillis(TTL_MS)));
        return token;
    }

    /**
     * Validates the token and returns the associated mobile number,
     * or null if the token is missing / expired.
     * The token is consumed (single-use) on successful validation.
     */
    public String validateAndConsume(String token) {
        evictExpired();
        Entry entry = store.remove(token);
        if (entry == null || Instant.now().isAfter(entry.expiry())) return null;
        return entry.mobile();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiry()));
    }
}
