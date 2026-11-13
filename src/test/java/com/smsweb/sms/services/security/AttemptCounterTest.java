package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttemptCounterTest {

    @Test
    void countsWithinWindowAndResetsAfter() {
        AttemptCounter c = new AttemptCounter(60_000, 1000);
        assertEquals(1, c.hit("k", null, 0).count());
        assertEquals(2, c.hit("k", null, 30_000).count());
        assertEquals(2, c.count("k", 30_001));
        assertEquals(1, c.hit("k", null, 60_001).count());
        assertEquals(0, c.count("other", 0));
    }

    @Test
    void countsDistinctItems() {
        AttemptCounter c = new AttemptCounter(60_000, 1000);
        c.hit("ip", "a", 0);
        c.hit("ip", "a", 1);
        AttemptCounter.Hit h = c.hit("ip", "b", 2);
        assertEquals(3, h.count());
        assertEquals(2, h.distinct());
    }

    @Test
    void storeIsBounded() {
        AttemptCounter c = new AttemptCounter(60_000, 100);
        for (int i = 0; i < 5000; i++) {
            c.hit("k" + i, null, i * 2000L);
        }
        assertTrue(c.size() <= 100);
        c.clear("k4999");
        assertEquals(0, c.count("k4999", 5000 * 2000L));
    }
}
