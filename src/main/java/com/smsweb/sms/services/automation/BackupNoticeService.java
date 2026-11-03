package com.smsweb.sms.services.automation;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NEW service (feature: shared email account + backup notice). After a SCHEDULED database backup finishes, every
 * branch that switched on "Backup notice" gets a short plain email saying whether it worked. The email carries no
 * file and no student data, only the time, the size and the outcome. The backup file itself goes only to the shared
 * backup receiver, exactly as before.
 *
 * Safe by design: runs on its own thread, so a slow mail server never delays the backup or the scheduler; a failure
 * for one branch is logged and never affects another branch or the backup result; a failure notice is sent at most
 * once a day per branch even when the backup is retried.
 */
@Service
public class BackupNoticeService {

    private static final Logger log = LoggerFactory.getLogger(BackupNoticeService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /** What happened, in plain values (kept free of DbBackupService so the two classes never depend on each other). */
    public static final class Outcome {
        final boolean success;
        final boolean emailed;
        final String size;
        final LocalDateTime when;

        public Outcome(boolean success, boolean emailed, String size, LocalDateTime when) {
            this.success = success;
            this.emailed = emailed;
            this.size = size == null ? "" : size;
            this.when = when;
        }

        /** Reads the result map returned by DbBackupService (it holds either "error" or "success"). */
        public static Outcome from(Map<String, Object> result, LocalDateTime when) {
            boolean ok = result != null && !result.containsKey("error") && Boolean.TRUE.equals(result.get("success"));
            boolean emailed = ok && Boolean.TRUE.equals(result.get("emailed"));
            Object size = result == null ? null : result.get("size");
            return new Outcome(ok, emailed, size == null ? "" : String.valueOf(size), when);
        }
    }

    private final AutomationSettingsService settingsService;
    private final AutomationMailService mailService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-notice");
        t.setDaemon(true);
        return t;
    });

    public BackupNoticeService(AutomationSettingsService settingsService, AutomationMailService mailService) {
        this.settingsService = settingsService;
        this.mailService = mailService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Queues the notices and returns at once. Never throws. */
    public void notifyBranches(Outcome outcome) {
        try {
            executor.submit(() -> {
                try {
                    sendAll(outcome);
                } catch (Exception e) {
                    log.error("Backup notices failed", e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not queue the backup notices: {}", e.getMessage());
        }
    }

    /** Sends to every branch that has the notice on. One branch failing never stops the others. */
    void sendAll(Outcome outcome) {
        Map<String, String> cfg = settingsService.snapshot();
        for (Long schoolId : branchesWithNotice(cfg)) {
            try {
                sendOne(cfg, schoolId, outcome);
            } catch (Exception e) {
                log.error("Backup notice for school {} failed: {}", schoolId, e.getMessage());
            }
        }
    }

    private void sendOne(Map<String, String> cfg, Long schoolId, Outcome outcome) throws Exception {
        AutomationSettings s = settingsService.fromSnapshot(cfg, schoolId);
        List<String> to = s.getBackupNoticeRecipients();
        if (!s.isBackupNoticeOn() || to.isEmpty() || !s.isAccountReady()) {
            return;
        }
        LocalDate day = outcome.when.toLocalDate();
        if (!outcome.success && settingsService.isDone(cfg, AutomationSettingsService.JOB_BACKUP_NOTICE_FAIL, schoolId, day)) {
            return; // a failure notice already went out today; retries stay quiet
        }
        mailService.send(s, to, subject(outcome), body(outcome), false);
        settingsService.markSent(AutomationSettingsService.JOB_BACKUP_NOTICE, schoolId);
        if (!outcome.success) {
            settingsService.markDone(AutomationSettingsService.JOB_BACKUP_NOTICE_FAIL, schoolId, day);
        }
    }

    static String subject(Outcome o) {
        return o.success ? "Database backup completed" : "Database backup FAILED";
    }

    static String body(Outcome o) {
        StringBuilder b = new StringBuilder();
        if (o.success) {
            b.append("The scheduled database backup finished successfully.\n\n");
            b.append("Time: ").append(o.when.format(WHEN)).append('\n');
            if (!o.size.isBlank()) {
                b.append("Size: ").append(o.size).append('\n');
            }
            b.append(o.emailed
                    ? "The backup file was emailed to the main backup receiver.\n"
                    : "The backup file is saved on the server. It could not be emailed or was too large to attach.\n");
        } else {
            b.append("The scheduled database backup FAILED.\n\n");
            b.append("Time: ").append(o.when.format(WHEN)).append('\n');
            b.append("The system will try again shortly. If you keep getting this notice, please ask your system administrator to check the server.\n");
        }
        b.append("\nThis is only a notice. No backup file or student data is attached.");
        return b.toString();
    }

    /** Branches that switched the notice on (found from the config keys, no school query needed). */
    static Set<Long> branchesWithNotice(Map<String, String> cfg) {
        Set<Long> ids = new LinkedHashSet<>();
        String prefix = AutomationSettingsService.K_BACKUP_NOTICE_ON;
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            if (!e.getKey().startsWith(prefix) || !"true".equalsIgnoreCase(e.getValue())) {
                continue;
            }
            try {
                ids.add(Long.parseLong(e.getKey().substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // not one of our keys
            }
        }
        return ids;
    }
}
