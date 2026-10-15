package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.MaintenanceModeService;
import com.smsweb.sms.services.admin.SiblingDiscountConfigService;
import jakarta.servlet.http.HttpSession;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

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
    private final SiblingDiscountConfigService siblingDiscountConfigService;
    private final TcConfigService tcConfigService;

    public AppConfigController(MaintenanceModeService maintenanceModeService,
                               SiblingDiscountConfigService siblingDiscountConfigService, TcConfigService tcConfigService) {
        this.maintenanceModeService = maintenanceModeService;
        this.siblingDiscountConfigService = siblingDiscountConfigService;
        this.tcConfigService = tcConfigService;
    }

    @GetMapping
    public String view(Model model, HttpSession session) {
        log.info("Inside app config page");
        model.addAttribute("mobileAccessEnabled", maintenanceModeService.isMobileAccessEnabled());
        model.addAttribute("maintenanceMessage", maintenanceModeService.getMaintenanceMessage());

        // Discount tab - per-school setting. School comes from the session only (never from the request).
        // If the session has no school (e.g. a super admin not tied to a school) the tab shows a notice instead.
        School school = (School) session.getAttribute("school");
        model.addAttribute("discountSchoolAvailable", school != null);
        model.addAttribute("discountSchoolName", school != null ? school.getSchoolName() : "");
        model.addAttribute("siblingDiscountAllowed",
                school != null ? siblingDiscountConfigService.getAllowedPerGroup(school.getId())
                               : SiblingDiscountConfigService.DEFAULT_ALLOWED);
        model.addAttribute("siblingDiscountMin", SiblingDiscountConfigService.MIN_ALLOWED);
        model.addAttribute("siblingDiscountMax", SiblingDiscountConfigService.MAX_ALLOWED);
        addTcSettings(model, session);
        return "admin/app-config";
    }

    /**
     * Fills the "TC Settings" tab. Wrapped so that a problem here (for example the TC tables
     * not created yet on a database) can never break the Mobile App tab of this page.
     */
    private void addTcSettings(Model model, HttpSession session) {
        model.addAttribute("tcSchoolSelected", false);
        try {
            School school = (School) session.getAttribute("school");
            if (school == null) {
                return;
            }
            model.addAttribute("tcSchoolName", school.getSchoolName());
            model.addAttribute("tcEnabled", tcConfigService.isEnabled(school));
            model.addAttribute("tcFormatNames", String.join(", ", tcConfigService.formatNames(school)));
            model.addAttribute("tcReceiptPrefix", tcConfigService.receiptPrefix(school));
            model.addAttribute("tcReceiptPad", tcConfigService.receiptPad(school));
            model.addAttribute("tcReceiptStart", tcConfigService.receiptStart(school));
            model.addAttribute("tcReceiptStartLocked", tcConfigService.receiptStartLocked(school));
            model.addAttribute("tcSchoolSelected", true);
        } catch (Exception e) {
            log.warn("Could not load TC settings for the App Config page", e);
            model.addAttribute("tcSchoolSelected", false);
            model.addAttribute("tcLoadError", true);
        }
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

    @PostMapping("/discount/sibling-allowed")
    @ResponseBody
    public ResponseEntity<?> setSiblingDiscountAllowed(@RequestParam("value") String value, HttpSession session) {
        log.info("Inside set sibling discount allowed per group");
        // School is taken from the logged-in session only, so one school's admin can never write another school's setting.
        School school = (School) session.getAttribute("school");
        if (school == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "No school is linked to your login, so this setting can't be saved."));
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Please enter a whole number."));
        }
        if (parsed < SiblingDiscountConfigService.MIN_ALLOWED || parsed > SiblingDiscountConfigService.MAX_ALLOWED) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "Value must be between " + SiblingDiscountConfigService.MIN_ALLOWED
                            + " and " + SiblingDiscountConfigService.MAX_ALLOWED + "."));
        }
        try {
            siblingDiscountConfigService.setAllowedPerGroup(school.getId(), parsed);
            return ResponseEntity.ok(Map.of("success", true, "value", parsed,
                    "message", "Saved. Up to " + parsed + (parsed == 1 ? " student" : " students")
                            + " per sibling group can get the sibling discount."));
        } catch (Exception e) {
            log.error("Failed to update sibling discount allowed-per-group", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Could not save — please try again."));
        }
    }

    // ── TC Settings tab (per school, saved by AJAX like the Mobile App tab) ──────────────

    @PostMapping("/tc-settings/feature")
    @ResponseBody
    public ResponseEntity<?> setTcFeature(@RequestParam("enabled") boolean enabled, HttpSession session) {
        log.info("Inside set TC feature - enabled={}", enabled);
        return saveTcSetting(session, school -> {
            tcConfigService.setEnabled(school, enabled);
            return enabled
                    ? "Transfer Certificate feature enabled for this school."
                    : "Transfer Certificate feature disabled for this school.";
        });
    }

    @PostMapping("/tc-settings/formats")
    @ResponseBody
    public ResponseEntity<?> setTcFormats(@RequestParam("formats") String formats, HttpSession session) {
        log.info("Inside set TC format names");
        return saveTcSetting(session, school -> {
            tcConfigService.setFormatNames(school, Arrays.asList(formats.split(",")));
            return "TC format names saved.";
        });
    }

    @PostMapping("/tc-settings/receipt")
    @ResponseBody
    public ResponseEntity<?> setTcReceiptNumbering(@RequestParam("prefix") String prefix,
                                                   @RequestParam("pad") String pad,
                                                   @RequestParam("start") String start,
                                                   HttpSession session) {
        log.info("Inside set TC receipt numbering");
        return saveTcSetting(session, school -> {
            int padValue;
            long startValue;
            try {
                padValue = Integer.parseInt(pad.trim());
                startValue = Long.parseLong(start.trim());
            } catch (NumberFormatException e) {
                throw new TcValidationException("Padding and start number must be whole numbers.");
            }
            tcConfigService.setReceiptNumbering(school, prefix, padValue, startValue);
            return "Receipt numbering saved.";
        });
    }

    /** Runs one TC setting save for the school in the session and turns the outcome into JSON. */
    private ResponseEntity<?> saveTcSetting(HttpSession session, Function<School, String> action) {
        School school = (School) session.getAttribute("school");
        if (school == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No school is selected for this login."));
        }
        try {
            String message = action.apply(school);
            return ResponseEntity.ok(Map.of("success", true, "message", message));
        } catch (TcValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to save TC setting", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Could not save — please try again."));
        }
    }
}
