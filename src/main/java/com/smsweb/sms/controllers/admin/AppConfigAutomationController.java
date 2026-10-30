package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.automation.AllClearMessage;
import com.smsweb.sms.services.admin.BirthdayNotificationSettingsService;
import com.smsweb.sms.services.automation.AbsenceReportService;
import com.smsweb.sms.services.automation.AllClearMessageService;
import com.smsweb.sms.services.automation.AutomationMailService;
import com.smsweb.sms.services.automation.AutomationSettings;
import com.smsweb.sms.services.automation.AutomationSettingsService;
import com.smsweb.sms.services.automation.BackupScheduleService;
import com.smsweb.sms.services.security.SecretCipher;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints behind the "Email Account", "Database Backup" and "Notifications" tabs of App Config. Kept
 * separate from AppConfigController so that file stays untouched. Everything belongs to the school in the
 * logged-in session, never to a school id sent by the browser.
 */
@RestController
@RequestMapping("/admin/app-config")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class AppConfigAutomationController {

    private static final Logger log = LoggerFactory.getLogger(AppConfigAutomationController.class);

    private final BackupScheduleService backupScheduleService;
    private final AutomationSettingsService settingsService;
    private final AutomationMailService mailService;
    private final AbsenceReportService reportService;
    private final AllClearMessageService allClearService;
    private final SecretCipher secretCipher;
    private final BirthdayNotificationSettingsService birthdayService;

    public AppConfigAutomationController(BackupScheduleService backupScheduleService,
                                         AutomationSettingsService settingsService,
                                         AutomationMailService mailService,
                                         AbsenceReportService reportService,
                                         AllClearMessageService allClearService,
                                         SecretCipher secretCipher,
                                         BirthdayNotificationSettingsService birthdayService) {
        this.birthdayService = birthdayService;
        this.allClearService = allClearService;
        this.backupScheduleService = backupScheduleService;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.reportService = reportService;
        this.secretCipher = secretCipher;
    }

    // ── Email Account tab ───────────────────────────────────────────────────────────────────────

    @GetMapping("/email-account")
    public ResponseEntity<Map<String, Object>> getEmailAccount(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        AutomationSettings s = settingsService.load(schoolId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("sender", s.getMailSender());
        m.put("hasPassword", s.hasPassword());
        m.put("keyConfigured", secretCipher.isConfigured());
        m.put("fromName", s.getMailFromName());
        m.put("host", s.getMailHost());
        m.put("port", s.getMailPort());
        m.put("security", s.getMailSecurity());
        m.put("auth", s.isMailAuth());
        m.put("timeout", s.getMailTimeoutSeconds());
        m.put("accountReady", s.isAccountReady());
        m.put("reminderReceivers", String.join(", ", s.getReminderRecipients()));
        m.put("reportReceivers", String.join(", ", s.getReportRecipients()));
        m.put("backupReceivers", String.join(", ", AutomationSettingsService.splitRecipients(
                String.valueOf(backupScheduleService.get(schoolId).get("receivers")))));
        return ResponseEntity.ok(m);
    }

    @PostMapping("/email-account")
    public ResponseEntity<Map<String, Object>> saveEmailAccount(@RequestParam String sender,
                                                                @RequestParam(required = false) String password,
                                                                @RequestParam(required = false) String fromName,
                                                                @RequestParam String host,
                                                                @RequestParam int port,
                                                                @RequestParam String security,
                                                                @RequestParam(defaultValue = "true") boolean auth,
                                                                @RequestParam(defaultValue = "30") int timeout,
                                                                HttpSession session) {
        Long schoolId = schoolId(session);
        AutomationSettingsService.AccountRequest r = new AutomationSettingsService.AccountRequest();
        r.sender = sender;
        r.password = password;
        r.fromName = fromName;
        r.host = host;
        r.port = port;
        r.security = security;
        r.auth = auth;
        r.timeoutSeconds = timeout;
        return saved(schoolId, () -> settingsService.saveAccount(schoolId, r), "Email account saved.");
    }

    /** Sends a test message through the SAVED account to one address typed by the admin. */
    @PostMapping("/email-account/test")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestParam String to, HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        List<String> receivers = AutomationSettingsService.splitRecipients(to);
        String error = receivers.isEmpty() ? "Please enter the email address to send the test to."
                : AutomationSettingsService.validateRecipients(to);
        if (error != null) {
            return fail(HttpStatus.BAD_REQUEST, error);
        }
        AutomationSettings s = settingsService.load(schoolId);
        if (!s.isAccountReady()) {
            return fail(HttpStatus.BAD_REQUEST, "Please save the email account first.");
        }
        try {
            mailService.send(s, receivers, "Test email from WebSMS",
                    "This is a test email. Your email account is working.", false);
            return ok("Test email sent to " + String.join(", ", receivers) + ".");
        } catch (Exception e) {
            log.error("Test email failed for school {}", schoolId, e);
            return fail(HttpStatus.BAD_GATEWAY, "Could not send the test email: " + describeMailFailure(e));
        }
    }

    // ── Database Backup tab ─────────────────────────────────────────────────────────────────────

    @GetMapping("/backup-schedule")
    public ResponseEntity<Map<String, Object>> getBackupSchedule(HttpSession session) {
        return ResponseEntity.ok(backupScheduleService.get(schoolId(session)));
    }

    @PostMapping("/backup-schedule")
    public ResponseEntity<Map<String, Object>> saveBackupSchedule(@RequestParam String frequency,
                                                                  @RequestParam(required = false) String dayOfWeek,
                                                                  @RequestParam String time,
                                                                  @RequestParam(required = false) String time2,
                                                                  @RequestParam(required = false) String receivers,
                                                                  HttpSession session) {
        Long schoolId = schoolId(session);
        return saved(schoolId, () -> backupScheduleService.save(schoolId, frequency, dayOfWeek, time, time2, receivers),
                "Backup schedule saved. Takes effect from the next run onward.");
    }

    // ── Notifications tab ───────────────────────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        AutomationSettings s = settingsService.load(schoolId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("accountReady", s.isAccountReady());
        m.put("accountSender", s.getMailSender());
        m.put("reminderOn", s.isReminderOn());
        m.put("reminderTime", s.getReminderTime().toString());
        m.put("reminderGap", s.getReminderGapMinutes());
        m.put("reminderReceivers", String.join(", ", s.getReminderRecipients()));
        m.put("reportOn", s.isReportOn());
        m.put("reportDays", s.getReportDays());
        m.put("reportFrequency", s.getReportFrequency().name());
        m.put("reportTime", s.getReportTime().toString());
        m.put("reportDayOfWeek", s.getReportDayOfWeek().name().substring(0, 3));
        m.put("reportDayOfMonth", s.getReportDayOfMonth());
        m.put("reportReceivers", String.join(", ", s.getReportRecipients()));
        Map<String, String> cfg = settingsService.snapshot();
        m.put("reminderLastSent", settingsService.lastSent(cfg, AutomationSettingsService.JOB_REMINDER, schoolId));
        m.put("reportLastSent", settingsService.lastSent(cfg, AutomationSettingsService.JOB_REPORT, schoolId));
        return ResponseEntity.ok(m);
    }

    @PostMapping("/notifications/reminder")
    public ResponseEntity<Map<String, Object>> saveReminder(@RequestParam(defaultValue = "false") boolean on,
                                                            @RequestParam String time,
                                                            @RequestParam(defaultValue = "0") int gap,
                                                            @RequestParam(required = false) String receivers,
                                                            HttpSession session) {
        Long schoolId = schoolId(session);
        AutomationSettingsService.ReminderRequest r = new AutomationSettingsService.ReminderRequest();
        r.on = on;
        r.time = time;
        r.gapMinutes = gap;
        r.recipients = receivers;
        return saved(schoolId, () -> settingsService.saveReminder(schoolId, r), "Reminder settings saved.");
    }

    @PostMapping("/notifications/report")
    public ResponseEntity<Map<String, Object>> saveReport(@RequestParam(defaultValue = "false") boolean on,
                                                          @RequestParam(defaultValue = "7") int days,
                                                          @RequestParam String frequency,
                                                          @RequestParam String time,
                                                          @RequestParam(required = false) String dayOfWeek,
                                                          @RequestParam(required = false) String dayOfMonth,
                                                          @RequestParam(required = false) String receivers,
                                                          HttpSession session) {
        Long schoolId = schoolId(session);
        AutomationSettingsService.ReportRequest r = new AutomationSettingsService.ReportRequest();
        r.on = on;
        r.days = days;
        r.frequency = frequency;
        r.time = time;
        r.dayOfWeek = dayOfWeek == null || dayOfWeek.isBlank() ? "MON" : dayOfWeek;
        r.dayOfMonth = dayOfMonth == null || dayOfMonth.isBlank() ? "1" : dayOfMonth;
        r.recipients = receivers;
        return saved(schoolId, () -> settingsService.saveReport(schoolId, r), "Report settings saved.");
    }

    /** Sends today's reminder email now (even if attendance is confirmed, it says so instead of staying silent). */
    @PostMapping("/notifications/reminder/test")
    public ResponseEntity<Map<String, Object>> testReminder(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        AutomationSettings s = settingsService.load(schoolId);
        if (!s.isAccountReady() || s.getReminderRecipients().isEmpty()) {
            return fail(HttpStatus.BAD_REQUEST, "Please save the email account and the receiver email first.");
        }
        try {
            mailService.send(s, s.getReminderRecipients(), "Test: attendance reminder",
                    "This is a test of the attendance reminder email. A real reminder is sent only when today's attendance is not confirmed.", false);
            return ok("Test reminder sent to " + String.join(", ", s.getReminderRecipients()) + ".");
        } catch (Exception e) {
            log.error("Test reminder failed for school {}", schoolId, e);
            return fail(HttpStatus.BAD_GATEWAY, "Could not send the test reminder: " + describeMailFailure(e));
        }
    }

    @PostMapping("/notifications/report/send-now")
    public ResponseEntity<Map<String, Object>> sendReportNow(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        AutomationSettings s = settingsService.load(schoolId);
        if (!s.isAccountReady() || s.getReportRecipients().isEmpty()) {
            return fail(HttpStatus.BAD_REQUEST, "Please save the email account and the receiver email first.");
        }
        try {
            return ok(reportService.sendReport(s));
        } catch (Exception e) {
            log.error("Send-report-now failed for school {}", schoolId, e);
            return fail(HttpStatus.BAD_GATEWAY, "Could not send the report: " + describeMailFailure(e));
        }
    }

    // ── All-clear messages (Notifications tab) ──────────────────────────────────────────────────

    @GetMapping("/notifications/all-clear")
    public ResponseEntity<Map<String, Object>> listAllClear(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        int active = 0;
        for (AllClearMessage m : allClearService.list(schoolId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("text", m.getMessageText());
            row.put("active", m.isActive());
            rows.add(row);
            if (m.isActive()) {
                active++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("messages", rows);
        m.put("activeCount", active);
        m.put("max", AllClearMessageService.MAX_MESSAGES);
        return ResponseEntity.ok(m);
    }

    @PostMapping("/notifications/all-clear")
    public ResponseEntity<Map<String, Object>> addAllClear(@RequestParam String text, HttpSession session) {
        Long schoolId = schoolId(session);
        return saved(schoolId, () -> allClearService.add(schoolId, text), "Message added.");
    }

    @PostMapping("/notifications/all-clear/update")
    public ResponseEntity<Map<String, Object>> updateAllClear(@RequestParam Long id, @RequestParam String text, HttpSession session) {
        Long schoolId = schoolId(session);
        return saved(schoolId, () -> allClearService.update(schoolId, id, text), "Message updated.");
    }

    @PostMapping("/notifications/all-clear/active")
    public ResponseEntity<Map<String, Object>> setAllClearActive(@RequestParam Long id, @RequestParam boolean active, HttpSession session) {
        Long schoolId = schoolId(session);
        return saved(schoolId, () -> allClearService.setActive(schoolId, id, active), active ? "Message switched on." : "Message switched off.");
    }

    @PostMapping("/notifications/all-clear/delete")
    public ResponseEntity<Map<String, Object>> deleteAllClear(@RequestParam Long id, HttpSession session) {
        Long schoolId = schoolId(session);
        return saved(schoolId, () -> allClearService.delete(schoolId, id), "Message deleted.");
    }

    @PostMapping("/notifications/all-clear/restore")
    public ResponseEntity<Map<String, Object>> restoreAllClear(HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        int added = allClearService.restoreDefaults(schoolId);
        return ok(added == 0 ? "All the built-in messages are already in your list." : added + " built-in message(s) added back.");
    }

    @GetMapping("/notifications/all-clear/preview")
    public ResponseEntity<Map<String, Object>> previewAllClear(@RequestParam(defaultValue = "7") int days, HttpSession session) {
        Long schoolId = schoolId(session);
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        return ok(allClearService.pickRendered(schoolId, Math.max(AutomationSettingsService.MIN_DAYS, Math.min(AutomationSettingsService.MAX_DAYS, days))));
    }

    // ── Birthday tab (one global time for all schools) ──────────────────────────────────────────

    @GetMapping("/birthday")
    public ResponseEntity<Map<String, Object>> getBirthday() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("time", birthdayService.currentTime());
        return ResponseEntity.ok(m);
    }

    @PostMapping("/birthday")
    public ResponseEntity<Map<String, Object>> saveBirthday(@RequestParam String time, HttpSession session) {
        // Needs a logged-in school like the other tabs; the time itself is global.
        return saved(schoolId(session), () -> birthdayService.saveTime(time),
                "Birthday notifications will be sent daily at " + time + ". Takes effect from the next run.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * A short, safe reason for the browser. The raw exception text (server names, addresses, stack details)
     * is only written to the server log, never returned to the client.
     */
    private static String describeMailFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String n = t.getClass().getName();
            if (n.contains("AuthenticationFailed") || n.contains("MailAuthentication")) {
                return "the mail server rejected the login. Check the sender email and password.";
            }
            if (t instanceof java.net.UnknownHostException) {
                return "the SMTP server name was not found. Check the server name.";
            }
            if (t instanceof java.net.SocketTimeoutException) {
                return "the mail server did not respond in time.";
            }
            if (t instanceof java.net.ConnectException || n.contains("MailConnect")) {
                return "could not connect to the mail server. Check the server name, port and security.";
            }
            if (t instanceof javax.net.ssl.SSLException) {
                return "a secure connection could not be made. Check the security setting and port.";
            }
        }
        return "please check the email account settings and try again.";
    }

    private interface SaveAction {
        String run();
    }

    private ResponseEntity<Map<String, Object>> saved(Long schoolId, SaveAction action, String okMessage) {
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login, so this can't be saved.");
        }
        try {
            String error = action.run();
            return error == null ? ok(okMessage) : fail(HttpStatus.BAD_REQUEST, error);
        } catch (Exception e) {
            log.error("Failed to save automation settings for school {}", schoolId, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save - please try again.");
        }
    }

    private static Long schoolId(HttpSession session) {
        Object school = session.getAttribute("school");
        return school instanceof School ? ((School) school).getId() : null;
    }

    private static ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", message);
        return ResponseEntity.ok(m);
    }

    private static ResponseEntity<Map<String, Object>> fail(HttpStatus status, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", error);
        return ResponseEntity.status(status).body(m);
    }
}
