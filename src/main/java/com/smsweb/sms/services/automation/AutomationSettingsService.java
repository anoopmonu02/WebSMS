package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.security.SecretCipher;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * NEW service (feature: admin automation). Reads and writes one school's email account and notification settings.
 * No new table: every value is a row in the existing system_config table, named <KEY>_<schoolId>
 * (same per-school naming as SIBLING_DISCOUNT_ALLOWED_PER_GROUP_{schoolId}).
 *
 * Also keeps the tiny "already done today" marks of the automatic jobs (AUTO_DONE_<JOB>_<schoolId>),
 * which is what makes a job run once a day and survive a restart.
 *
 * The email password is stored encrypted (SecretCipher) and is never handed back to the screen.
 */
@Service
public class AutomationSettingsService {

    // ── system_config key prefixes (each is followed by the school id) ──────────────────────────
    static final String K_MAIL_SENDER = "AUTO_MAIL_SENDER_";
    static final String K_MAIL_PASSWORD = "AUTO_MAIL_PASSWORD_";
    static final String K_MAIL_HOST = "AUTO_MAIL_HOST_";
    static final String K_MAIL_PORT = "AUTO_MAIL_PORT_";
    static final String K_MAIL_SECURITY = "AUTO_MAIL_SECURITY_";
    static final String K_MAIL_NAME = "AUTO_MAIL_NAME_";
    static final String K_MAIL_AUTH = "AUTO_MAIL_AUTH_";
    static final String K_MAIL_TIMEOUT = "AUTO_MAIL_TIMEOUT_";
    static final String K_REMINDER_TO = "ATT_REMINDER_TO_";
    static final String K_REPORT_TO = "ABS_REPORT_TO_";
    static final String K_REMINDER_ON = "ATT_REMINDER_ON_";
    static final String K_REMINDER_TIME = "ATT_REMINDER_TIME_";
    static final String K_REMINDER_GAP = "ATT_REMINDER_GAP_";
    static final String K_REPORT_ON = "ABS_REPORT_ON_";
    static final String K_REPORT_DAYS = "ABS_REPORT_DAYS_";
    static final String K_REPORT_FREQ = "ABS_REPORT_FREQ_";
    static final String K_REPORT_TIME = "ABS_REPORT_TIME_";
    static final String K_REPORT_DOW = "ABS_REPORT_DOW_";
    static final String K_REPORT_DOM = "ABS_REPORT_DOM_";
    static final String K_DONE = "AUTO_DONE_";
    static final String K_SENT = "AUTO_SENT_";

    public static final String JOB_REMINDER_1 = "ATT_REMINDER_1";
    public static final String JOB_REMINDER_2 = "ATT_REMINDER_2";
    public static final String JOB_REPORT = "ABS_REPORT";
    /** Used only for the "last sent" note (first and second reminder share it). */
    public static final String JOB_REMINDER = "ATT_REMINDER";

