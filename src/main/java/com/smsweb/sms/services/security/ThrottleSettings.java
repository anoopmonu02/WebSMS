package com.smsweb.sms.services.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * All the numbers behind login and API throttling, read from application.properties / environment variables with
 * safe defaults. A missing, empty or invalid value falls back to the default instead of stopping the application.
 *
 * <p>Mode ({@code app.security.throttle.mode}): {@code off} = nothing is counted, {@code log} = counted and written
 * to the log as "would block", nobody is blocked (default), {@code enforce} = blocks.</p>
 */
@Component
public class ThrottleSettings {

    public static final String MODE_OFF = "off";
    public static final String MODE_LOG = "log";
    public static final String MODE_ENFORCE = "enforce";

    public static final String IP_MODE_REMOTE_ADDR = "remote-addr";
    public static final String IP_MODE_CF = "cf-connecting-ip";

    private static final String P = "app.security.throttle.";
    private static final String DEFAULT_TRUSTED = "127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128";

    private final String mode;
    private final boolean ipRules;
    private final int accountLockThreshold;
    private final int[] accountLockMinutes;
    private final int accountTriesAfterUnlock;
    private final int accountIdleResetMinutes;
    private final int accountLevelMemoryHours;
    private final boolean ignoreRepeatedPassword;
    private final int webIpMax;
    private final int webIpDistinct;
    private final int mobileIpMax;
    private final int mobileIpDistinct;
    private final int[] ipBlockMinutes;
    private final int ipTriesAfterUnlock;
    private final int ipWindowMinutes;
    private final int spraySpecialLockMinutes;
    private final int forgotPasswordIpMax;
    private final int apiPerUserPerMinute;
    private final int apiPerIpPerMinute;
    private final int apiRefreshPerIpPerMinute;
    private final int apiSustainedMinutes;
    private final int apiSustainedBlockMinutes;
    private final int maxEntries;
    private final boolean turnstileEnabled;
    private final String turnstileSiteKey;
    private final String turnstileSecretKey;
    private final int turnstileAfterFailures;
    private final int turnstileAfterFailuresPerIp;
    private final String turnstileVerifyUrl;
    private final String clientIpMode;
    private final List<String> trustedProxies;

    @Autowired
    public ThrottleSettings(Environment env) {
        this(env::getProperty);
    }

    public ThrottleSettings(Function<String, String> lookup) {
        String m = text(lookup, P + "mode", MODE_LOG).toLowerCase(Locale.ROOT);
        this.mode = m.equals(MODE_OFF) || m.equals(MODE_ENFORCE) ? m : MODE_LOG;
        this.ipRules = bool(lookup, P + "ip-rules", true);
        this.accountLockThreshold = num(lookup, P + "account.lock-threshold", 5, 1, 1000);
        this.accountLockMinutes = list(lookup, P + "account.lock-minutes", new int[]{15, 30, 60, 60});
        this.accountTriesAfterUnlock = num(lookup, P + "account.tries-after-unlock", 2, 1, 1000);
        this.accountIdleResetMinutes = num(lookup, P + "account.idle-reset-minutes", 30, 1, 10_080);
        this.accountLevelMemoryHours = num(lookup, P + "account.level-memory-hours", 24, 1, 720);
        this.ignoreRepeatedPassword = bool(lookup, P + "account.ignore-repeated-password", true);
        this.webIpMax = num(lookup, P + "web.ip.max", 30, 1, 100_000);
        this.webIpDistinct = num(lookup, P + "web.ip.distinct-accounts", 20, 2, 100_000);
        this.mobileIpMax = num(lookup, P + "mobile.ip.max", 40, 1, 100_000);
        this.mobileIpDistinct = num(lookup, P + "mobile.ip.distinct-accounts", 20, 2, 100_000);
        this.ipBlockMinutes = list(lookup, P + "ip.block-minutes", new int[]{15, 30, 60, 60});
        this.ipTriesAfterUnlock = num(lookup, P + "ip.tries-after-unlock", 10, 1, 100_000);
        this.ipWindowMinutes = num(lookup, P + "ip.window-minutes", 15, 1, 1440);
        this.spraySpecialLockMinutes = num(lookup, P + "ip.spray-lock-minutes", 30, 1, 1440);
        this.forgotPasswordIpMax = num(lookup, P + "forgot-password.ip-max", 20, 1, 100_000);
        this.apiPerUserPerMinute = num(lookup, P + "api.per-user-per-minute", 120, 1, 1_000_000);
        this.apiPerIpPerMinute = num(lookup, P + "api.per-ip-per-minute", 300, 1, 1_000_000);
        this.apiRefreshPerIpPerMinute = num(lookup, P + "api.refresh-per-ip-per-minute", 120, 1, 1_000_000);
        this.apiSustainedMinutes = num(lookup, P + "api.sustained-minutes", 5, 2, 120);
        this.apiSustainedBlockMinutes = num(lookup, P + "api.sustained-block-minutes", 10, 1, 1440);
        this.maxEntries = num(lookup, P + "max-entries", 50_000, 100, 5_000_000);
        this.turnstileEnabled = bool(lookup, "app.security.turnstile.enabled", false);
        this.turnstileSiteKey = text(lookup, "app.security.turnstile.site-key", "");
        this.turnstileSecretKey = text(lookup, "app.security.turnstile.secret-key", "");
        this.turnstileAfterFailures = num(lookup, "app.security.turnstile.after-failures", 3, 1, 100);
        this.turnstileAfterFailuresPerIp = num(lookup, "app.security.turnstile.after-failures-per-ip", 10, 1, 1000);
        this.turnstileVerifyUrl = text(lookup, "app.security.turnstile.verify-url",
                "https://challenges.cloudflare.com/turnstile/v0/siteverify");
        String ipMode = text(lookup, "app.security.client-ip-mode", IP_MODE_REMOTE_ADDR).toLowerCase(Locale.ROOT);
        this.clientIpMode = ipMode.equals(IP_MODE_CF) ? IP_MODE_CF : IP_MODE_REMOTE_ADDR;
        List<String> trusted = new ArrayList<>();
        for (String part : text(lookup, "app.security.trusted-proxies", DEFAULT_TRUSTED).split(",")) {
            if (!part.isBlank()) {
                trusted.add(part.trim());
            }
        }
        this.trustedProxies = trusted;
    }

