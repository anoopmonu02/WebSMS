package com.smsweb.sms.services.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.LongSupplier;

/**
 * Request-rate limits for the mobile API ({@code /api/v1/**}): per signed-in user and per IP address, counted per
 * minute. Over the limit the caller gets HTTP 429 and "retry after" seconds; nothing is locked.
 *
 * <p>Special cases, because the mobile app logs the parent out when its token refresh fails:</p>
 * <ul>
 *   <li>{@code /api/v1/auth/ping} is never counted (the maintenance screen polls it).</li>
 *   <li>{@code /api/v1/auth/refresh} has its own, separate per-IP counter and is not part of the general budget.</li>
 * </ul>
 *
 * <p>Mode {@code log} (default) counts and writes "SECURITY api-rate WOULD BLOCK" lines (once per minute per user or
 * address) but never blocks; {@code enforce} blocks; {@code off} does nothing.</p>
 */
@Service
public class ApiRateLimitService {

    /** The answer for one request. */
    public record Decision(boolean block, long retryAfterSeconds) {
        static final Decision ALLOW = new Decision(false, 0);

        public static Decision allow() {
            return ALLOW;
        }
    }

    public enum Kind { GENERAL, REFRESH, SKIP }

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitService.class);
    private static final long WINDOW_MS = 60_000L;

    private final ThrottleSettings settings;
    private final LongSupplier clock;
    private final AttemptCounter perUser;
    private final AttemptCounter perIp;
    private final AttemptCounter refreshPerIp;
    private final ExpiringStore<Streak> streaks;
    private volatile SecurityActivityLog activity;

    /** How long a caller has been over the limit: consecutive over-limit minutes and any temporary block. */
    private record Streak(long lastWindowEnd, int minutes, long blockedUntil) {
    }

    @Autowired
    public ApiRateLimitService(ThrottleSettings settings) {
        this(settings, System::currentTimeMillis);
    }

    ApiRateLimitService(ThrottleSettings settings, LongSupplier clock) {
        this.settings = settings;
        this.clock = clock;
        this.perUser = new AttemptCounter(WINDOW_MS, settings.maxEntries());
        this.perIp = new AttemptCounter(WINDOW_MS, settings.maxEntries());
        this.refreshPerIp = new AttemptCounter(WINDOW_MS, settings.maxEntries());
        this.streaks = new ExpiringStore<>(settings.maxEntries(),
                s -> Math.max(s.lastWindowEnd(), s.blockedUntil()),
                (s, now) -> s.blockedUntil() <= now && now - s.lastWindowEnd() > 2 * WINDOW_MS);
    }

    /** Optional: the short list of recent events shown to admins. Without it nothing is listed. */
    @Autowired(required = false)
    public void setActivityLog(SecurityActivityLog activity) {
        this.activity = activity;
    }

    /** Records one more over-limit minute for the caller; true when this made it reach the "held off" state. */
    private boolean noteOver(String key, String id, long windowEnd, long now) {
        if (id == null) {
            return false;
        }
        boolean[] held = new boolean[1];
        long gapAllowed = WINDOW_MS + 5_000L; // fixed windows start at the first request, so allow a little slack
        streaks.compute(key, now, (k, cur) -> {
            if (cur != null && cur.blockedUntil() > now) {
                return cur;
            }
            int minutes = 1;
            if (cur != null && cur.lastWindowEnd() == windowEnd) {
                minutes = cur.minutes(); // the same minute again
            } else if (cur != null && windowEnd - cur.lastWindowEnd() <= gapAllowed && windowEnd > cur.lastWindowEnd()) {
                minutes = cur.minutes() + 1;
            }
            if (minutes >= settings.apiSustainedMinutes()) {
                held[0] = true;
                log.warn("SECURITY api-rate sustained over limit for {} minutes: {} (mode={}); held for {} minutes if enforced",
                        minutes, key.startsWith("user|") ? "user=" + Masking.username(id) : "ip=" + Masking.plain(id),
                        settings.mode(), settings.apiSustainedBlockMinutes());
                SecurityActivityLog a = activity;
                if (a != null) {
                    a.apiHeld(key.startsWith("user|") ? Masking.username(id) : Masking.plain(id),
                            settings.apiSustainedBlockMinutes(), settings.isEnforce());
                }
                return new Streak(windowEnd, 0, now + settings.apiSustainedBlockMinutes() * 60_000L);
            }
            return new Streak(windowEnd, minutes, 0);
        });
        return held[0];
    }

    /** Milliseconds left on a "held off" state for this caller, or 0. */
    private long heldMillis(String key, String id, long now) {
        if (id == null) {
            return 0;
        }
        Streak s = streaks.get(key);
        return s == null || s.blockedUntil() <= now ? 0 : s.blockedUntil() - now;
    }

    private Decision sustainedDecision(long heldMs) {
        if (!settings.isEnforce()) {
            return Decision.ALLOW;
        }
        return new Decision(true, Math.max(1, (heldMs + 999) / 1000));
    }

    /** Which budget a request path (without the context path) belongs to. */
    public static Kind kindOf(String pathWithoutContext) {
        String p = pathWithoutContext == null ? "" : pathWithoutContext;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if ("/api/v1/auth/ping".equals(p)) {
            return Kind.SKIP;
        }
        if ("/api/v1/auth/refresh".equals(p)) {
            return Kind.REFRESH;
        }
        return Kind.GENERAL;
    }

    /**
     * @param userKey the signed-in user (token subject), or null for a request without a valid token
     * @param ip      the visitor address, or null if unknown
     */
    public Decision check(Kind kind, String userKey, String ip) {
        try {
            if (settings.isOff() || kind == Kind.SKIP) {
                return Decision.ALLOW;
            }
            long now = clock.getAsLong();
            boolean over = false;
            String scope = null;
            long windowEnd = now + WINDOW_MS;
            long userEnd = 0;
            long ipEnd = 0;
            if (kind == Kind.REFRESH) {
                if (ip != null) {
                    AttemptCounter.Hit h = refreshPerIp.hit(ip, null, now);
                    if (h.count() > settings.apiRefreshPerIpPerMinute()) {
                        over = true;
                        scope = "refresh-ip";
                        windowEnd = h.windowEndMillis();
                    }
                }
            } else {
                // A caller that stayed over the limit for several minutes in a row is held off for a while.
                long heldMs = Math.max(heldMillis("user|" + userKey, userKey, now), heldMillis("ip|" + ip, ip, now));
                if (heldMs > 0) {
                    return sustainedDecision(heldMs);
                }
                if (userKey != null) {
                    AttemptCounter.Hit h = perUser.hit(userKey, null, now);
                    if (h.count() > settings.apiPerUserPerMinute()) {
                        over = true;
                        scope = "user";
                        windowEnd = h.windowEndMillis();
                        userEnd = h.windowEndMillis();
                    }
                }
                if (ip != null && settings.ipRulesEnabled()) {
                    AttemptCounter.Hit h = perIp.hit(ip, null, now);
                    if (h.count() > settings.apiPerIpPerMinute()) {
                        ipEnd = h.windowEndMillis();
                        if (!over) {
                            windowEnd = h.windowEndMillis();
                        }
                        over = true;
                        scope = scope == null ? "ip" : scope;
                    }
                }
            }
            if (!over) {
                return Decision.ALLOW;
            }
            if (kind == Kind.GENERAL) {
                boolean newlyHeld = (userEnd > 0 && noteOver("user|" + userKey, userKey, userEnd, now))
                        | (ipEnd > 0 && noteOver("ip|" + ip, ip, ipEnd, now));
                if (newlyHeld && settings.isEnforce()) {
                    long heldMs = Math.max(heldMillis("user|" + userKey, userKey, now), heldMillis("ip|" + ip, ip, now));
                    return sustainedDecision(heldMs);
                }
            }
            long retry = Math.max(1, (windowEnd - now + 999) / 1000);
            if (settings.isEnforce()) {
                return new Decision(true, retry);
            }
            String gate = scope + "|" + ("user".equals(scope) ? userKey : ip);
            if (perIp.hit("log|" + gate, null, now).count() == 1) {
                log.warn("SECURITY api-rate WOULD BLOCK ({}) user={} ip={} mode={}", scope, Masking.username(userKey),
                        Masking.plain(ip), settings.mode());
            }
            return Decision.ALLOW;
        } catch (RuntimeException e) {
            log.error("API rate check failed; allowing the request", e);
            return Decision.ALLOW;
        }
    }
}
