package com.smsweb.sms.services.security;

import com.smsweb.sms.services.mobile.LoginAttemptService;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SecurityActivityTest {

    private static final long HOUR = 60 * 60_000L;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    private ThrottleSettings settings(String mode) {
        Map<String, String> m = new HashMap<>();
        m.put("app.security.throttle.mode", mode);
        return new ThrottleSettings(m::get);
    }

    @Test
    void keepsNewestFirstDropsAfterADayAndIsBounded() {
        SecurityActivityLog log = new SecurityActivityLog(clock::get);
        log.accountLock("ra***", "raj", 1, 15, true);
        clock.addAndGet(1000);
        log.parentLock("98******10", 1, 15);
        List<SecurityActivityLog.Event> r = log.recent();
        assertEquals(2, r.size());
        assertEquals(SecurityActivityLog.Type.PARENT_LOCK, r.get(0).type());
        clock.addAndGet(25 * HOUR);
        assertTrue(log.recent().isEmpty());
        for (int i = 0; i < SecurityActivityLog.MAX_EVENTS + 50; i++) {
            log.ipBlock("1.1.1." + (i % 200), 1, 15, 3, true);
        }
        assertEquals(SecurityActivityLog.MAX_EVENTS, log.recent().size());
    }

    @Test
    void webLockIsListedEvenInLogModeAndMarkedNotEnforced() {
        SecurityActivityLog log = new SecurityActivityLog(clock::get);
        LoginThrottleService t = new LoginThrottleService(settings("log"), clock::get);
        t.setActivityLog(log);
        for (int i = 0; i < 5; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "Raj", "p" + i, "1.1.1.1");
        List<SecurityActivityLog.Event> r = log.recent();
        assertEquals(1, r.size());
        assertEquals(SecurityActivityLog.Type.ACCOUNT_LOCK, r.get(0).type());
        assertEquals("raj", r.get(0).staffKey());
        assertFalse(r.get(0).who().contains("raj"));   // masked
        assertFalse(r.get(0).enforced());
    }

    @Test
    void ipSprayBlockAndChangePasswordLockAreListed() {
        SecurityActivityLog log = new SecurityActivityLog(clock::get);
        LoginThrottleService t = new LoginThrottleService(settings("enforce"), clock::get);
        t.setActivityLog(log);
        for (int i = 0; i < 20; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "user" + i, "x", "5.5.5.5");
        assertTrue(log.recent().stream().anyMatch(e -> e.type() == SecurityActivityLog.Type.IP_BLOCK && e.accounts() >= 20));
        for (int i = 0; i < 5; i++) t.recordFailure(LoginThrottleService.Channel.CHANGE_PASSWORD, "9876543210", "q" + i, "7.7.7.7");
        assertTrue(log.recent().stream().anyMatch(e -> e.type() == SecurityActivityLog.Type.PARENT_LOCK));
    }

    @Test
    void parentMobileLockAndApiHeldAreListed() {
        SecurityActivityLog log = new SecurityActivityLog(clock::get);
        LoginAttemptService a = new LoginAttemptService();
        a.setActivityLog(log);
        for (int i = 0; i < 5; i++) a.recordFailure("9812345610");
        assertEquals(SecurityActivityLog.Type.PARENT_LOCK, log.recent().get(0).type());
        assertFalse(log.recent().get(0).who().contains("9812345610"));

        ApiRateLimitService api = new ApiRateLimitService(settings("enforce"), clock::get);
        api.setActivityLog(log);
        for (int minute = 0; minute < 5; minute++) {
            for (int i = 0; i < 125; i++) api.check(ApiRateLimitService.Kind.GENERAL, "u1", "9.9.9.9");
            clock.addAndGet(60_001L);
        }
        assertTrue(log.recent().stream().anyMatch(e -> e.type() == SecurityActivityLog.Type.API_HELD));
    }

    @Test
    void withoutAnActivityLogNothingChanges() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce"), clock::get);
        for (int i = 0; i < 5; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "p" + i, "1.1.1.1");
        assertTrue(t.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "1.1.1.1"));
        LoginAttemptService a = new LoginAttemptService();
        for (int i = 0; i < 5; i++) a.recordFailure("9800000000");
        assertTrue(a.isLocked("9800000000"));
    }

    @Test
    void viewShowsOnlyThisBranchesStaffButEveryonesAddressAndParentEvents() {
        List<SecurityActivityLog.Event> all = new ArrayList<>();
        long now = clock.get();
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.ACCOUNT_LOCK, "ra***", "raj", 1, 15, 0, true));
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.ACCOUNT_LOCK, "ot***", "other", 2, 30, 0, true));
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.ACCOUNT_LOCK, "gh***", "ghost", 1, 15, 0, true));
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.IP_BLOCK, "5.5.5.5", null, 1, 30, 20, true));
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.PARENT_LOCK, "98******10", null, 1, 15, 0, true));
        all.add(new SecurityActivityLog.Event(now, SecurityActivityLog.Type.API_HELD, "u1***", null, 0, 10, 0, false));
        Map<String, Long> branch = Map.of("raj", 1L, "other", 2L); // "ghost" is unknown
        Map<String, Object> v = SecurityActivityService.build(all, 1L, branch::get, "log", ZoneId.of("UTC"));
        assertEquals(3, v.get("lockedSignIns"));   // raj, ghost (unknown account), parent; not the other branch's staff
        assertEquals(1, v.get("addressBlocks"));
        assertEquals(1, v.get("apiHeld"));
        assertEquals("log", v.get("mode"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) v.get("events");
        assertEquals(5, rows.size());
        assertFalse(rows.toString().contains("other"));
        assertFalse(rows.toString().contains("raj"));   // only masked names are sent
    }

    @Test
    void listIsLimitedAndTextMentionsLogMode() {
        List<SecurityActivityLog.Event> all = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            all.add(new SecurityActivityLog.Event(clock.get(), SecurityActivityLog.Type.IP_BLOCK, "1.1.1.1", null, 1, 15, 1, false));
        }
        Map<String, Object> v = SecurityActivityService.build(all, 1L, k -> null, "log", ZoneId.of("UTC"));
        assertEquals(50, v.get("addressBlocks"));
        assertEquals(SecurityActivityService.LIST_SIZE, ((List<?>) v.get("events")).size());
        assertTrue(SecurityActivityService.text(all.get(0)).contains("log mode"));
    }
}