    public String mode() {
        return mode;
    }

    public boolean isOff() {
        return MODE_OFF.equals(mode);
    }

    public boolean isEnforce() {
        return MODE_ENFORCE.equals(mode);
    }

    public boolean ipRulesEnabled() {
        return ipRules;
    }

    /** The account ladder (staff web sign-in and mobile change-password). */
    public LockoutTracker.Config accountLadder() {
        return new LockoutTracker.Config(accountLockThreshold, accountLockMinutes, accountTriesAfterUnlock,
                accountIdleResetMinutes * 60_000L, accountLevelMemoryHours * 3_600_000L, ignoreRepeatedPassword, maxEntries);
    }

    /** The ladder for one IP address; {@code threshold} is the wrong attempts allowed from that address. */
    public LockoutTracker.Config ipLadder(int threshold) {
        return new LockoutTracker.Config(threshold, ipBlockMinutes, ipTriesAfterUnlock,
                ipWindowMinutes * 60_000L, 24 * 3_600_000L, false, maxEntries);
    }

    public int webIpMax() {
        return webIpMax;
    }

    public int webIpDistinct() {
        return webIpDistinct;
    }

    public int mobileIpMax() {
        return mobileIpMax;
    }

    public int mobileIpDistinct() {
        return mobileIpDistinct;
    }

    public long ipWindowMillis() {
        return ipWindowMinutes * 60_000L;
    }

    public int spraySpecialLockMinutes() {
        return spraySpecialLockMinutes;
    }

    public int forgotPasswordIpMax() {
        return forgotPasswordIpMax;
    }

    public int apiPerUserPerMinute() {
        return apiPerUserPerMinute;
    }

    public int apiPerIpPerMinute() {
        return apiPerIpPerMinute;
    }

    public int apiRefreshPerIpPerMinute() {
        return apiRefreshPerIpPerMinute;
    }

    /** How many minutes in a row a caller may stay over the API limit before being blocked for a while. */
    public int apiSustainedMinutes() {
        return apiSustainedMinutes;
    }

    public int apiSustainedBlockMinutes() {
        return apiSustainedBlockMinutes;
    }

    public int maxEntries() {
        return maxEntries;
    }

    /** True only when the switch is on AND both keys are present; otherwise the CAPTCHA is simply not used. */
    public boolean turnstileActive() {
        return turnstileEnabled && !turnstileSiteKey.isBlank() && !turnstileSecretKey.isBlank();
    }

    /** Public key, safe to put in the page. */
    public String turnstileSiteKey() {
        return turnstileSiteKey;
    }

    public String turnstileSecretKey() {
        return turnstileSecretKey;
    }

    public int turnstileAfterFailures() {
        return turnstileAfterFailures;
    }

    /** Wrong attempts from one address (any accounts) before everyone on that address needs the CAPTCHA. */
    public int turnstileAfterFailuresPerIp() {
        return turnstileAfterFailuresPerIp;
    }

    public String turnstileVerifyUrl() {
        return turnstileVerifyUrl;
    }

    public String clientIpMode() {
        return clientIpMode;
    }

    public List<String> trustedProxies() {
        return trustedProxies;
    }

    // ── lenient parsing ─────────────────────────────────────────────────────────────────────────

    private static String text(Function<String, String> f, String key, String def) {
        String v = f.apply(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static boolean bool(Function<String, String> f, String key, boolean def) {
        String v = f.apply(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        String t = v.trim();
        if (t.equalsIgnoreCase("true")) {
            return true;
        }
        if (t.equalsIgnoreCase("false")) {
            return false;
        }
        return def;
    }

    private static int num(Function<String, String> f, String key, int def, int min, int max) {
        String v = f.apply(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n < min || n > max ? def : n;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int[] list(Function<String, String> f, String key, int[] def) {
        String v = f.apply(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        String[] parts = v.split(",");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                int n = Integer.parseInt(parts[i].trim());
                if (n < 1 || n > 10_080) {
                    return def;
                }
                out[i] = n;
            }
        } catch (NumberFormatException e) {
            return def;
        }
        return out;
    }
}
