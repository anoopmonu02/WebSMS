package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ApiSustainedAndSinkTest {

    private static final long MIN = 60_000L;
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private ThrottleSettings settings(String mode) {
        Map<String, String> m = new HashMap<>();
        m.put("app.security.throttle.mode", mode);
        return new ThrottleSettings(m::get);
    }

    @Test
    void fiveMinutesInARowOverTheLimitHoldsTheCallerForTenMinutes() {
        ApiRateLimitService s = new ApiRateLimitService(settings("enforce"), clock::get);
        boolean held = false;
        for (int minute = 0; minute < 5; minute++) {
            for (int i = 0; i < 125; i++) {
                s.check(ApiRateLimitService.Kind.GENERAL, "u1", "9.9.9.9");
            }
            if (minute < 4) clock.addAndGet(MIN + 1);
        }
        // now the 5th over-limit minute has been recorded: the caller is held even after the minute rolls over
        clock.addAndGet(MIN + 1);
        ApiRateLimitService.Decision d = s.check(ApiRateLimitService.Kind.GENERAL, "u1", "9.9.9.9");
        assertTrue(d.block());
        assertTrue(d.retryAfterSeconds() > 60);
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "other-user", "8.8.8.8").block());
        assertFalse(s.check(ApiRateLimitService.Kind.REFRESH, null, "9.9.9.9").block());
        clock.addAndGet(11 * MIN);
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u1", "9.9.9.9").block());
    }

    @Test
    void aQuietMinuteBreaksTheStreak() {
        ApiRateLimitService s = new ApiRateLimitService(settings("enforce"), clock::get);
        for (int round = 0; round < 8; round++) {
            for (int i = 0; i < 125; i++) {
                s.check(ApiRateLimitService.Kind.GENERAL, "u2", "7.7.7.7");
            }
            clock.addAndGet(3 * MIN); // idle gap
        }
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u2", "7.7.7.7").block());
    }

    @Test
    void logModeNeverHolds() {
        ApiRateLimitService s = new ApiRateLimitService(settings("log"), clock::get);
        for (int minute = 0; minute < 8; minute++) {
            for (int i = 0; i < 130; i++) {
                assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u3", "6.6.6.6").block());
            }
            clock.addAndGet(MIN + 1);
        }
    }

    // ── events for the alert e-mail ──

    private static final class Recorder implements SecurityEventSink {
        final List<String> events = new ArrayList<>();

        @Override
        public void accountLocked(LoginThrottleService.Channel c, String key, int level, long minutes, String ip) {
            events.add("account:" + key + ":" + level + ":" + minutes);
        }

        @Override
        public void ipBlocked(LoginThrottleService.Channel c, String ip, int level, long minutes, int accounts, boolean spray) {
            events.add("ip:" + ip + ":" + spray + ":" + accounts);
        }
    }

    @Test
    void enforceModePublishesLocksAndSpraying() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce"), clock::get);
        Recorder r = new Recorder();
        t.setSink(r);
        for (int i = 0; i < 5; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "Raj", "pw" + i, "1.1.1." + i);
        assertEquals("account:raj:1:15", r.events.get(0));
        clock.addAndGet(16 * MIN);
        for (int i = 10; i < 12; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "pw" + i, "2.2.2.2");
        assertEquals("account:raj:2:30", r.events.get(1));
        r.events.clear();
        for (int i = 0; i < 20; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "user" + i, "x", "5.5.5.5");
        assertTrue(r.events.contains("ip:5.5.5.5:true:20"));
    }

    @Test
    void logModeAndFailingReceiverDoNotPublishOrBreak() {
        LoginThrottleService t = new LoginThrottleService(settings("log"), clock::get);
        Recorder r = new Recorder();
        t.setSink(r);
        for (int i = 0; i < 6; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "pw" + i, "1.1.1.1");
        assertTrue(r.events.isEmpty());
        LoginThrottleService e = new LoginThrottleService(settings("enforce"), clock::get);
        e.setSink(new SecurityEventSink() {
            public void accountLocked(LoginThrottleService.Channel c, String k, int l, long m, String ip) { throw new IllegalStateException("boom"); }
            public void ipBlocked(LoginThrottleService.Channel c, String ip, int l, long m, int a, boolean s) { throw new IllegalStateException("boom"); }
        });
        for (int i = 0; i < 5; i++) e.recordFailure(LoginThrottleService.Channel.WEB, "raj", "pw" + i, "1.1.1.1");
        assertTrue(e.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "1.1.1.1"));
    }
}
