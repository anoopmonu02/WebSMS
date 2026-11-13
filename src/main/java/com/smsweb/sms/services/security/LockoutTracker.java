package com.smsweb.sms.services.security;

/**
 * The lock ladder: counts wrong attempts per key (an account, or an IP address) and locks the key for a growing
 * time. It never disables anything permanently.
 *
 * <p>How it works with the default settings (5 wrong attempts, locks of 15, 30, 60, 60 minutes, 2 tries after each
 * unlock):</p>
 * <ul>
 *   <li>5 wrong attempts in a row lock the key for 15 minutes (level 1).</li>
 *   <li>After the lock ends the next 2 wrong attempts lock it for 30 minutes (level 2), then 2 more for 60 minutes
 *       (level 3), then 60 minutes each time (the last value is the cap).</li>
 *   <li>The count of wrong attempts starts again after a quiet period (idle reset) or after a correct login
 *       ({@link #clear}). The level is forgotten after the level memory (24 hours by default).</li>
 *   <li>Typing the same wrong password again does not count (a common typing habit that is not a guess), so a real
 *       user does not lock themselves out; only different wrong guesses count.</li>
 * </ul>
 *
 * <p>Pure Java, in memory, bounded by {@link ExpiringStore}; time is passed in so tests do not wait.</p>
 */
public final class LockoutTracker {

    /** Settings of one ladder. */
    public record Config(int threshold, int[] lockMinutes, int triesAfterUnlock, long idleResetMillis,
                         long levelMemoryMillis, boolean ignoreRepeated, int maxEntries) {
        long minutesFor(int level) {
            return lockMinutes[Math.min(Math.max(level, 1), lockMinutes.length) - 1];
        }
    }

    /** What one wrong attempt did. */
    public record Outcome(boolean counted, boolean newlyLocked, boolean alreadyLocked, int level,
                          long lockMinutes, long lockedUntil) {
        static Outcome counted(int level) {
            return new Outcome(true, false, false, level, 0, 0);
        }

        static Outcome notCounted(int level) {
            return new Outcome(false, false, false, level, 0, 0);
        }

        static Outcome alreadyLocked(int level, long until) {
            return new Outcome(false, false, true, level, 0, until);
        }

        static Outcome locked(int level, long minutes, long until) {
            return new Outcome(true, true, false, level, minutes, until);
        }
    }

    private static final String[] NO_RECENT = new String[0];
    private static final int RECENT_KEPT = 3;

    private static final class State {
        final int failures;
        final int level;
        final long lockedUntil;
        final long lastFailure;
        final long lastLock;
        final String[] recent;

        State(int failures, int level, long lockedUntil, long lastFailure, long lastLock, String[] recent) {
            this.failures = failures;
            this.level = level;
            this.lockedUntil = lockedUntil;
            this.lastFailure = lastFailure;
            this.lastLock = lastLock;
            this.recent = recent;
        }
    }

    private static final State EMPTY = new State(0, 0, 0, 0, 0, NO_RECENT);

    private final Config cfg;
    private final ExpiringStore<State> store;

    public LockoutTracker(Config cfg) {
        this.cfg = cfg;
        this.store = new ExpiringStore<>(cfg.maxEntries(),
                s -> Math.max(Math.max(s.lastFailure, s.lastLock), s.lockedUntil),
                (s, now) -> s.lockedUntil <= now && now - Math.max(s.lastFailure, s.lastLock) > cfg.levelMemoryMillis());
    }

    /** True while the key is locked. */
    public boolean isLocked(String key, long now) {
        State s = store.get(key);
        return s != null && s.lockedUntil > now;
    }

    /** Milliseconds left on the lock, or 0 when not locked. */
    public long remainingMillis(String key, long now) {
        State s = store.get(key);
        return s == null || s.lockedUntil <= now ? 0 : s.lockedUntil - now;
    }

    /**
     * Records one wrong attempt.
     *
     * @param fingerprint a keyed fingerprint of the wrong password (see {@link Fingerprints}), or null when the
     *                    attempt has no password to compare (for example an IP address)
     */
    public Outcome recordFailure(String key, String fingerprint, long now) {
        Outcome[] out = new Outcome[1];
        store.compute(key, now, (k, existing) -> {
            State cur = existing == null ? EMPTY : existing;
            if (cur.lockedUntil > now) {
                out[0] = Outcome.alreadyLocked(cur.level, cur.lockedUntil);
                return cur;
            }
            int failures = cur.failures;
            int level = cur.level;
            String[] recent = cur.recent;
            if (cur.lastFailure != 0 && now - cur.lastFailure > cfg.idleResetMillis()) {
                failures = 0;
                recent = NO_RECENT;
            }
            if (level > 0 && now - cur.lastLock > cfg.levelMemoryMillis()) {
                level = 0;
            }
            if (cfg.ignoreRepeated() && fingerprint != null && contains(recent, fingerprint)) {
                out[0] = Outcome.notCounted(level);
                return new State(failures, level, 0, cur.lastFailure, cur.lastLock, recent);
            }
            failures++;
            recent = push(recent, fingerprint);
            int needed = level == 0 ? cfg.threshold() : cfg.triesAfterUnlock();
            if (failures >= needed) {
                int newLevel = level + 1;
                long minutes = cfg.minutesFor(newLevel);
                long until = now + minutes * 60_000L;
                out[0] = Outcome.locked(newLevel, minutes, until);
                return new State(0, newLevel, until, now, now, NO_RECENT);
            }
            out[0] = Outcome.counted(level);
            return new State(failures, level, 0, now, cur.lastLock, recent);
        });
        return out[0];
    }

    /**
     * Locks the key now (used when a pattern, not a count, shows abuse, such as one IP failing on many accounts).
     * The lock lasts at least {@code minMinutes}. Does nothing if the key is already locked.
     */
    public Outcome lockNow(String key, long minMinutes, long now) {
        Outcome[] out = new Outcome[1];
        store.compute(key, now, (k, existing) -> {
            State cur = existing == null ? EMPTY : existing;
            if (cur.lockedUntil > now) {
                out[0] = Outcome.alreadyLocked(cur.level, cur.lockedUntil);
                return cur;
            }
            int level = cur.level;
            if (level > 0 && now - cur.lastLock > cfg.levelMemoryMillis()) {
                level = 0;
            }
            int newLevel = level + 1;
            long minutes = Math.max(cfg.minutesFor(newLevel), minMinutes);
            long until = now + minutes * 60_000L;
            out[0] = Outcome.locked(newLevel, minutes, until);
            return new State(0, newLevel, until, now, now, NO_RECENT);
        });
        return out[0];
    }

    /** Forgets everything about the key (correct login, or an admin reset the password). */
    public void clear(String key) {
        store.remove(key);
    }

    public int size() {
        return store.size();
    }

    private static boolean contains(String[] recent, String fp) {
        for (String r : recent) {
            if (r.equals(fp)) {
                return true;
            }
        }
        return false;
    }

    private static String[] push(String[] recent, String fp) {
        if (fp == null) {
            return recent;
        }
        int keep = Math.min(recent.length, RECENT_KEPT - 1);
        String[] n = new String[keep + 1];
        System.arraycopy(recent, recent.length - keep, n, 0, keep);
        n[keep] = fp;
        return n;
    }
}
