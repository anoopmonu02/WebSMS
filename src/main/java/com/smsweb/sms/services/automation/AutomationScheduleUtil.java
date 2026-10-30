package com.smsweb.sms.services.automation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

/**
 * NEW helper (feature: admin automation). Pure date/time rules for the automatic jobs, kept free of
 * Spring and the database so they can be unit tested. Every "now" and "today" passed in is the
 * server's local time (the app pins the JVM to Asia/Kolkata).
 */
public final class AutomationScheduleUtil {

    private AutomationScheduleUtil() {}

    /** A job that was due at HH:mm may still start up to this many minutes late (restart, busy scheduler). */
    public static final int GRACE_MINUTES = 30;

    /** A scheduled backup is considered missed only after this many minutes have passed without a new backup file. */
    public static final int BACKUP_CATCHUP_DELAY_MINUTES = 3;

    /** Smallest allowed distance between the two daily backup times. */
    public static final int MIN_BACKUP_GAP_MINUTES = 10;

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public enum Frequency { DAILY, WEEKLY, MONTHLY }

    public static boolean isValidTime(String value) {
        return value != null && TIME_PATTERN.matcher(value).matches();
    }

    /** @return the parsed time, or null when the text is not a valid HH:mm. */
    public static LocalTime parseTime(String value) {
        return isValidTime(value) ? LocalTime.parse(value) : null;
    }

    /**
     * True when {@code now} lies inside [target + offset, target + offset + grace] of the same calendar day.
     * A window that would slip past midnight is cut at midnight (the next day has its own window).
     */
    public static boolean isDueNow(LocalDateTime now, LocalTime target, int offsetMinutes, int graceMinutes) {
        if (now == null || target == null) {
            return false;
        }
        LocalDateTime start = now.toLocalDate().atTime(target).plusMinutes(offsetMinutes);
        if (!start.toLocalDate().equals(now.toLocalDate())) {
            return false;
        }
        LocalDateTime end = start.plusMinutes(graceMinutes);
        if (!end.toLocalDate().equals(now.toLocalDate())) {
            end = now.toLocalDate().atTime(LocalTime.of(23, 59, 59));
        }
        return !now.isBefore(start) && !now.isAfter(end);
    }

    /**
     * Whether the absence report is meant to go out on this calendar day.
     * DAILY skips Sundays (no school, so nothing changed). MONTHLY accepts 1..28 or "LAST".
     */
    public static boolean isReportDay(LocalDate day, Frequency frequency, DayOfWeek weeklyDay, String monthlyDay) {
        if (day == null || frequency == null) {
            return false;
        }
        switch (frequency) {
            case DAILY:
                return day.getDayOfWeek() != DayOfWeek.SUNDAY;
            case WEEKLY:
                return weeklyDay != null && day.getDayOfWeek() == weeklyDay;
            case MONTHLY:
                if (monthlyDay == null) {
                    return false;
                }
                if ("LAST".equalsIgnoreCase(monthlyDay.trim())) {
                    return day.equals(YearMonth.from(day).atEndOfMonth());
                }
                try {
                    return day.getDayOfMonth() == Integer.parseInt(monthlyDay.trim());
                } catch (NumberFormatException e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * The most recent backup slot that should already have happened (at or before {@code now}).
     * Looks back up to a week so a weekly schedule is found too.
     *
     * @param weekly   true: slots happen only on {@code weeklyDay}; false: every day
     * @param times    the one or two daily backup times
     * @return the latest slot, or null when none falls inside the look-back window
     */
    public static LocalDateTime lastExpectedBackupSlot(LocalDateTime now, boolean weekly, DayOfWeek weeklyDay, List<LocalTime> times) {
        if (now == null || times == null || times.isEmpty()) {
            return null;
        }
        for (int back = 0; back <= 7; back++) {
            LocalDate day = now.toLocalDate().minusDays(back);
            if (weekly && day.getDayOfWeek() != weeklyDay) {
                continue;
            }
            LocalDateTime best = null;
            for (LocalTime t : times) {
                if (t == null) {
                    continue;
                }
                LocalDateTime slot = day.atTime(t);
                if (!slot.isAfter(now) && (best == null || slot.isAfter(best))) {
                    best = slot;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** Shortest distance in minutes between two clock times, treating midnight as a wrap-around. */
    public static int minutesApart(LocalTime a, LocalTime b) {
        int diff = Math.abs(a.getHour() * 60 + a.getMinute() - (b.getHour() * 60 + b.getMinute()));
        return Math.min(diff, 1440 - diff);
    }

    /** The parts of the stored backup cron ("0 m h * * *" / "0 m h * * DOW") the UI and catch-up care about. */
    public static final class BackupCron {
        public final boolean weekly;
        public final DayOfWeek dayOfWeek;
        public final LocalTime time;

        BackupCron(boolean weekly, DayOfWeek dayOfWeek, LocalTime time) {
            this.weekly = weekly;
            this.dayOfWeek = dayOfWeek;
            this.time = time;
        }
    }

    /** @return the parsed cron, or null when it is not in the shape this app writes (never throws). */
    public static BackupCron parseBackupCron(String cron) {
        try {
            if (cron == null) {
                return null;
            }
            String[] f = cron.trim().split("\\s+");
            if (f.length < 6) {
                return null;
            }
            int minute = Integer.parseInt(f[1]);
            int hour = Integer.parseInt(f[2]);
            LocalTime time = LocalTime.of(hour, minute);
            String dow = f[5];
            if ("*".equals(dow) || "?".equals(dow)) {
                return new BackupCron(false, null, time);
            }
            return new BackupCron(true, dayOfWeekFromCode(dow), time);
        } catch (Exception e) {
            return null;
        }
    }

    /** "MON".."SUN" to DayOfWeek; null when the code is unknown. */
    public static DayOfWeek dayOfWeekFromCode(String code) {
        if (code == null) {
            return null;
        }
        switch (code.trim().toUpperCase()) {
            case "MON": return DayOfWeek.MONDAY;
            case "TUE": return DayOfWeek.TUESDAY;
            case "WED": return DayOfWeek.WEDNESDAY;
            case "THU": return DayOfWeek.THURSDAY;
            case "FRI": return DayOfWeek.FRIDAY;
            case "SAT": return DayOfWeek.SATURDAY;
            case "SUN": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    public static String dayOfWeekCode(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "MON";
            case TUESDAY: return "TUE";
            case WEDNESDAY: return "WED";
            case THURSDAY: return "THU";
            case FRIDAY: return "FRI";
            case SATURDAY: return "SAT";
            default: return "SUN";
        }
    }
}
