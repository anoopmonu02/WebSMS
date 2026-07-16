package com.smsweb.sms.dto.mobile;

import java.util.List;

/**
 * Fixed allow-lists for the mobile student profile self-edit feature.
 * Kept separate from Student.java (which stores these as plain strings,
 * free-text at the entity level) — validation happens here, at the mobile
 * write endpoint, before anything is saved.
 */
public final class MobileProfileConstants {

    public static final List<String> QUALIFICATION_OPTIONS = List.of(
            "ILLITERATE",
            "UPTO 5TH",
            "UPTO 8TH",
            "UPTO 10TH",
            "UPTO 12TH/EQUIVALENT",
            "GRADUATE/EQUIVALENT",
            "POST GRADUATE/EQUIVALENT",
            "DOCTORATE/EQUIVALENT"
    );

    public static final List<String> BLOOD_GROUPS = List.of(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "NO PREFERENCE"
    );

    public static boolean isValidQualification(String value) {
        return value != null && QUALIFICATION_OPTIONS.contains(value.trim().toUpperCase());
    }

    public static boolean isValidBloodGroup(String value) {
        return value != null && BLOOD_GROUPS.contains(value.trim().toUpperCase());
    }

    public static boolean isValidEmail(String value) {
        return value != null && value.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    }

    public static boolean isValidIfsc(String value) {
        return value != null && value.trim().toUpperCase().matches("^[A-Z]{4}0[A-Z0-9]{6}$");
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    /**
     * Some schools keep a placeholder "No Bank" row in the Bank lookup table
     * for students who don't have real bank details on file (Student.bank is
     * a mandatory FK, so this placeholder is how that's represented rather
     * than leaving the column null). When a student selects this, account
     * number/branch name/IFSC should be allowed to stay blank rather than
     * being force-required like a real bank selection.
     */
    public static boolean isNoBankPlaceholder(String bankName) {
        if (bankName == null) return false;
        String normalized = bankName.trim().toUpperCase();
        return normalized.equals("NO BANK") || normalized.equals("NA") || normalized.equals("N/A");
    }

    private MobileProfileConstants() {}
}
