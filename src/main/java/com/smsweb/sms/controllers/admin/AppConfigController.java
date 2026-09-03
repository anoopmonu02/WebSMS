package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.services.admin.MaintenanceModeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * NEW controller (feature: App Config admin screen). Currently one tab ("Mobile App")
 * with a single switch — mobile API maintenance mode — plus its admin-editable message.
 * Built as a tabbed page deliberately so future global app-level toggles have a home here
 * instead of each needing its own new sidebar entry/screen.
 *
 * Deliberately Admin + SuperAdmin only (NOT ROLE_STAFF) per explicit product decision —
 * this is a platform-wide switch that can take the entire mobile app down for every family,
 * across every school, so it needs a tighter gate than the Staff-visible
 * BirthdayNotificationSettingsController precedent.
 *
 * Saves are instant AJAX (no page reload / no confirm dialog) — the switch and the message
 * box each POST independently as soon as they change.
 */
@Controller
@RequestMapping("/admin/app-config")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class AppConfigController {

    private static final Logger log = LoggerFactory.getLogger(AppConfigController.class);

    private static final int MAX_MESSAGE_LENGTH = 255; // system_config.config_value is VARCHAR(255)

    private final MaintenanceModeService maintenanceModeService;

    public AppConfigController(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    @GetMapping
    public String view(Model model) {
        log.info("Inside app config page");
        model.addAttribute("mobileAccessEnabled", maintenanceModeService.isMobileAccessEnabled());
        model.addAttribute("maintenanceMessage", maintenanceModeService.getMaintenanceMessage());
        return "admin/app-config";
    }

    @PostMapping("/mobile-access")
    @ResponseBody
    public ResponseEntity<?> setMobileAccess(@RequestParam("enabled") boolean enabled) {
        log.info("Inside set mobile access - enabled={}", enabled);
        try {
            maintenanceModeService.setMobileAccessEnabled(enabled);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "enabled", enabled,
                    "message", enabled
                            ? "Mobile app access enabled."
                            : "Mobile app access disabled — the mobile app is now showing the maintenance screen to every user."
            ));
        } catch (Exception e) {
            log.error("Failed to update mobile access flag", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Could not save — please try again."));
        }
    }

    @PostMapping("/mobile-message")
    @ResponseBody
    public ResponseEntity<?> setMobileMessage(@RequestParam("message") String message) {
        log.info("Inside set mobile maintenance message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Message can't be empty."));
        }
        if (message.trim().length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "Message is too long (max " + MAX_MESSAGE_LENGTH + " characters)."));
        }
        try {
            maintenanceModeService.setMaintenanceMessage(message.trim());
            return ResponseEntity.ok(Map.of("success", true, "message", "Maintenance message saved."));
        } catch (Exception e) {
            log.error("Failed to update maintenance message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Could not save — please try again."));
        }
    }
}
