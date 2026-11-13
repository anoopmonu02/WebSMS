package com.smsweb.sms.services.security;

import java.util.HashSet;
import java.util.Set;

/**
 * Counts events per key inside a fixed time window (for example "requests per minute for one IP") and, if asked,
 * how many different items were seen with that key (for example "how many different usernames failed from one IP",
 * the signal of password spraying). In memory only, bounded by {@link ExpiringStore}.
 */
public final class AttemptCounter {

    /** Result of one {@link #hit}: events so far in this window, different items so far, and when the window ends. */
    public record Hit(int count, int distinct, long windowEndMillis) {
    }

    private static final int MAX_DISTINCT_TRACKED = 200;

    private static final class Entry {
        final int count;
        final long start;
        final Set<String> items;

        Entry(int count, long start, Set<String> items) {
            this.count = count;
            this.start = start;
            this.items = items;
        }
    }

    private final long windowMillis;
    private final ExpiringStore<Entry> store;

    public AttemptCounter(long windowMillis, int maxEntries) {
        this.windowMillis = Math.max(1000L, windowMillis);
        this.store = new ExpiringStore<>(maxEntries, e -> e.start, (e, now) -> now - e.start >= this.windowMillis);
    }

    /** Counts one event for the key. {@code item} may be null when different items are not of interest. */
    public Hit hit(String key, String item, long now) {
        Hit[] out = new Hit[1];
        store.compute(key, now, (k, e) -> {
            Entry n;
            if (e == null || now - e.start >= windowMillis) {
                n = new Entry(1, now, new HashSet<>());
            } else {
                n = new Entry(e.count + 1, e.start, e.items);
            }
            if (item != null && n.items.size() < MAX_DISTINCT_TRACKED) {
                n.items.add(item);
            }
            out[0] = new Hit(n.count, n.items.size(), n.start + windowMillis);
            return n;
        });
        return out[0];
    }

    /** Events counted for the key in the current window (0 when none or expired). */
    public int count(String key, long now) {
        Entry e = store.get(key);
        return e == null || now - e.start >= windowMillis ? 0 : e.count;
    }

    public void clear(String key) {
        store.remove(key);
    }

    public int size() {
        return store.size();
    }
}
