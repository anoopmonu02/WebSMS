package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleServiceTest {

    private static final long MIN = 60_000L;
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private LoginThrottleService service(String mode) {
        Map<String, String> m = new HashMap<>();
        m.put("app.security.throttle.mode", mode);
        return new LoginThrottleService(new ThrottleSettings(m::get), clock::get);
    }

    private void fail(LoginThrottleService s, LoginThrottleService.Channel ch, String user, int n, String ip) {
        for (int i = 0; i < n; i++) s.recordFailure(ch, user, "pw" + i + user, ip);
    }

    @Test
    void logModeNeverBlocks() {
        LoginThrottleService s = service("log");
        fail(s, LoginThrottleService.Channel.WEB, "Raj", 6, "1.1.1.1");
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "1.1.1.1"));
    }

    @Test
    void enforceBlocksAccountEvenWhenIpChanges() {
        LoginThrottleService s = service("enforce");
        for (int i = 0; i < 5; i++) s.recordFailure(LoginThrottleService.Channel.WEB, "raj", "pw" + i, "9.9.9." + i);
        assertTrue(s.shouldBlock(LoginThrottleService.Channel.WEB, "RAJ", "8.8.8.8"));
        clock.addAndGet(16 * MIN);
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "8.8.8.8"));
    }

    @Test
    void successClearsAccount() {
        LoginThrottleService s = service("enforce");
        fail(s, LoginThrottleService.Channel.WEB, "raj", 4, "1.1.1.1");
        s.recordSuccess(LoginThrottleService.Channel.WEB, "raj");
        fail(s, LoginThrottleService.Channel.WEB, "raj", 4, "1.1.1.1");
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "1.1.1.1"));
    }

    @Test
    void sprayingManyAccountsFromOneIpBlocksTheIp() {
        LoginThrottleService s = service("enforce");
        for (int i = 0; i < 20; i++) s.recordFailure(LoginThrottleService.Channel.WEB, "user" + i, "pw", "5.5.5.5");
        assertTrue(s.shouldBlock(LoginThrottleService.Channel.WEB, "someone-else", "5.5.5.5"));
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "someone-else", "6.6.6.6"));
        clock.addAndGet(31 * MIN);
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "someone-else", "5.5.5.5"));
    }

    @Test
    void mobileHasIpRuleOnlyAndChangePasswordHasAccountRuleOnly() {
        LoginThrottleService s = service("enforce");
        fail(s, LoginThrottleService.Channel.MOBILE, "9876543210", 10, "7.7.7.7");
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.MOBILE, "9876543210", "7.7.7.8"));
        fail(s, LoginThrottleService.Channel.CHANGE_PASSWORD, "fa1", 5, "7.7.7.7");
        assertTrue(s.shouldBlock(LoginThrottleService.Channel.CHANGE_PASSWORD, "fa1", "other"));
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.CHANGE_PASSWORD, "fa2", "7.7.7.7"));
    }

    @Test
    void offModeDoesNothingAndNullsAreSafe() {
        LoginThrottleService s = service("off");
        fail(s, LoginThrottleService.Channel.WEB, "raj", 10, "1.1.1.1");
        assertFalse(s.shouldBlock(LoginThrottleService.Channel.WEB, "raj", "1.1.1.1"));
        LoginThrottleService e = service("enforce");
        e.recordFailure(LoginThrottleService.Channel.WEB, null, null, null);
        assertFalse(e.shouldBlock(LoginThrottleService.Channel.WEB, null, null));
    }

    @Test
    void forgotPasswordPerIp() {
        LoginThrottleService s = service("enforce");
        for (int i = 0; i < 20; i++) {
            assertFalse(s.shouldBlockForgotPassword("3.3.3.3"));
            s.recordForgotPassword("3.3.3.3");
        }
        assertTrue(s.shouldBlockForgotPassword("3.3.3.3"));
        assertFalse(s.shouldBlockForgotPassword("3.3.3.4"));
    }
}
