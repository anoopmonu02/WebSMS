package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LockoutTrackerTest {

    private static final long MIN = 60_000L;

    private LockoutTracker tracker() {
        return new LockoutTracker(new LockoutTracker.Config(5, new int[]{15, 30, 60, 60}, 2,
                30 * MIN, 24 * 60 * MIN, true, 1000));
    }

    private void wrong(LockoutTracker t, String key, int from, int to, long now) {
        for (int i = from; i <= to; i++) {
            t.recordFailure(key, "pw" + i, now);
        }
    }

    @Test
    void fiveDifferentWrongPasswordsLockForFifteenMinutes() {
        LockoutTracker t = tracker();
        wrong(t, "a", 1, 4, 0);
        assertFalse(t.isLocked("a", 0));
        LockoutTracker.Outcome o = t.recordFailure("a", "pw5", 1000);
        assertTrue(o.newlyLocked());
        assertEquals(1, o.level());
        assertEquals(15, o.lockMinutes());
        assertTrue(t.isLocked("a", 1000 + 14 * MIN));
        assertFalse(t.isLocked("a", 1000 + 15 * MIN + 1));
    }

    @Test
    void ladderIs15Then30Then60Then60() {
        LockoutTracker t = tracker();
        long now = 0;
        wrong(t, "a", 1, 5, now);
        now += 16 * MIN;
        LockoutTracker.Outcome o2 = null;
        for (int i = 6; i <= 7; i++) o2 = t.recordFailure("a", "pw" + i, now);
        assertEquals(30, o2.lockMinutes());
        now += 31 * MIN;
        LockoutTracker.Outcome o3 = null;
        for (int i = 8; i <= 9; i++) o3 = t.recordFailure("a", "pw" + i, now);
        assertEquals(60, o3.lockMinutes());
        now += 61 * MIN;
        LockoutTracker.Outcome o4 = null;
        for (int i = 10; i <= 11; i++) o4 = t.recordFailure("a", "pw" + i, now);
        assertEquals(60, o4.lockMinutes());
        assertEquals(4, o4.level());
    }

    @Test
    void sameWrongPasswordTypedAgainIsNotCounted() {
        LockoutTracker t = tracker();
        for (int i = 0; i < 20; i++) {
            t.recordFailure("a", "same", i);
        }
        assertFalse(t.isLocked("a", 100));
    }

    @Test
    void correctLoginClearsEverything() {
        LockoutTracker t = tracker();
        wrong(t, "a", 1, 5, 0);
        t.clear("a");
        assertFalse(t.isLocked("a", 1));
        wrong(t, "a", 1, 4, 2);
        assertFalse(t.isLocked("a", 3));
    }

    @Test
    void countStartsAgainAfterIdlePeriod() {
        LockoutTracker t = tracker();
        long base = 1_000_000L; // a real clock is never 0
        wrong(t, "a", 1, 4, base);
        LockoutTracker.Outcome o = t.recordFailure("a", "pw9", base + 31 * MIN);
        assertFalse(o.newlyLocked());
        wrong(t, "a", 10, 12, base + 31 * MIN + 1);
        assertFalse(t.isLocked("a", base + 31 * MIN + 2));
        wrong(t, "a", 13, 13, base + 31 * MIN + 3);
        assertTrue(t.isLocked("a", base + 31 * MIN + 4));
    }

    @Test
    void levelIsForgottenAfterMemoryPeriod() {
        LockoutTracker t = tracker();
        wrong(t, "a", 1, 5, 0);
        long later = 25 * 60 * MIN;
        wrong(t, "a", 6, 10, later);
        assertTrue(t.isLocked("a", later + 1));
        assertEquals(15 * MIN - 1, t.remainingMillis("a", later + 1));
    }

    @Test
    void failuresWhileLockedAreIgnoredAndLockNowWorks() {
        LockoutTracker t = tracker();
        wrong(t, "a", 1, 5, 0);
        assertTrue(t.recordFailure("a", "x", 5).alreadyLocked());
        LockoutTracker.Outcome o = t.lockNow("ip", 30, 0);
        assertTrue(o.newlyLocked());
        assertEquals(30, o.lockMinutes());
        assertTrue(t.isLocked("ip", 29 * MIN));
        assertEquals(0, t.remainingMillis("nobody", 0));
    }

    @Test
    void storeStaysBounded() {
        LockoutTracker t = new LockoutTracker(new LockoutTracker.Config(5, new int[]{15}, 2, 30 * MIN, 24 * 60 * MIN, true, 100));
        for (int i = 0; i < 5000; i++) {
            t.recordFailure("k" + i, "p", i * 2000L);
        }
        assertTrue(t.size() <= 100);
    }
}
