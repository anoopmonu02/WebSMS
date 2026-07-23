package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.student.BirthdayNotificationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * NEW controller (feature: admin-configurable birthday notification time). Lets
 * ROLE_ADMIN/ROLE_SUPERADMIN pick the daily time BirthdayNotificationScheduler
 * fires at, stored as a cron string in the existing system_config table under
 * BirthdayNotificationScheduler.CONFIG_KEY. DynamicSchedulingConfig re-reads that
 * value on every run, so a save here takes effect from the next firing onward —
 * no server restart needed.
 *
 * One global time for the whole platform (not per-school) — matches how the job
 * itself already works: one cron fires once and loops through every school.
 *
 * Deliberately plain @PreAuthorize gating, not the finer-grained @CheckAccess/
 * AppScreen system — same precedent as Mobile Users / Mobile Sessions Cleanup /
 * Family Migration (small system-level admin utility screens, not business-data
 * CRUD screens), which avoids needing a new screen-permission row seeded in the DB.
 *
 * GET  /admin/birthday-notification-settings   — shows the current time
 * POST /admin/birthday-notification-settings   — saves a new time
 */
@Controller
@RequestMapping("/admin/birthday-notification-settings")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class BirthdayNotificationSettingsController {

    private static final Logger log = LoggerFactory.getLogger(BirthdayNotificationSettingsController.class);

    private final SystemConfigRepository systemConfigRepository;

    public BirthdayNotificationSettingsController(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @GetMapping
    public String view(Model model) {
        log.info("Inside birthday notification settings page");
        String cron = systemConfigRepository.findByConfigName(BirthdayNotificationScheduler.CONFIG_KEY)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(BirthdayNotificationScheduler.DEFAULT_CRON);
        model.addAttribute("currentTime", cronToTime(cron));
        return "admin/birthdayNotificationSettings";
    }

    @PostMapping
    public String save(@RequestParam("time") String time, RedirectAttributes redirectAttributes) {
        log.info("Inside save birthday notification time - time={}", time);
        if (time == null || !time.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            redirectAttributes.addFlashAttribute("error", "Please pick a valid time.");
            return "redirect:/admin/birthday-notification-settings";
        }

        String cron = timeToCron(time);
        SystemConfig config = systemConfigRepository.findByConfigName(BirthdayNotificationScheduler.CONFIG_KEY)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigName(BirthdayNotificationScheduler.CONFIG_KEY);
                    return c;
                });
        config.setConfigValue(cron);
        config.setDescription("Daily time the 'Happy Birthday' notification job runs, across all schools (24h HH:mm, stored as a cron expression). Set from the Birthday Notification Settings admin screen.");
        systemConfigRepository.save(config);

        redirectAttributes.addFlashAttribute("success",
                "Birthday notifications will now be sent daily at " + time + ". Takes effect from tomorrow's run onward.");
        return "redirect:/admin/birthday-notification-settings";
    }

    /** "08:00" -> "0 0 8 * * *" */
    private String timeToCron(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return "0 " + minute + " " + hour + " * * *";
    }

    /** "0 0 8 * * *" -> "08:00". Falls back to the default time's HH:mm if the
     *  stored value isn't in the expected 6-field second-precision cron shape
     *  (defensive — this table is also hand-editable via SQL). */
    private String cronToTime(String cron) {
        try {
            String[] fields = cron.trim().split("\\s+");
            int hour = Integer.parseInt(fields[2]);
            int minute = Integer.parseInt(fields[1]);
            return String.format("%02d:%02d", hour, minute);
        } catch (Exception e) {
            log.warn("Could not parse cron '{}' back into HH:mm, showing default", cron, e);
            String[] fields = BirthdayNotificationScheduler.DEFAULT_CRON.split("\\s+");
            return String.format("%02d:%02d", Integer.parseInt(fields[2]), Integer.parseInt(fields[1]));
        }
    }
}
