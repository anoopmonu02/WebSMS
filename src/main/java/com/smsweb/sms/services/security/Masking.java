package com.smsweb.sms.services.security;

/**
 * Makes usernames and mobile numbers safe to write to the log: shortened, and stripped of line breaks and other
 * control characters (so a visitor cannot forge log lines by typing them into the login form).
 */
public final class Masking {

    private static final int MAX_LEN = 40;

    private Masking() {
    }

    /** "ravi.teacher" becomes "ra***". */
    public static String username(String value) {
        String s = clean(value);
        if (s.isEmpty()) {
            return "?";
        }
        return s.substring(0, Math.min(2, s.length())) + "***";
    }

    /** "9876543210" becomes "98******10". */
    public static String mobile(String value) {
        String s = clean(value);
        if (s.length() <= 4) {
            return s.isEmpty() ? "?" : "****";
        }
        StringBuilder sb = new StringBuilder(s.substring(0, 2));
        for (int i = 2; i < s.length() - 2; i++) {
            sb.append('*');
        }
        return sb.append(s.substring(s.length() - 2)).toString();
    }

    /** An IP address or other short value with control characters removed. */
    public static String plain(String value) {
        String s = clean(value);
        return s.isEmpty() ? "?" : s;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String t = value.trim();
        if (t.length() > MAX_LEN) {
            t = t.substring(0, MAX_LEN);
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            sb.append(c < 0x20 || c == 0x7f ? '?' : c);
        }
        return sb.toString();
    }
}
