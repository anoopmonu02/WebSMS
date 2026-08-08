package com.smsweb.sms.config;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.admin.DbBackupScheduler;
import com.smsweb.sms.services.admin.DbBackupService;
import com.smsweb.sms.services.student.BirthdayNotificationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * NEW config class (feature: admin-configurable birthday notification time).
 *
 * Registers BirthdayNotificationScheduler.runScheduledBirthdayNotifications() as a
 * trigger task instead of using a plain @Scheduled(cron = "...") annotation. The
 * difference matters here: @Scheduled reads its cron string exactly once, at
 * startup, from wherever the property placeholder points — there's no built-in way
 * to make it pick up a new value later without restarting the app. A Trigger, on
 * the other hand, gets asked to compute nextExecution() again every time the
 * previous run finishes, so if that computation re-reads the cron string from the
 * DB each time, a value saved via the admin UI (BirthdayNotificationSettingsController)
 * takes effect starting from the very next firing — no restart required.
 *
 * Coexists safely with the existing @Scheduled-annotated jobs elsewhere in the app
 * (e.g. MobileRefreshTokenCleanupScheduler) — implementing SchedulingConfigurer
 * doesn't disable annotation-based scheduling, it just adds this one job as an
 * extra trigger task on top, using Spring Boot's same default task scheduler since
 * no custom one is set here.
 */
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchedulingConfig.class);

    private final SystemConfigRepository systemConfigRepository;
    private final BirthdayNotificationScheduler birthdayNotificationScheduler;
    private final DbBackupScheduler dbBackupScheduler;

    public DynamicSchedulingConfig(SystemConfigRepository systemConfigRepository,
                                    BirthdayNotificationScheduler birthdayNotificationScheduler,
                                    DbBackupScheduler dbBackupScheduler) {
        this.systemConfigRepository = systemConfigRepository;
        this.birthdayNotificationScheduler = birthdayNotificationScheduler;
        this.dbBackupScheduler = dbBackupScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                birthdayNotificationScheduler::runScheduledBirthdayNotifications,
                triggerContext -> new CronTrigger(resolveBirthdayCron()).nextExecution(triggerContext)
        );
        registrar.addTriggerTask(
                dbBackupScheduler::runScheduledBackup,
                triggerContext -> new CronTrigger(resolveBackupCron()).nextExecution(triggerContext)
        );
    }

    /** Reads the admin-configured cron string from system_config (falls back to
     *  the original fixed 8:00 AM default if no row exists yet, or if whatever's
     *  stored there isn't a valid cron expression — a bad save should never be
     *  able to silently kill the whole job). */
    private String resolveBirthdayCron() {
        String cron = systemConfigRepository.findByConfigName(BirthdayNotificationScheduler.CONFIG_KEY)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(BirthdayNotificationScheduler.DEFAULT_CRON);
        try {
            new CronTrigger(cron); // throws IllegalArgumentException on a malformed expression
            return cron;
        } catch (IllegalArgumentException e) {
            log.error("Invalid birthday notification cron '{}' in system_config — falling back to default '{}'",
                    cron, BirthdayNotificationScheduler.DEFAULT_CRON);
            return BirthdayNotificationScheduler.DEFAULT_CRON;
        }
    }

    /** Same re-read-on-every-run pattern as resolveBirthdayCron(), for the
     *  Database Backup schedule (DB_BACKUP_SCHEDULE). */
    private String resolveBackupCron() {
        String cron = systemConfigRepository.findByConfigName(DbBackupService.CONFIG_CRON)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DbBackupService.DEFAULT_CRON);
        try {
            new CronTrigger(cron);
            return cron;
        } catch (IllegalArgumentException e) {
            log.error("Invalid DB backup cron '{}' in system_config — falling back to default '{}'",
                    cron, DbBackupService.DEFAULT_CRON);
            return DbBackupService.DEFAULT_CRON;
        }
    }
}
