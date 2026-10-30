package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.services.admin.BirthdayNotificationSettingsService;
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

    private final BirthdayNotificationSettingsService settingsService;

    public BirthdayNotificationSettingsController(BirthdayNotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public String view(Model model) {
        log.info("Inside birthday notification settings page");
        model.addAttribute("currentTime", settingsService.currentTime());
        return "admin/birthdayNotificationSettings";
    }

    @PostMapping
    public String save(@RequestParam("time") String time, RedirectAttributes redirectAttributes) {
        log.info("Inside save birthday notification time - time={}", time);
        String error = settingsService.saveTime(time);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/admin/birthday-notification-settings";
        }
        redirectAttributes.addFlashAttribute("success",
                "Birthday notifications will now be sent daily at " + time + ". Takes effect from tomorrow's run onward.");
        return "redirect:/admin/birthday-notification-settings";
    }
}
