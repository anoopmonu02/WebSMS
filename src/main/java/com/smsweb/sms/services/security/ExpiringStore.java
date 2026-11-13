package com.smsweb.sms.services.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;

/**
 * A small in-memory map for attempt counters that cannot grow without limit.
 *
 * <p>Why it exists: the earlier login limiters kept one entry per mobile number or email forever, so a script
 * trying millions of made-up numbers slowly used up memory. This store removes entries that have expired (checked at
 * most once a minute, or once a second when it is full) and, if it is still full, drops the least recently touched
 * ones. Lookups and updates stay O(1) on the normal path.</p>
 *
 * <p>Time is always passed in (epoch milliseconds) so tests do not need to wait.</p>
 */
public final class ExpiringStore<V> {

    private static final long PURGE_EVERY_MS = 60_000L;
    private static final long FULL_PURGE_GAP_MS = 1_000L;

    private final ConcurrentHashMap<String, V> map = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final ToLongFunction<V> lastTouched;
    private final BiPredicate<V, Long> expired;
    private final AtomicLong lastPurge = new AtomicLong();

    /**
     * @param maxEntries  soft cap; when reached, expired entries are removed first, then the oldest ones
     * @param lastTouched when the value was last used (epoch ms); the smallest values are dropped first
     * @param expired     true when the value no longer matters at the given time
     */
    public ExpiringStore(int maxEntries, ToLongFunction<V> lastTouched, BiPredicate<V, Long> expired) {
        this.maxEntries = Math.max(10, maxEntries);
        this.lastTouched = lastTouched;
        this.expired = expired;
    }

    /** Atomically creates or updates the value for the key; the function may return null to remove it. */
    public V compute(String key, long now, BiFunction<String, V, V> fn) {
        maybePurge(now);
        return map.compute(key, fn);
    }

    public V get(String key) {
        return map.get(key);
    }

    public void remove(String key) {
        map.remove(key);
    }

    public int size() {
        return map.size();
    }

    private void maybePurge(long now) {
        boolean full = map.size() >= maxEntries;
        long last = lastPurge.get();
        if (now - last < (full ? FULL_PURGE_GAP_MS : PURGE_EVERY_MS)) {
            return;
        }
        if (!lastPurge.compareAndSet(last, now)) {
            return; // another thread is already purging
        }
        map.values().removeIf(v -> expired.test(v, now));
        if (map.size() >= maxEntries) {
            evictOldest();
        }
    }

    private void evictOldest() {
        int target = maxEntries - Math.max(1, maxEntries / 10);
        List<Map.Entry<String, V>> all = new ArrayList<>(map.entrySet());
        all.sort(Comparator.comparingLong(e -> lastTouched.applyAsLong(e.getValue())));
        for (Map.Entry<String, V> e : all) {
            if (map.size() <= target) {
                break;
            }
            map.remove(e.getKey(), e.getValue());
        }
    }
}
