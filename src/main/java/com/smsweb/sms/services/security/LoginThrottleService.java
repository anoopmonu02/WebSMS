package com.smsweb.sms.services.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Wrong-password throttling for the web sign-in, the mobile sign-in and the mobile change-password check.
 *
 * <p>Two kinds of rules, both temporary and in memory:</p>
 * <ul>
 *   <li><b>Per account</b> (web sign-in and change-password): the lock ladder in {@link LockoutTracker}. It ignores
 *       the IP address, so a bot that changes IP on every try is still stopped.</li>
 *   <li><b>Per IP</b> (web and mobile sign-in): many wrong attempts from one address, or wrong attempts on many
 *       different accounts from one address (password spraying), lock that address. The limits are generous because
 *       schools and mobile carriers share addresses.</li>
 * </ul>
 *
 * <p>The mobile sign-in's per-number rule (5 wrong attempts, 15 minutes) stays in
 * {@code LoginAttemptService} exactly as before.</p>
 *
 * <p>Mode {@code log} (default) keeps all the counts and writes "SECURITY ... WOULD BLOCK" lines but never blocks;
 * {@code enforce} blocks; {@code off} does nothing. Every method is safe to call at any time and does not throw.</p>
 */
@Service
public class LoginThrottleService {

    public enum Channel {
        WEB("web-login", true, true),
        MOBILE("mobile-login", false, true),
        CHANGE_PASSWORD("change-password", true, false);

        private final String label;
        private final boolean accountRule;
        private final boolean ipRule;

        Channel(String label, boolean accountRule, boolean ipRule) {
            this.label = label;
            this.accountRule = accountRule;
            this.ipRule = ipRule;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);
    private static final int MAX_KEY_LEN = 100;

    private final ThrottleSettings settings;
    private final LongSupplier clock;
    private final EnumMap<Channel, LockoutTracker> accounts = new EnumMap<>(Channel.class);
    private final EnumMap<Channel, LockoutTracker> ips = new EnumMap<>(Channel.class);
    private final EnumMap<Channel, AttemptCounter> distinct = new EnumMap<>(Channel.class);
    private final AttemptCounter forgotPasswordIp;
    private final AttemptCounter logGate;
    private final AttemptCounter captchaIp;
    private final AttemptCounter captchaUser;
    private volatile SecurityEventSink sink;
    private volatile SecurityActivityLog activity;

    @Autowired
    public LoginThrottleService(ThrottleSettings settings) {
        this(settings, System::currentTimeMillis);
    }

    LoginThrottleService(ThrottleSettings settings, LongSupplier clock) {
        this.settings = settings;
        this.clock = clock;
        for (Channel c : Channel.values()) {
            if (c.accountRule) {
                accounts.put(c, new LockoutTracker(settings.accountLadder()));
            }
            if (c.ipRule) {
                int max = c == Channel.WEB ? settings.webIpMax() : settings.mobileIpMax();
                ips.put(c, new LockoutTracker(settings.ipLadder(max)));
                distinct.put(c, new AttemptCounter(settings.ipWindowMillis(), settings.maxEntries()));
            }
        }
        this.forgotPasswordIp = new AttemptCounter(settings.ipWindowMillis(), settings.maxEntries());
        this.logGate = new AttemptCounter(60_000L, 5_000);
        this.captchaIp = new AttemptCounter(settings.ipWindowMillis(), settings.maxEntries());
        this.captchaUser = new AttemptCounter(settings.ipWindowMillis(), settings.maxEntries());
    }

    /** Registers the receiver of serious events (alert e-mails). Optional; without it nothing is sent. */
    public void setSink(SecurityEventSink sink) {
        this.sink = sink;
    }

    /** Optional: the short list of recent events shown to admins. Without it nothing is listed. */
    @Autowired(required = false)
    public void setActivityLog(SecurityActivityLog activity) {
        this.activity = activity;
    }

    private void noteAccountLock(Channel channel, String key, LockoutTracker.Outcome o) {
        SecurityActivityLog a = activity;
        if (a == null) {
            return;
        }
        try {
            if (channel == Channel.WEB) {
                a.accountLock(display(channel, key), key, o.level(), o.lockMinutes(), settings.isEnforce());
            } else {
                a.parentLock(display(channel, key), o.level(), o.lockMinutes());
            }
        } catch (RuntimeException e) {
            log.error("Could not list the security event", e);
        }
    }

