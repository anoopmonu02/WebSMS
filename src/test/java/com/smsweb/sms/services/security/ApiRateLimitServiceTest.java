package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ApiRateLimitServiceTest {

    private final AtomicLong clock = new AtomicLong(0);

    private ApiRateLimitService service(String mode) {
        Map<String, String> m = new HashMap<>();
        m.put("app.security.throttle.mode", mode);
        return new ApiRateLimitService(new ThrottleSettings(m::get), clock::get);
    }

    @Test
    void kindsOfPaths() {
        assertEquals(ApiRateLimitService.Kind.SKIP, ApiRateLimitService.kindOf("/api/v1/auth/ping"));
        assertEquals(ApiRateLimitService.Kind.REFRESH, ApiRateLimitService.kindOf("/api/v1/auth/refresh/"));
        assertEquals(ApiRateLimitService.Kind.GENERAL, ApiRateLimitService.kindOf("/api/v1/fees"));
        assertEquals(ApiRateLimitService.Kind.GENERAL, ApiRateLimitService.kindOf(null));
    }

    @Test
    void enforceBlocksOver120PerMinutePerUserThenResets() {
        ApiRateLimitService s = service("enforce");
        for (int i = 0; i < 120; i++) assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u1", "1.1.1." + (i % 250)).block());
        ApiRateLimitService.Decision d = s.check(ApiRateLimitService.Kind.GENERAL, "u1", "2.2.2.2");
        assertTrue(d.block());
        assertTrue(d.retryAfterSeconds() >= 1 && d.retryAfterSeconds() <= 60);
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u2", "2.2.2.2").block());
        clock.addAndGet(61_000);
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "u1", "2.2.2.2").block());
    }

    @Test
    void perIpLimitAndRefreshAreSeparate() {
        ApiRateLimitService s = service("enforce");
        for (int i = 0; i < 300; i++) s.check(ApiRateLimitService.Kind.GENERAL, "user" + i, "4.4.4.4");
        assertTrue(s.check(ApiRateLimitService.Kind.GENERAL, "fresh", "4.4.4.4").block());
        assertFalse(s.check(ApiRateLimitService.Kind.REFRESH, null, "4.4.4.4").block());
        for (int i = 0; i < 120; i++) s.check(ApiRateLimitService.Kind.REFRESH, null, "5.5.5.5");
        assertTrue(s.check(ApiRateLimitService.Kind.REFRESH, null, "5.5.5.5").block());
        assertFalse(s.check(ApiRateLimitService.Kind.GENERAL, "x", "5.5.5.5").block());
    }

    @Test
    void pingNeverCountedAndLogModeNeverBlocks() {
        ApiRateLimitService e = service("enforce");
        for (int i = 0; i < 1000; i++) assertFalse(e.check(ApiRateLimitService.Kind.SKIP, "u", "1.1.1.1").block());
        ApiRateLimitService l = service("log");
        for (int i = 0; i < 400; i++) assertFalse(l.check(ApiRateLimitService.Kind.GENERAL, "u", "1.1.1.1").block());
        ApiRateLimitService off = service("off");
        for (int i = 0; i < 400; i++) assertFalse(off.check(ApiRateLimitService.Kind.GENERAL, "u", "1.1.1.1").block());
    }
}
