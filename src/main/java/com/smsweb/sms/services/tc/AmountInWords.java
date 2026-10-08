package com.smsweb.sms.services.tc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Writes a rupee amount in words using the Indian system (thousand, lakh, crore), for example
 * "Rupees Four Thousand Five Hundred Only". Used on the Due Clearance Receipt.
 */
public final class AmountInWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    /** Amounts below zero are treated as zero. Paise are included when present. */
    public static String rupees(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long rupees = value.longValue();
        int paise = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        StringBuilder sb = new StringBuilder("Rupees ");
        sb.append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            sb.append(" and ").append(words(paise)).append(" Paise");
        }
        return sb.append(" Only").toString();
    }

    private static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000L;
        n %= 10_000_000L;
        long lakh = n / 100_000L;
        n %= 100_000L;
        long thousand = n / 1_000L;
        n %= 1_000L;
        long hundred = n / 100L;
        long rest = n % 100L;

        if (crore > 0) {
            sb.append(words(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(twoDigits((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(twoDigits((int) thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (rest > 0) {
            sb.append(twoDigits((int) rest));
        }
        return sb.toString().trim();
    }

    private static String twoDigits(int n) {
        if (n < 20) {
            return ONES[n];
        }
        String tens = TENS[n / 10];
        return n % 10 == 0 ? tens : tens + " " + ONES[n % 10];
    }
}
