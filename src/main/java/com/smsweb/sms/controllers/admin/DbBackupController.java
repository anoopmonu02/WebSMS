package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.admin.DbBackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW controller (feature: Database Backup). Lets ROLE_ADMIN/ROLE_SUPERADMIN
 * configure the automatic backup schedule + recipient/sender email, trigger an
 * immediate backup, and manage (list/delete) existing backups.
 *
 * Hard-locked with @PreAuthorize rather than the flexible @CheckAccess screen-
 * permission system — same precedent as the Birthday Notification Settings and
 * Mobile Sessions Cleanup screens (small system-level admin utilities, not
 * business-data CRUD), and deliberately tighter than that precedent (ROLE_STAFF
 * excluded here) since a full database dump is far more sensitive than a
 * notification send-time.
 *
 * GET  /admin/db-backup           - schedule form + existing backups list
 * POST /admin/db-backup/schedule  - save schedule + email settings
 * POST /admin/db-backup/run-now   - trigger an immediate backup (AJAX)
 * GET  /admin/db-backup/list      - refresh the backups list (AJAX)
 * POST /admin/db-backup/delete    - delete one backup's .sql + .zip (AJAX)
 */
@Controller
@RequestMapping("/admin/db-backup")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class DbBackupController {

    private static final Logger log = LoggerFactory.getLogger(DbBackupController.class);

    private final SystemConfigRepository systemConfigRepository;
    private final DbBackupService dbBackupService;

    public DbBackupController(SystemConfigRepository systemConfigRepository, DbBackupService dbBackupService) {
        this.systemConfigRepository = systemConfigRepository;
        this.dbBackupService = dbBackupService;
    }

    @GetMapping
    public String view(Model model) {
        log.info("Inside db backup settings page");
        String cron = systemConfigRepository.findByConfigName(DbBackupService.CONFIG_CRON)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(DbBackupService.DEFAULT_CRON);
        Map<String, String> parsed = parseCron(cron);

        model.addAttribute("frequency", parsed.get("frequency"));
        model.addAttribute("dayOfWeek", parsed.get("dayOfWeek"));
        model.addAttribute("time", parsed.get("time"));
        model.addAttribute("emailTo", systemConfigRepository.findByConfigName(DbBackupService.CONFIG_EMAIL_TO)
                .map(SystemConfig::getConfigValue).orElse(""));
        model.addAttribute("hasPassword", systemConfigRepository.findByConfigName(DbBackupService.CONFIG_EMAIL_PASSWORD)
                .map(c -> c.getConfigValue() != null && !c.getConfigValue().isBlank())
                .orElse(false));
        model.addAttribute("backups", dbBackupService.listBackups());
        return "admin/dbBackup";
    }

    @PostMapping("/schedule")
    public String saveSchedule(@RequestParam String frequency,
                                @RequestParam(required = false) String dayOfWeek,
                                @RequestParam String time,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                RedirectAttributes redirectAttributes) {
        log.info("Inside saveSchedule - frequency={}, dayOfWeek={}, time={}", frequency, dayOfWeek, time);

        if (time == null || !time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            redirectAttributes.addFlashAttribute("error", "Please pick a valid time.");
            return "redirect:/admin/db-backup";
        }
        if (email == null || email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid email address.");
            return "redirect:/admin/db-backup";
        }

        String cron = buildCron(frequency, dayOfWeek, time);
        saveConfig(DbBackupService.CONFIG_CRON, cron,
                "Cron expression for the automatic database backup job. Set from the Database Backup admin screen.");
        saveConfig(DbBackupService.CONFIG_EMAIL_TO, email.trim(),
                "Email address the database backup zip is sent to (also used as the SMTP sender account).");

        // Blank password on save = "leave the existing one unchanged" (this field
        // is never pre-filled for display, so an admin editing just the schedule
        // shouldn't be forced to re-enter the passkey every time).
        if (password != null && !password.isBlank()) {
            saveConfig(DbBackupService.CONFIG_EMAIL_PASSWORD, password,
                    "App password/passkey for the backup sender email account.");
        }

        redirectAttributes.addFlashAttribute("success", "Backup schedule saved. Takes effect from the next run onward.");
        return "redirect:/admin/db-backup";
    }

    @PostMapping("/run-now")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runNow() {
        log.info("Inside runNow (manual backup trigger)");
        Map<String, Object> result = dbBackupService.runBackupNow();
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(dbBackupService.listBackups());
    }

    @PostMapping("/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String filename) {
        log.info("Inside delete backup - filename={}", filename);
        Map<String, Object> result = new LinkedHashMap<>();
        boolean deleted = dbBackupService.deleteBackup(filename);
        if (deleted) {
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        result.put("error", "Could not delete backup — it may have already been removed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private void saveConfig(String key, String value, String description) {
        SystemConfig config = systemConfigRepository.findByConfigName(key).orElseGet(() -> {
            SystemConfig c = new SystemConfig();
            c.setConfigName(key);
            return c;
        });
        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);
    }

    /** "WEEKLY"/"MON"/"02:00" -> "0 0 2 * * MON". "DAILY""02:00" -> "0 0 2 * * *". */
    private String buildCron(String frequency, String dayOfWeek, String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if ("WEEKLY".equalsIgnoreCase(frequency)) {
            String dow = (dayOfWeek == null || dayOfWeek.isBlank()) ? "SUN" : dayOfWeek.toUpperCase();
            return "0 " + minute + " " + hour + " * * " + dow;
        }
        return "0 " + minute + " " + hour + " * * *";
    }

    /** Inverse of buildCron — falls back to a sane default if the stored value
     *  isn't in the expected shape (defensive — this table is also hand-editable). */
    private Map<String, String> parseCron(String cron) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String[] fields = cron.trim().split("\\s+");
            int minute = Integer.parseInt(fields[1]);
            int hour = Integer.parseInt(fields[2]);
            String dow = fields[5];
            map.put("time", String.format("%02d:%02d", hour, minute));
            if ("*".equals(dow) || "?".equals(dow)) {
                map.put("frequency", "DAILY");
                map.put("dayOfWeek", "SUN");
            } else {
                map.put("frequency", "WEEKLY");
                map.put("dayOfWeek", dow.toUpperCase());
            }
        } catch (Exception e) {
            log.warn("Could not parse cron '{}', showing default", cron, e);
            map.put("time", "02:00");
            map.put("frequency", "WEEKLY");
            map.put("dayOfWeek", "SUN");
        }
        return map;
    }
}
