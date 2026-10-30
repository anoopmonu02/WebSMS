package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.admin.DbBackupService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and saves the Database Backup schedule shown in App Config. Slot 1 is the existing
 * DB_BACKUP_SCHEDULE cron (so the existing trigger keeps working untouched); an optional second daily
 * time is stored as DB_BACKUP_TIME_2 ("HH:mm") and run by the automation tick. Daily or weekly only, at
 * most 2 times a day, at least 10 minutes apart.
 *
 * The backup email is sent from the Email Account of the school whose admin saved the schedule
 * (DB_BACKUP_MAIL_SCHOOL_ID) to DB_BACKUP_RECEIVER (empty = the sending account itself).
 */
@Service
public class BackupScheduleService {

    public static final String CONFIG_TIME_2 = "DB_BACKUP_TIME_2";

    private final SystemConfigRepository systemConfigRepository;
    private final AutomationSettingsService settingsService;

    public BackupScheduleService(SystemConfigRepository systemConfigRepository, AutomationSettingsService settingsService) {
        this.systemConfigRepository = systemConfigRepository;
        this.settingsService = settingsService;
    }

    /** @param schoolId the logged-in school, used to show whether its email account is ready (may be null) */
    public Map<String, Object> get(Long schoolId) {
        String cron = value(DbBackupService.CONFIG_CRON).orElse(DbBackupService.DEFAULT_CRON);
        AutomationScheduleUtil.BackupCron parsed = AutomationScheduleUtil.parseBackupCron(cron);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean weekly = parsed == null || parsed.weekly;
        m.put("frequency", weekly ? "WEEKLY" : "DAILY");
        m.put("dayOfWeek", parsed != null && parsed.dayOfWeek != null
                ? AutomationScheduleUtil.dayOfWeekCode(parsed.dayOfWeek) : "SUN");
        m.put("time", parsed != null ? parsed.time.toString() : "02:00");
        String t2 = value(BackupScheduleService.CONFIG_TIME_2).orElse("");
        m.put("time2", AutomationScheduleUtil.isValidTime(t2) ? t2 : "");
        m.put("receivers", value(DbBackupService.CONFIG_RECEIVER).orElse(""));

        // Which email account sends the backup: the chosen school's, else the school of this login if nothing is chosen yet.
        Long accountSchool = parseLong(value(DbBackupService.CONFIG_MAIL_SCHOOL_ID).orElse(null));
        if (accountSchool == null) {
            accountSchool = schoolId;
        }
        AutomationSettings account = accountSchool == null ? null : settingsService.load(accountSchool);
        boolean ready = account != null && account.isAccountReady();
        m.put("accountReady", ready);
        m.put("accountSender", ready ? account.getMailSender() : "");
        m.put("legacyEmail", value(DbBackupService.CONFIG_EMAIL_TO).orElse(""));
        return m;
    }

    /** @return null on success, otherwise a message for the admin. Nothing is saved when validation fails. */
    public String save(Long schoolId, String frequency, String dayOfWeek, String time, String time2, String receivers) {
        if (schoolId == null) {
            return "No school is linked to your login, so the backup email account cannot be chosen.";
        }
        boolean weekly;
        if ("DAILY".equalsIgnoreCase(frequency)) {
            weekly = false;
        } else if ("WEEKLY".equalsIgnoreCase(frequency)) {
            weekly = true;
        } else {
            return "Please choose Daily or Weekly.";
        }
        DayOfWeek dow = null;
        if (weekly) {
            dow = AutomationScheduleUtil.dayOfWeekFromCode(dayOfWeek);
            if (dow == null) {
                return "Please choose the day of the week.";
            }
        }
        if (!AutomationScheduleUtil.isValidTime(time)) {
            return "Please pick a valid backup time.";
        }
        boolean hasSecond = time2 != null && !time2.isBlank();
        if (hasSecond) {
            if (!AutomationScheduleUtil.isValidTime(time2)) {
                return "Please pick a valid second backup time.";
            }
            if (AutomationScheduleUtil.minutesApart(AutomationScheduleUtil.parseTime(time), AutomationScheduleUtil.parseTime(time2))
                    < AutomationScheduleUtil.MIN_BACKUP_GAP_MINUTES) {
                return "The two backup times must be at least " + AutomationScheduleUtil.MIN_BACKUP_GAP_MINUTES + " minutes apart.";
            }
        }
        String receiversError = AutomationSettingsService.validateRecipients(receivers);
        if (receiversError != null) {
            return receiversError;
        }

        LocalTime t = AutomationScheduleUtil.parseTime(time);
        String cron = "0 " + t.getMinute() + " " + t.getHour() + " * * " + (weekly ? AutomationScheduleUtil.dayOfWeekCode(dow) : "*");
        upsert(DbBackupService.CONFIG_CRON, cron,
                "Cron expression for the automatic database backup job. Set from App Config - Database Backup.");
        if (hasSecond) {
            upsert(BackupScheduleService.CONFIG_TIME_2, time2.trim(), "Optional second daily database backup time (HH:mm). Set from App Config - Database Backup.");
        } else {
            systemConfigRepository.findByConfigName(BackupScheduleService.CONFIG_TIME_2).ifPresent(systemConfigRepository::delete);
        }
        upsert(DbBackupService.CONFIG_RECEIVER, String.join(", ", AutomationSettingsService.splitRecipients(receivers)),
                "Receiver emails of the database backup (empty = the sending account itself). Set from App Config - Database Backup.");
        upsert(DbBackupService.CONFIG_MAIL_SCHOOL_ID, String.valueOf(schoolId),
                "School whose Email Account sends the database backup email.");
        return null;
    }

    private Optional<String> value(String key) {
        return systemConfigRepository.findByConfigName(key)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank());
    }

    private static Long parseLong(String v) {
        try {
            return v == null ? null : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void upsert(String key, String value, String description) {
        SystemConfig c = systemConfigRepository.findByConfigName(key).orElseGet(() -> {
            SystemConfig n = new SystemConfig();
            n.setConfigName(key);
            return n;
        });
        c.setConfigValue(value);
        c.setDescription(description);
        systemConfigRepository.save(c);
    }
}
