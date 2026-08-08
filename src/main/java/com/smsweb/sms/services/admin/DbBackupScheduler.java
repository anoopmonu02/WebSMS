package com.smsweb.sms.services.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NEW component (feature: Database Backup). Wired up as a trigger task by
 * DynamicSchedulingConfig (SchedulingConfigurer) rather than a plain @Scheduled
 * annotation, so the cron expression is re-read from system_config
 * (DbBackupService.CONFIG_CRON) on every run — a schedule saved from the Database
 * Backup admin screen takes effect from the very next firing, no restart needed.
 * Same mechanism already used for BirthdayNotificationScheduler.
 */
@Component
public class DbBackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DbBackupScheduler.class);

    @Autowired
    private DbBackupService dbBackupService;

    public void runScheduledBackup() {
        log.info("Scheduled database backup starting");
        Map<String, Object> result = dbBackupService.runBackupNow();
        if (result.containsKey("error")) {
            log.error("Scheduled database backup failed: {}", result.get("error"));
        } else {
            log.info("Scheduled database backup finished: {}", result.get("message"));
        }
    }
}
