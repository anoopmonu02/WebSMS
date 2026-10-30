package com.smsweb.sms.services.automation;

import com.smsweb.sms.services.admin.DbBackupService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One scheduler tick per minute that drives the Stage 1 automation: attendance reminders, the absence
 * report, the second daily backup and the missed-backup catch-up.
 *
 * Cheap by design: one read of the small system_config table per tick, and nothing else unless a job
 * is actually due. "Done for today" marks live in system_config, so a restart never repeats a job and a
 * job that was missed (server down) still runs once, as long as it is within the grace window.
 * Failed sends are retried a few times, a few minutes apart, then dropped for the day.
 */
@Component
public class AutomationJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AutomationJobRunner.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_GAP_MILLIS = 5L * 60 * 1000;
    private static final long BACKUP_RETRY_GAP_MILLIS = 15L * 60 * 1000;

    private final AutomationSettingsService settingsService;
    private final AttendanceReminderService reminderService;
    private final AbsenceReportService reportService;
    private final DbBackupService dbBackupService;

    /** key to {attempts, lastAttemptMillis}. Keys carry the date (or slot), so old entries are simply never used again. */
    private final Map<String, long[]> attempts = new ConcurrentHashMap<>();
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auto-backup");
        t.setDaemon(true);
        return t;
    });
    private volatile LocalDateTime backupSatisfiedSlot;

    public AutomationJobRunner(AutomationSettingsService settingsService,
                               AttendanceReminderService reminderService,
                               AbsenceReportService reportService,
                               DbBackupService dbBackupService) {
        this.settingsService = settingsService;
        this.reminderService = reminderService;
        this.reportService = reportService;
        this.dbBackupService = dbBackupService;
    }

    @PreDestroy
    void shutdown() {
        backupExecutor.shutdownNow();
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        try {
            runTick(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Automation tick failed", e);
        }
    }

    void runTick(LocalDateTime now) {
        Map<String, String> cfg = settingsService.snapshot();
        for (Long schoolId : enabledSchools(cfg)) {
            try {
                runSchool(cfg, schoolId, now);
            } catch (Exception e) {
                log.error("Automation jobs failed for school {}", schoolId, e);
            }
        }
        try {
            backupJobs(cfg, now);
        } catch (Exception e) {
            log.error("Backup catch-up check failed", e);
        }
    }

    // ── Per-school jobs ─────────────────────────────────────────────────────────────────────────

    private void runSchool(Map<String, String> cfg, Long schoolId, LocalDateTime now) {
        AutomationSettings s = settingsService.fromSnapshot(cfg, schoolId);
        if (!s.isAccountReady()) {
            return;
        }
        LocalDate today = now.toLocalDate();

        if (s.isReminderOn() && !s.getReminderRecipients().isEmpty()) {
            if (due(cfg, AutomationSettingsService.JOB_REMINDER_1, schoolId, today, now, s.getReminderTime(), 0)) {
                attempt(AutomationSettingsService.JOB_REMINDER_1, schoolId, today,
                        () -> reminderService.checkAndNotify(s, today, false));
            }
            if (s.getReminderGapMinutes() > 0
                    && due(cfg, AutomationSettingsService.JOB_REMINDER_2, schoolId, today, now, s.getReminderTime(), s.getReminderGapMinutes())) {
                attempt(AutomationSettingsService.JOB_REMINDER_2, schoolId, today,
                        () -> reminderService.checkAndNotify(s, today, true));
            }
        }
        if (s.isReportOn() && !s.getReportRecipients().isEmpty()
                && AutomationScheduleUtil.isReportDay(today, s.getReportFrequency(), s.getReportDayOfWeek(), s.getReportDayOfMonth())
                && due(cfg, AutomationSettingsService.JOB_REPORT, schoolId, today, now, s.getReportTime(), 0)) {
            attempt(AutomationSettingsService.JOB_REPORT, schoolId, today, () -> {
                log.info("Absence report for school {}: {}", schoolId, reportService.sendReport(s));
                return true;
            });
        }
    }

    private boolean due(Map<String, String> cfg, String job, Long schoolId, LocalDate today, LocalDateTime now,
                        LocalTime target, int offset) {
        return AutomationScheduleUtil.isDueNow(now, target, offset, AutomationScheduleUtil.GRACE_MINUTES)
                && !settingsService.isDone(cfg, job, schoolId, today);
    }

    private interface Job {
        boolean run() throws Exception;
    }

    /** Runs a job unless its retry budget for today is used up; a finished job is marked done for the day. */
    private void attempt(String job, Long schoolId, LocalDate today, Job work) {
        String key = job + "_" + schoolId + "_" + today;
        if (!allowAttempt(key, RETRY_GAP_MILLIS)) {
            return;
        }
        try {
            if (work.run()) {
                settingsService.markDone(job, schoolId, today);
            }
        } catch (Exception e) {
            log.error("Automation job {} failed for school {} (will retry shortly): {}", job, schoolId, e.getMessage());
        }
    }

    /** Counts this try and says whether it may go ahead (max attempts, minimum gap between them). */
    private boolean allowAttempt(String key, long gapMillis) {
        long nowMillis = System.currentTimeMillis();
        long[] state = attempts.computeIfAbsent(key, k -> new long[]{0, 0});
        synchronized (state) {
            if (state[0] >= MAX_ATTEMPTS || (state[0] > 0 && nowMillis - state[1] < gapMillis)) {
                return false;
            }
            state[0]++;
            state[1] = nowMillis;
            return true;
        }
    }

    /** Schools that have at least one of the two features switched on (found from config keys, no school query needed). */
    static Set<Long> enabledSchools(Map<String, String> cfg) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            String name = e.getKey();
            String prefix = name.startsWith(AutomationSettingsService.K_REMINDER_ON) ? AutomationSettingsService.K_REMINDER_ON
                    : name.startsWith(AutomationSettingsService.K_REPORT_ON) ? AutomationSettingsService.K_REPORT_ON : null;
            if (prefix == null || !"true".equalsIgnoreCase(e.getValue())) {
                continue;
            }
            try {
                ids.add(Long.parseLong(name.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // not one of our keys
            }
        }
        return ids;
    }

    // ── Backup: second daily slot + missed-backup catch-up ──────────────────────────────────────

    /**
     * Runs a backup when the most recent expected slot (slot 1 from the existing cron, slot 2 from
     * DB_BACKUP_TIME_2) has no newer backup file. Slot 1 gets a few minutes of head start so the normal
     * cron trigger does its own run first. Works only when a backup schedule has been saved.
     */
    private void backupJobs(Map<String, String> cfg, LocalDateTime now) {
        String cronValue = cfg.get(DbBackupService.CONFIG_CRON);
        if (cronValue == null || cronValue.isBlank()) {
            return;
        }
        AutomationScheduleUtil.BackupCron cron = AutomationScheduleUtil.parseBackupCron(cronValue);
        if (cron == null) {
            return;
        }
        LocalTime second = AutomationScheduleUtil.parseTime(cfg.get(BackupScheduleService.CONFIG_TIME_2));
        List<LocalTime> times = new ArrayList<>();
        times.add(cron.time);
        if (second != null) {
            times.add(second);
        }
        LocalDateTime slot = AutomationScheduleUtil.lastExpectedBackupSlot(now, cron.weekly, cron.dayOfWeek, times);
        if (slot == null || slot.equals(backupSatisfiedSlot)) {
            return;
        }
        boolean firstSlot = slot.toLocalTime().equals(cron.time);
        int delay = firstSlot ? AutomationScheduleUtil.BACKUP_CATCHUP_DELAY_MINUTES : 0;
        if (now.isBefore(slot.plusMinutes(delay)) || dbBackupService.isBackupRunning()) {
            return;
        }
        long slotMillis = slot.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (dbBackupService.latestBackupMillis() >= slotMillis) {
            backupSatisfiedSlot = slot;
            return;
        }
        if (!allowAttempt("BACKUP_" + slot, BACKUP_RETRY_GAP_MILLIS)) {
            return;
        }
        log.info("Running backup for slot {} (no backup file newer than it was found)", slot);
        backupExecutor.submit(() -> {
            try {
                Map<String, Object> result = dbBackupService.runBackupNow();
                if (result.containsKey("error")) {
                    log.error("Backup for slot {} failed: {}", slot, result.get("error"));
                } else {
                    log.info("Backup for slot {} finished: {}", slot, result.get("message"));
                }
            } catch (Exception e) {
                log.error("Backup for slot {} failed", slot, e);
            }
        });
    }
}
