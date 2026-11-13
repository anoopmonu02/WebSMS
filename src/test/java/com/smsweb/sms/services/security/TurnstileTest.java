package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TurnstileTest {

    private static final long MIN = 60_000L;
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private ThrottleSettings settings(String mode, boolean enabled, String site, String secret) {
        Map<String, String> m = new HashMap<>();
        m.put("app.security.throttle.mode", mode);
        m.put("app.security.turnstile.enabled", String.valueOf(enabled));
        m.put("app.security.turnstile.site-key", site);
        m.put("app.security.turnstile.secret-key", secret);
        return new ThrottleSettings(m::get);
    }

    @Test
    void activeOnlyWithSwitchAndBothKeys() {
        assertTrue(settings("enforce", true, "s", "k").turnstileActive());
        assertFalse(settings("enforce", false, "s", "k").turnstileActive());
        assertFalse(settings("enforce", true, "", "k").turnstileActive());
        assertFalse(settings("enforce", true, "s", "").turnstileActive());
    }

    @Test
    void verifyPassedFailedAndUnavailable() {
        ThrottleSettings st = settings("enforce", true, "site", "secret");
        AtomicReference<String> sent = new AtomicReference<>();
        TurnstileService ok = new TurnstileService(st, (url, form) -> {
            sent.set(url + "?" + form);
            return "{\"success\":true}";
        });
        assertEquals(TurnstileService.Result.PASSED, ok.verify("tok", "203.0.113.9"));
        assertTrue(sent.get().contains("secret=secret"));
        assertTrue(sent.get().contains("response=tok"));
        assertTrue(sent.get().contains("remoteip=203.0.113.9"));
        assertTrue(sent.get().startsWith("https://challenges.cloudflare.com/turnstile/v0/siteverify"));

        TurnstileService bad = new TurnstileService(st, (u, f) -> "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
        assertEquals(TurnstileService.Result.FAILED, bad.verify("tok", null));

        TurnstileService down = new TurnstileService(st, (u, f) -> { throw new IllegalStateException("timeout"); });
        assertEquals(TurnstileService.Result.UNAVAILABLE, down.verify("tok", "1.1.1.1"));

        TurnstileService odd = new TurnstileService(st, (u, f) -> "<html>oops</html>");
        assertEquals(TurnstileService.Result.UNAVAILABLE, odd.verify("tok", "1.1.1.1"));
    }

    @Test
    void missingOrHugeTokenFailsWithoutCallingCloudflare() {
        ThrottleSettings st = settings("enforce", true, "site", "secret");
        TurnstileService s = new TurnstileService(st, (u, f) -> { throw new AssertionError("must not be called"); });
        assertEquals(TurnstileService.Result.FAILED, s.verify(null, "1.1.1.1"));
        assertEquals(TurnstileService.Result.FAILED, s.verify("   ", "1.1.1.1"));
        assertEquals(TurnstileService.Result.FAILED, s.verify("x".repeat(5000), "1.1.1.1"));
    }

    @Test
    void captchaRequiredAfterThreeWrongAttemptsAndClearedBySuccess() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce", true, "site", "secret"), clock::get);
        assertTrue(t.captchaEnabled());
        t.recordFailure(LoginThrottleService.Channel.WEB, "Raj", "a", "1.1.1.1");
        t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "b", "2.2.2.2");
        assertFalse(t.captchaRequired("raj", "3.3.3.3"));
        t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "c", "4.4.4.4");
        assertTrue(t.captchaRequired("RAJ", "9.9.9.9"));      // the account, from any address
        assertFalse(t.captchaRequired("other", "9.9.9.9"));
        t.recordSuccess(LoginThrottleService.Channel.WEB, "raj");
        assertFalse(t.captchaRequired("raj", "9.9.9.9"));
    }

    @Test
    void captchaRequiredForAnAddressOnlyAfterManyFailuresAndExpires() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce", true, "site", "secret"), clock::get);
        for (int i = 0; i < 3; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "user" + i, "x", "5.5.5.5");
        assertFalse(t.captchaRequiredForIp("5.5.5.5"));              // 3 is not enough for a whole address
        assertFalse(t.captchaRequired("someoneElse", "5.5.5.5"));    // others on the address are not asked
        for (int i = 3; i < 10; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "user" + i, "x", "5.5.5.5");
        assertTrue(t.captchaRequiredForIp("5.5.5.5"));
        assertTrue(t.captchaRequired("someoneElse", "5.5.5.5"));
        assertFalse(t.captchaRequiredForIp("6.6.6.6"));
        clock.addAndGet(16 * MIN);
        assertFalse(t.captchaRequiredForIp("5.5.5.5"));
    }

    @Test
    void oneAccountFailingThreeTimesOnlyAsksThatAccount() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce", true, "site", "secret"), clock::get);
        for (int i = 0; i < 3; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "p" + i, "5.5.5.5");
        assertTrue(t.captchaRequired("raj", "5.5.5.5"));
        assertFalse(t.captchaRequired("other", "5.5.5.5"));
        assertFalse(t.captchaRequiredForIp("5.5.5.5"));
    }

    @Test
    void neverRequiredWhenOffInLogModeOrNotConfigured() {
        for (LoginThrottleService t : new LoginThrottleService[]{
                new LoginThrottleService(settings("log", true, "site", "secret"), clock::get),
                new LoginThrottleService(settings("off", true, "site", "secret"), clock::get),
                new LoginThrottleService(settings("enforce", false, "site", "secret"), clock::get),
                new LoginThrottleService(settings("enforce", true, "", ""), clock::get)}) {
            for (int i = 0; i < 6; i++) t.recordFailure(LoginThrottleService.Channel.WEB, "raj", "p" + i, "1.1.1.1");
            assertFalse(t.captchaRequired("raj", "1.1.1.1"));
            assertFalse(t.captchaEnabled());
        }
    }

    @Test
    void mobileAndChangePasswordDoNotUseTheCaptcha() {
        LoginThrottleService t = new LoginThrottleService(settings("enforce", true, "site", "secret"), clock::get);
        for (int i = 0; i < 6; i++) t.recordFailure(LoginThrottleService.Channel.MOBILE, "9876543210", "p" + i, "7.7.7.7");
        assertFalse(t.captchaRequired("9876543210", "7.7.7.7"));
    }
}
