package com.smsweb.sms.services.security;

/**
 * Receives the serious throttle events (an account reaching a repeat lock, an address blocked for guessing many
 * accounts). Implemented by the alert e-mail service. Kept as an interface so the throttle has no dependency on the
 * database or mail code; implementations must return quickly and never throw.
 */
public interface SecurityEventSink {

    /** An account was locked (level 1 = first lock, 2 = second within 24 h, ...). {@code key} is lower-cased. */
    void accountLocked(LoginThrottleService.Channel channel, String key, int level, long lockMinutes, String ip);

    /** An address was blocked; {@code spray} is true when it failed on many different accounts. */
    void ipBlocked(LoginThrottleService.Channel channel, String ip, int level, long lockMinutes, int differentAccounts,
                   boolean spray);
}