    public static final int MAX_RECIPIENTS = 5;
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 60;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final int MIN_TIMEOUT = 5;
    public static final int MAX_TIMEOUT = 120;
    private static final String NO_SCHOOL = "No school is linked to your login, so these settings can't be saved.";
    private static final String KEY_MISSING = "The password could not be saved: the server's encryption key (SMS_CONFIG_ENCRYPTION_KEY) is not configured. Nothing was changed.";
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,98}[A-Za-z0-9])?$");
    private static final int[] ALLOWED_GAPS = {0, 15, 30, 45, 60, 90, 120};

    private final SystemConfigRepository systemConfigRepository;
    private final SecretCipher secretCipher;

    public AutomationSettingsService(SystemConfigRepository systemConfigRepository, SecretCipher secretCipher) {
        this.systemConfigRepository = systemConfigRepository;
        this.secretCipher = secretCipher;
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    /** The whole system_config table as name to value (a few dozen rows), read once per scheduler tick. */
    public Map<String, String> snapshot() {
        Map<String, String> map = new HashMap<>();
        for (SystemConfig c : systemConfigRepository.findAll()) {
            map.put(c.getConfigName(), c.getConfigValue());
        }
        return map;
    }

    public AutomationSettings load(Long schoolId) {
        return fromSnapshot(snapshot(), schoolId);
    }

    /** Builds a school's settings from a snapshot. Anything missing or malformed falls back to its default. */
    public AutomationSettings fromSnapshot(Map<String, String> cfg, Long schoolId) {
        AutomationSettings s = new AutomationSettings();
        s.setSchoolId(schoolId);
        s.setMailSender(get(cfg, K_MAIL_SENDER, schoolId, ""));
        s.setMailPasswordEncrypted(get(cfg, K_MAIL_PASSWORD, schoolId, ""));
        s.setMailFromName(get(cfg, K_MAIL_NAME, schoolId, ""));
        String host = get(cfg, K_MAIL_HOST, schoolId, "").trim();
        if (!host.isEmpty()) {
            s.setMailHost(host);
        }
        int port = parseIntOr(get(cfg, K_MAIL_PORT, schoolId, ""), 587);
        s.setMailPort(port >= 1 && port <= 65535 ? port : 587);
        String security = get(cfg, K_MAIL_SECURITY, schoolId, "").trim().toUpperCase();
        if (isValidSecurity(security)) {
            s.setMailSecurity(security);
        }
        s.setMailAuth(!"false".equalsIgnoreCase(get(cfg, K_MAIL_AUTH, schoolId, "true")));
        int timeout = parseIntOr(get(cfg, K_MAIL_TIMEOUT, schoolId, ""), 30);
        s.setMailTimeoutSeconds(timeout >= MIN_TIMEOUT && timeout <= MAX_TIMEOUT ? timeout : 30);

        s.setReminderRecipients(splitRecipients(get(cfg, K_REMINDER_TO, schoolId, "")));
        s.setReminderOn("true".equalsIgnoreCase(get(cfg, K_REMINDER_ON, schoolId, "false")));
        LocalTime rt = AutomationScheduleUtil.parseTime(get(cfg, K_REMINDER_TIME, schoolId, ""));
        if (rt != null) {
            s.setReminderTime(rt);
        }
        s.setReminderGapMinutes(parseIntOr(get(cfg, K_REMINDER_GAP, schoolId, "0"), 0));

        s.setReportRecipients(splitRecipients(get(cfg, K_REPORT_TO, schoolId, "")));
        s.setReportOn("true".equalsIgnoreCase(get(cfg, K_REPORT_ON, schoolId, "false")));
        int days = parseIntOr(get(cfg, K_REPORT_DAYS, schoolId, ""), AutomationSettings.DEFAULT_THRESHOLD_DAYS);
        s.setReportDays(days < MIN_DAYS || days > MAX_DAYS ? AutomationSettings.DEFAULT_THRESHOLD_DAYS : days);
        try {
            s.setReportFrequency(AutomationScheduleUtil.Frequency.valueOf(get(cfg, K_REPORT_FREQ, schoolId, "DAILY").toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            // keeps the default (DAILY)
        }
        LocalTime reportTime = AutomationScheduleUtil.parseTime(get(cfg, K_REPORT_TIME, schoolId, ""));
        if (reportTime != null) {
            s.setReportTime(reportTime);
        }
        DayOfWeek dow = AutomationScheduleUtil.dayOfWeekFromCode(get(cfg, K_REPORT_DOW, schoolId, ""));
        if (dow != null) {
            s.setReportDayOfWeek(dow);
        }
        String dom = get(cfg, K_REPORT_DOM, schoolId, "1").trim();
        if (isValidDayOfMonth(dom)) {
            s.setReportDayOfMonth(dom.toUpperCase());
        }
        return s;
    }

    public boolean isDone(Map<String, String> cfg, String job, Long schoolId, LocalDate day) {
        return day.toString().equals(cfg.get(K_DONE + job + "_" + schoolId));
    }

    // ── Writing ─────────────────────────────────────────────────────────────────────────────────

    /** Remembers that a job has finished for this school on this day (also used for deliberate skips). */
    public void markDone(String job, Long schoolId, LocalDate day) {
        upsert(K_DONE + job + "_" + schoolId, day.toString(),
                "Automatic job '" + job + "' last finished on this date for school " + schoolId + ". Written by the system, do not edit.", schoolId);
    }

    /** Remembers when an email of this job was last really sent (shown on the Notifications tab). */
    public void markSent(String job, Long schoolId) {
        try {
            upsert(K_SENT + job + "_" + schoolId, java.time.LocalDateTime.now().withSecond(0).withNano(0).toString().replace('T', ' '),
                    "When the automatic email '" + job + "' was last sent for school " + schoolId + ". Written by the system, do not edit.", schoolId);
        } catch (RuntimeException e) {
            // the note is only informational; never let it fail a send that already happened
        }
    }

    /** "yyyy-MM-dd HH:mm" of the last real send, or "" when none yet. */
    public String lastSent(Map<String, String> cfg, String job, Long schoolId) {
        String v = cfg.get(K_SENT + job + "_" + schoolId);
        return v == null ? "" : v;
    }

    /** What the Email Account card posts. A blank {@code password} means "keep the saved one". */
    public static class AccountRequest {
        public String sender;
        public String password;
        public String fromName;
        public String host;
        public int port;
        public String security;
        public boolean auth;
        public int timeoutSeconds;
    }

    public static class ReminderRequest {
        public boolean on;
        public String time;
        public int gapMinutes;
        public String recipients;
    }

    public static class ReportRequest {
        public boolean on;
        public int days;
        public String frequency;
        public String time;
        public String dayOfWeek;
        public String dayOfMonth;
        public String recipients;
    }

    /** @return null on success, otherwise a message for the user. Nothing is saved unless everything is valid. */
    public String saveAccount(Long schoolId, AccountRequest r) {
        if (schoolId == null) {
            return NO_SCHOOL;
        }
        String sender = r.sender == null ? "" : r.sender.trim();
        if (!EMAIL.matcher(sender).matches()) {
            return "Please enter a valid sender email address.";
        }
        String host = r.host == null ? "" : r.host.trim();
        if (!HOST.matcher(host).matches()) {
            return "Please enter a valid SMTP server name, for example smtp.gmail.com.";
        }
        if (r.port < 1 || r.port > 65535) {
            return "The port must be between 1 and 65535.";
        }
        String security = r.security == null ? "" : r.security.trim().toUpperCase();
        if (!isValidSecurity(security)) {
            return "Please choose a security type.";
        }
        if (r.timeoutSeconds < MIN_TIMEOUT || r.timeoutSeconds > MAX_TIMEOUT) {
            return "The timeout must be between " + MIN_TIMEOUT + " and " + MAX_TIMEOUT + " seconds.";
        }
        String fromName = r.fromName == null ? "" : r.fromName.trim();
        if (fromName.length() > 100 || fromName.indexOf('\r') >= 0 || fromName.indexOf('\n') >= 0) {
            return "The sender name must be a single line of at most 100 characters.";
        }
        boolean newPassword = r.password != null && !r.password.isBlank();
        if (newPassword && !secretCipher.isConfigured()) {
            return KEY_MISSING;
        }
        boolean hasPassword = newPassword || !get(snapshot(), K_MAIL_PASSWORD, schoolId, "").isBlank();
        if (r.auth && !hasPassword) {
            return "Please enter the password, or untick 'Server requires login'.";
        }

        upsert(K_MAIL_SENDER + schoolId, sender, "Sender email account of school " + schoolId + " (used by all its automatic emails).", schoolId);
        upsert(K_MAIL_HOST + schoolId, host, "SMTP server of the sender account, school " + schoolId + ".", schoolId);
        upsert(K_MAIL_PORT + schoolId, String.valueOf(r.port), "SMTP port of the sender account, school " + schoolId + ".", schoolId);
        upsert(K_MAIL_SECURITY + schoolId, security, "SMTP security (STARTTLS/SSL/NONE) of the sender account, school " + schoolId + ".", schoolId);
        upsert(K_MAIL_NAME + schoolId, fromName, "Display name of the sender, school " + schoolId + ".", schoolId);
        upsert(K_MAIL_AUTH + schoolId, String.valueOf(r.auth), "Whether the SMTP server needs a login, school " + schoolId + ".", schoolId);
        upsert(K_MAIL_TIMEOUT + schoolId, String.valueOf(r.timeoutSeconds), "SMTP timeout in seconds, school " + schoolId + ".", schoolId);
        if (newPassword) {
            upsert(K_MAIL_PASSWORD + schoolId, secretCipher.encrypt(r.password),
                    "Password of the sender account for school " + schoolId + " (encrypted).", schoolId);
        }
        return null;
    }

    public String saveReminder(Long schoolId, ReminderRequest r) {
        if (schoolId == null) {
            return NO_SCHOOL;
        }
        String recipientsError = validateRecipients(r.recipients);
        if (recipientsError != null) {
            return recipientsError;
        }
        List<String> recipients = splitRecipients(r.recipients);
        if (!AutomationScheduleUtil.isValidTime(r.time)) {
            return "Please pick a valid reminder time.";
        }
        if (!isAllowedGap(r.gapMinutes)) {
            return "Please pick a valid second-reminder gap.";
        }
        if (r.on) {
            String why = notReadyToSwitchOn(schoolId, recipients);
            if (why != null) {
                return why;
            }
        }
        upsert(K_REMINDER_TO + schoolId, String.join(", ", recipients), "Receiver emails of the attendance reminder, school " + schoolId + ".", schoolId);
        upsert(K_REMINDER_ON + schoolId, String.valueOf(r.on), "Attendance-not-confirmed reminder on/off, school " + schoolId + ".", schoolId);
        upsert(K_REMINDER_TIME + schoolId, r.time, "Attendance-not-confirmed reminder time (HH:mm), school " + schoolId + ".", schoolId);
        upsert(K_REMINDER_GAP + schoolId, String.valueOf(r.gapMinutes), "Minutes after the first reminder for a second one (0 = none), school " + schoolId + ".", schoolId);
        return null;
    }

    public String saveReport(Long schoolId, ReportRequest r) {
        if (schoolId == null) {
            return NO_SCHOOL;
        }
        String recipientsError = validateRecipients(r.recipients);
        if (recipientsError != null) {
            return recipientsError;
        }
        List<String> recipients = splitRecipients(r.recipients);
        if (r.days < MIN_DAYS || r.days > MAX_DAYS) {
            return "Absent days must be between " + MIN_DAYS + " and " + MAX_DAYS + ".";
        }
        AutomationScheduleUtil.Frequency frequency;
        try {
            frequency = AutomationScheduleUtil.Frequency.valueOf(r.frequency == null ? "" : r.frequency.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Please pick a valid report frequency.";
        }
        if (!AutomationScheduleUtil.isValidTime(r.time)) {
            return "Please pick a valid report time.";
        }
        DayOfWeek dow = AutomationScheduleUtil.dayOfWeekFromCode(r.dayOfWeek);
        if (dow == null) {
            return "Please pick a valid weekday for the report.";
        }
        String dom = r.dayOfMonth == null ? "" : r.dayOfMonth.trim();
        if (!isValidDayOfMonth(dom)) {
            return "The day of the month must be 1 to 28, or the last day.";
        }
        if (r.on) {
            String why = notReadyToSwitchOn(schoolId, recipients);
            if (why != null) {
                return why;
            }
        }
        upsert(K_REPORT_TO + schoolId, String.join(", ", recipients), "Receiver emails of the long absence report, school " + schoolId + ".", schoolId);
        upsert(K_REPORT_ON + schoolId, String.valueOf(r.on), "Long absence report on/off, school " + schoolId + ".", schoolId);
        upsert(K_REPORT_DAYS + schoolId, String.valueOf(r.days), "Consecutive absent school days that put a student on the report, school " + schoolId + ".", schoolId);
        upsert(K_REPORT_FREQ + schoolId, frequency.name(), "Long absence report frequency (DAILY/WEEKLY/MONTHLY), school " + schoolId + ".", schoolId);
        upsert(K_REPORT_TIME + schoolId, r.time, "Long absence report time (HH:mm), school " + schoolId + ".", schoolId);
        upsert(K_REPORT_DOW + schoolId, AutomationScheduleUtil.dayOfWeekCode(dow), "Weekday of the weekly long absence report, school " + schoolId + ".", schoolId);
        upsert(K_REPORT_DOM + schoolId, dom.toUpperCase(), "Day of month of the monthly long absence report (1-28 or LAST), school " + schoolId + ".", schoolId);
        return null;
    }

    /** A notification can only be switched on when the school's email account works and it has a receiver. */
    private String notReadyToSwitchOn(Long schoolId, List<String> recipients) {
        if (!load(schoolId).isAccountReady()) {
            return "Please set up the Email Account tab first, then switch this on.";
        }
        if (recipients.isEmpty()) {
            return "Please enter at least one receiver email to switch this on.";
        }
        return null;
    }

    /** @return null when the list (comma/space separated) holds 0 to 5 valid emails, otherwise a message. */
    public static String validateRecipients(String raw) {
        List<String> list = splitRecipients(raw);
        for (String to : list) {
            if (!EMAIL.matcher(to).matches()) {
                return "'" + to + "' is not a valid email address.";
            }
        }
        if (list.size() > MAX_RECIPIENTS) {
            return "Please use at most " + MAX_RECIPIENTS + " receiver email addresses.";
        }
        if (String.join(", ", list).length() > 255) {
            return "The receiver list is too long.";
        }
        return null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private void upsert(String key, String value, String description, Long schoolId) {
        SystemConfig config = systemConfigRepository.findByConfigName(key).orElseGet(() -> {
            SystemConfig c = new SystemConfig();
            c.setConfigName(key);
            return c;
        });
        config.setConfigValue(value);
        config.setDescription(description.length() > 500 ? description.substring(0, 500) : description);
        if (schoolId != null) {
            config.setSchoolIds(String.valueOf(schoolId));
        }
        systemConfigRepository.save(config);
    }

    private static String get(Map<String, String> cfg, String prefix, Long schoolId, String fallback) {
        String v = cfg.get(prefix + schoolId);
        return v == null ? fallback : v;
    }

    public static List<String> splitRecipients(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static boolean isValidDayOfMonth(String dom) {
        if (dom == null) {
            return false;
        }
        if ("LAST".equalsIgnoreCase(dom.trim())) {
            return true;
        }
        try {
            int n = Integer.parseInt(dom.trim());
            return n >= 1 && n <= 28;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidSecurity(String v) {
        return AutomationSettings.SECURITY_STARTTLS.equals(v) || AutomationSettings.SECURITY_SSL.equals(v) || AutomationSettings.SECURITY_NONE.equals(v);
    }

    private static boolean isAllowedGap(int gap) {
        for (int g : ALLOWED_GAPS) {
            if (g == gap) {
                return true;
            }
        }
        return false;
    }
}
