package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.student.BirthdayNotificationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads and saves the daily time of the birthday notification job. One global value for the whole
 * installation, stored as a cron string in system_config under BirthdayNotificationScheduler.CONFIG_KEY
 * (DynamicSchedulingConfig re-reads it on every run). Shared by the old settings page and the
 * App Config "Birthday" tab so the rules live in one place.
 */
@Service
public class BirthdayNotificationSettingsService {

    private static final Logger log = LoggerFactory.getLogger(BirthdayNotificationSettingsService.class);
    private static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private final SystemConfigRepository systemConfigRepository;

    public BirthdayNotificationSettingsService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /** Current send time as HH:mm (the default time when nothing is saved or the saved value is unreadable). */
    public String currentTime() {
        String cron = systemConfigRepository.findByConfigName(BirthdayNotificationScheduler.CONFIG_KEY)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(BirthdayNotificationScheduler.DEFAULT_CRON);
        return cronToTime(cron);
    }

    public static boolean isValidTime(String time) {
        return time != null && time.matches(TIME_PATTERN);
    }

    /** Saves the time. Returns null on success, otherwise a message for the user. */
    public String saveTime(String time) {
        if (!isValidTime(time)) {
            return "Please pick a valid time.";
        }
        SystemConfig config = systemConfigRepository.findByConfigName(BirthdayNotificationScheduler.CONFIG_KEY)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigName(BirthdayNotificationScheduler.CONFIG_KEY);
                    return c;
                });
        config.setConfigValue(timeToCron(time));
        config.setDescription("Daily time the 'Happy Birthday' notification job runs, across all schools (24h HH:mm, stored as a cron expression). Set from the Birthday Notification Settings admin screen.");
        systemConfigRepository.save(config);
        return null;
    }

    /** "08:00" -> "0 0 8 * * *" */
    static String timeToCron(String time) {
        String[] parts = time.split(":");
        return "0 " + Integer.parseInt(parts[1]) + " " + Integer.parseInt(parts[0]) + " * * *";
    }

    /** "0 0 8 * * *" -> "08:00"; falls back to the default time when the stored value has an unexpected shape. */
    static String cronToTime(String cron) {
        try {
            String[] fields = cron.trim().split("\\s+");
            return String.format("%02d:%02d", Integer.parseInt(fields[2]), Integer.parseInt(fields[1]));
        } catch (Exception e) {
            log.warn("Could not parse cron '{}' back into HH:mm, showing default", cron, e);
            String[] fields = BirthdayNotificationScheduler.DEFAULT_CRON.split("\\s+");
            return String.format("%02d:%02d", Integer.parseInt(fields[2]), Integer.parseInt(fields[1]));
        }
    }
}
