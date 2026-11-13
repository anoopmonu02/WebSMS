package com.smsweb.sms.services.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Short keyed fingerprints of text, used only inside the throttling counters: to notice that the same wrong password
 * was typed again, and to count different usernames without keeping the usernames. The key is random per process and
 * never stored, so the values mean nothing outside the running application and cannot be reversed to the text.
 * This is NOT how passwords are stored; those stay BCrypt-hashed as before.
 */
public final class Fingerprints {

    private static final byte[] KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(KEY);
    }

    private Fingerprints() {
    }

    /** 16 hex characters, or null when the input is null. */
    public static String of(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            byte[] h = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((h[i] >> 4) & 0xF, 16)).append(Character.forDigit(h[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