    private void noteIpBlock(String ip, LockoutTracker.Outcome o, int accounts) {
        SecurityActivityLog a = activity;
        if (a == null) {
            return;
        }
        try {
            a.ipBlock(Masking.plain(ip), o.level(), o.lockMinutes(), accounts, settings.isEnforce());
        } catch (RuntimeException e) {
            log.error("Could not list the security event", e);
        }
    }

    private void publishAccountLock(Channel channel, String key, LockoutTracker.Outcome o, String ip) {
        SecurityEventSink s = sink;
        if (s == null || !settings.isEnforce()) {
            return; // in log mode nobody was really locked, so nobody is told
        }
        try {
            s.accountLocked(channel, key, o.level(), o.lockMinutes(), ip);
        } catch (RuntimeException e) {
            log.error("Security event receiver failed", e);
        }
    }

    private void publishIpBlock(Channel channel, String ip, LockoutTracker.Outcome o, int accounts, boolean spray) {
        SecurityEventSink s = sink;
        if (s == null || !settings.isEnforce()) {
            return;
        }
        try {
            s.ipBlocked(channel, ip, o.level(), o.lockMinutes(), accounts, spray);
        } catch (RuntimeException e) {
            log.error("Security event receiver failed", e);
        }
    }

    // ── sign-in and change-password ─────────────────────────────────────────────────────────────

    /**
     * True when this attempt should be refused now. In {@code log} mode this is always false, but a "WOULD BLOCK"
     * line is written (at most once a minute per account or address).
     *
     * @param key account name or number (may be null), {@code ip} the visitor address (may be null)
     */
    public boolean shouldBlock(Channel channel, String key, String ip) {
        try {
            if (settings.isOff()) {
                return false;
            }
            long now = clock.getAsLong();
            String reason = null;
            long remaining = 0;
            String k = norm(key);
            if (channel.accountRule && k != null) {
                LockoutTracker t = accounts.get(channel);
                if (t.isLocked(k, now)) {
                    reason = "ACCOUNT";
                    remaining = t.remainingMillis(k, now);
                }
            }
            if (reason == null && channel.ipRule && settings.ipRulesEnabled() && ip != null) {
                LockoutTracker t = ips.get(channel);
                if (t.isLocked(ip, now)) {
                    reason = "IP";
                    remaining = t.remainingMillis(ip, now);
                }
            }
            if (reason == null) {
                return false;
            }
            if (settings.isEnforce()) {
                return true;
            }
            String gateKey = channel.label + "|" + reason + "|" + ("ACCOUNT".equals(reason) ? k : ip);
            if (logGate.hit(gateKey, null, now).count() == 1) {
                log.warn("SECURITY {} WOULD BLOCK ({}) user={} ip={} remainingSeconds={} mode={}", channel.label, reason,
                        display(channel, k), Masking.plain(ip), remaining / 1000, settings.mode());
            }
            return false;
        } catch (RuntimeException e) {
            log.error("Login throttle check failed; allowing the attempt", e);
            return false;
        }
    }

    /**
     * Counts one wrong attempt.
     *
     * @param secret the wrong password that was typed (only a keyed fingerprint is used, so the same wrong password
     *               typed again does not count); may be null
     */
    public void recordFailure(Channel channel, String key, String secret, String ip) {
        try {
            if (settings.isOff()) {
                return;
            }
            long now = clock.getAsLong();
            String k = norm(key);
            if (channel == Channel.WEB && settings.turnstileActive()) {
                if (ip != null) {
                    captchaIp.hit(ip, null, now);
                }
                if (k != null) {
                    captchaUser.hit(k, null, now);
                }
            }
            if (channel.accountRule && k != null) {
                LockoutTracker.Outcome o = accounts.get(channel).recordFailure(k, Fingerprints.of(secret), now);
                if (o.newlyLocked()) {
                    log.warn("SECURITY {} account lock level={} minutes={} user={} ip={} enforced={}", channel.label, o.level(),
                            o.lockMinutes(), display(channel, k), Masking.plain(ip), settings.isEnforce());
                    publishAccountLock(channel, k, o, ip);
                    noteAccountLock(channel, k, o);
                }
            }
            if (channel.ipRule && settings.ipRulesEnabled() && ip != null) {
                AttemptCounter.Hit hit = distinct.get(channel).hit(ip, Fingerprints.of(k == null ? "" : k), now);
                int distinctMax = channel == Channel.WEB ? settings.webIpDistinct() : settings.mobileIpDistinct();
                LockoutTracker t = ips.get(channel);
                boolean spray = hit.distinct() >= distinctMax;
                LockoutTracker.Outcome o = spray
                        ? t.lockNow(ip, settings.spraySpecialLockMinutes(), now)
                        : t.recordFailure(ip, null, now);
                if (o.newlyLocked()) {
                    log.warn("SECURITY {} ip lock level={} minutes={} ip={} differentAccounts={} enforced={}", channel.label,
                            o.level(), o.lockMinutes(), Masking.plain(ip), hit.distinct(), settings.isEnforce());
                    publishIpBlock(channel, ip, o, hit.distinct(), spray);
                    noteIpBlock(ip, o, hit.distinct());
                }
            }
        } catch (RuntimeException e) {
            log.error("Login throttle could not record a failure", e);
        }
    }

