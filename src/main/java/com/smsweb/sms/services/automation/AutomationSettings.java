package com.smsweb.sms.services.automation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One school's automation settings: the sending email account (shared by every notification of the
 * school) plus the receivers and schedule of each notification. Plain value object, built from
 * system_config by {@link AutomationSettingsService}.
 */
public class AutomationSettings {

    public static final int DEFAULT_THRESHOLD_DAYS = 7;
    public static final String SECURITY_STARTTLS = "STARTTLS";
    public static final String SECURITY_SSL = "SSL";
    public static final String SECURITY_NONE = "NONE";

    private Long schoolId;

    // Sending email account (SMTP)
    private String mailSender = "";
    private String mailPasswordEncrypted = "";
    private String mailFromName = "";
    private String mailHost = "smtp.gmail.com";
    private int mailPort = 587;
    private String mailSecurity = SECURITY_STARTTLS;
    private boolean mailAuth = true;
    private int mailTimeoutSeconds = 30;

    // Attendance confirmation reminder
    private boolean reminderOn;
    private LocalTime reminderTime = LocalTime.of(11, 0);
    private int reminderGapMinutes; // 0 = no second reminder
    private List<String> reminderRecipients = new ArrayList<>();

    // Long absence report
    private boolean reportOn;
    private int reportDays = DEFAULT_THRESHOLD_DAYS;
    private AutomationScheduleUtil.Frequency reportFrequency = AutomationScheduleUtil.Frequency.DAILY;
    private LocalTime reportTime = LocalTime.of(8, 0);
    private DayOfWeek reportDayOfWeek = DayOfWeek.MONDAY;
    private String reportDayOfMonth = "1"; // "1".."28" or "LAST"
    private List<String> reportRecipients = new ArrayList<>();

    /** True when the account can send: a sender, a server, and a saved password if the server needs a login. */
    public boolean isAccountReady() {
        return !mailSender.isBlank() && !mailHost.isBlank() && (!mailAuth || !mailPasswordEncrypted.isBlank());
    }

    public boolean hasPassword() {
        return !mailPasswordEncrypted.isBlank();
    }

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public String getMailSender() { return mailSender; }
    public void setMailSender(String v) { this.mailSender = v == null ? "" : v; }
    public String getMailPasswordEncrypted() { return mailPasswordEncrypted; }
    public void setMailPasswordEncrypted(String v) { this.mailPasswordEncrypted = v == null ? "" : v; }
    public String getMailFromName() { return mailFromName; }
    public void setMailFromName(String v) { this.mailFromName = v == null ? "" : v; }
    public String getMailHost() { return mailHost; }
    public void setMailHost(String v) { this.mailHost = v == null ? "" : v; }
    public int getMailPort() { return mailPort; }
    public void setMailPort(int v) { this.mailPort = v; }
    public String getMailSecurity() { return mailSecurity; }
    public void setMailSecurity(String v) { this.mailSecurity = v == null ? SECURITY_STARTTLS : v; }
    public boolean isMailAuth() { return mailAuth; }
    public void setMailAuth(boolean v) { this.mailAuth = v; }
    public int getMailTimeoutSeconds() { return mailTimeoutSeconds; }
    public void setMailTimeoutSeconds(int v) { this.mailTimeoutSeconds = v; }

    public boolean isReminderOn() { return reminderOn; }
    public void setReminderOn(boolean v) { this.reminderOn = v; }
    public LocalTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalTime v) { this.reminderTime = v; }
    public int getReminderGapMinutes() { return reminderGapMinutes; }
    public void setReminderGapMinutes(int v) { this.reminderGapMinutes = v; }
    public List<String> getReminderRecipients() { return reminderRecipients; }
    public void setReminderRecipients(List<String> v) { this.reminderRecipients = v == null ? new ArrayList<>() : v; }

    public boolean isReportOn() { return reportOn; }
    public void setReportOn(boolean v) { this.reportOn = v; }
    public int getReportDays() { return reportDays; }
    public void setReportDays(int v) { this.reportDays = v; }
    public AutomationScheduleUtil.Frequency getReportFrequency() { return reportFrequency; }
    public void setReportFrequency(AutomationScheduleUtil.Frequency v) { this.reportFrequency = v; }
    public LocalTime getReportTime() { return reportTime; }
    public void setReportTime(LocalTime v) { this.reportTime = v; }
    public DayOfWeek getReportDayOfWeek() { return reportDayOfWeek; }
    public void setReportDayOfWeek(DayOfWeek v) { this.reportDayOfWeek = v; }
    public String getReportDayOfMonth() { return reportDayOfMonth; }
    public void setReportDayOfMonth(String v) { this.reportDayOfMonth = v; }
    public List<String> getReportRecipients() { return reportRecipients; }
    public void setReportRecipients(List<String> v) { this.reportRecipients = v == null ? new ArrayList<>() : v; }
}