    /** A correct sign-in: forgets the account's wrong attempts and lock level (the IP counters are left alone). */
    public void recordSuccess(Channel channel, String key) {
        clear(channel, key);
    }

    /** Forgets the account's lock and counters (correct sign-in, or an admin reset the password). */
    public void clear(Channel channel, String key) {
        try {
            String k = norm(key);
            if (channel.accountRule && k != null) {
                accounts.get(channel).clear(k);
            }
            if (channel == Channel.WEB && k != null) {
                captchaUser.clear(k);
            }
        } catch (RuntimeException e) {
            log.error("Login throttle could not clear an account", e);
        }
    }

    // ── CAPTCHA (Cloudflare Turnstile) gate for the web sign-in ─────────────────────────────────

    /** True when the CAPTCHA is switched on and the throttle is enforcing (never in log or off mode). */
    public boolean captchaEnabled() {
        return settings.turnstileActive() && settings.isEnforce();
    }

    /** The CAPTCHA must be solved for this sign-in: this account or this address has several recent wrong attempts. */
    public boolean captchaRequired(String username, String ip) {
        try {
            if (!captchaEnabled()) {
                return false;
            }
            long now = clock.getAsLong();
            int after = settings.turnstileAfterFailures();
            int afterIp = settings.turnstileAfterFailuresPerIp();
            String k = norm(username);
            return (k != null && captchaUser.count(k, now) >= after) || (ip != null && captchaIp.count(ip, now) >= afterIp);
        } catch (RuntimeException e) {
            log.error("CAPTCHA check failed; not requiring it", e);
            return false;
        }
    }

    /** The login page shows the widget up front when this address already has several recent wrong attempts. */
    public boolean captchaRequiredForIp(String ip) {
        return captchaRequired(null, ip);
    }

    // ── forgot password, per IP ─────────────────────────────────────────────────────────────────

    /** True when this address has sent too many "forgot password" requests (log mode: always false, but logged). */
    public boolean shouldBlockForgotPassword(String ip) {
        try {
            if (settings.isOff() || !settings.ipRulesEnabled() || ip == null) {
                return false;
            }
            long now = clock.getAsLong();
            if (forgotPasswordIp.count(ip, now) < settings.forgotPasswordIpMax()) {
                return false;
            }
            if (settings.isEnforce()) {
                return true;
            }
            if (logGate.hit("forgot|" + ip, null, now).count() == 1) {
                log.warn("SECURITY forgot-password WOULD BLOCK (IP) ip={} mode={}", Masking.plain(ip), settings.mode());
            }
            return false;
        } catch (RuntimeException e) {
            log.error("Forgot-password throttle check failed; allowing the request", e);
            return false;
        }
    }

    public void recordForgotPassword(String ip) {
        try {
            if (!settings.isOff() && settings.ipRulesEnabled() && ip != null) {
                forgotPasswordIp.hit(ip, null, clock.getAsLong());
            }
        } catch (RuntimeException e) {
            log.error("Forgot-password throttle could not record a request", e);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static String norm(String key) {
        if (key == null) {
            return null;
        }
        String t = key.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_KEY_LEN ? t.substring(0, MAX_KEY_LEN) : t;
    }

    private static String display(Channel channel, String key) {
        return channel == Channel.MOBILE ? Masking.mobile(key) : Masking.username(key);
    }
}
