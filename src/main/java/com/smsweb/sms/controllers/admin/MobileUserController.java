package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.MobileUserRowDto;
import com.smsweb.sms.dto.MobileUserStatsDto;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW controller — Mobile Users admin screen (Admin Config > Mobile Users).
 * Global, not scoped to a school/session — one mobile number's FamilyAccount
 * can span branches, same as Family Migration and Mobile Sessions Cleanup.
 *
 * Gating: base role check stays ROLE_ADMIN/ROLE_SUPERADMIN/ROLE_STAFF via
 * @PreAuthorize (unchanged). On top of that, each endpoint now also goes
 * through the finer-grained @CheckAccess/AppScreen system (screen key
 * ADMIN_MOBILE_USERS) — ROLE_ADMIN/ROLE_SUPERADMIN bypass it as always
 * (see PermissionService.isSuperOrAdmin), but a ROLE_STAFF user only gets
 * in once someone explicitly grants ADMIN_MOBILE_USERS on the User
 * Permissions screen, same as every other gated admin screen. This used to
 * be a deliberate exception (matching Mobile Sessions Cleanup, which still
 * is one) — changed because Mobile Users was visible to every STAFF login
 * regardless of granted permissions.
 *
 * GET  /admin/mobile-users                     — page                         [VIEW]
 * GET  /admin/mobile-users/list?search=...     — stats + searchable row list  [VIEW]
 * GET  /admin/mobile-users/generate-password   — one random temp password (JSON, ungated — no data access)
 * POST /admin/mobile-users/{id}/reset-password — admin sets a new password    [EDIT]
 * POST /admin/mobile-users/{id}/force-logout   — revokes every active session [EDIT]
 */
@Controller
@RequestMapping("/admin/mobile-users")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_STAFF')")
public class MobileUserController {

    private static final Logger log = LoggerFactory.getLogger(MobileUserController.class);

    private final FamilyAccountService familyAccountService;

    public MobileUserController(FamilyAccountService familyAccountService) {
        this.familyAccountService = familyAccountService;
    }

    @CheckAccess(screen = "ADMIN_MOBILE_USERS", type = AccessType.VIEW)
    @GetMapping
    public String view(Model model) {
        log.info("Inside mobile users page");
        // Needed so base.html loads the DataTables/Buttons JS bundle (pagination
        // for this table is added client-side after the row data is fetched).
        model.addAttribute("page", "datatable");
        return "admin/mobileUsers";
    }

    @CheckAccess(screen = "ADMIN_MOBILE_USERS", type = AccessType.VIEW)
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> list(@RequestParam(required = false, defaultValue = "") String search) {
        log.info("Inside mobile users list - search={}", search);
        try {
            List<MobileUserRowDto> allRows = familyAccountService.getAllMobileUserRows();
            MobileUserStatsDto stats = familyAccountService.computeMobileUserStats(allRows);
            List<MobileUserRowDto> rows = familyAccountService.filterMobileUserRows(allRows, search);

            Map<String, Object> response = new HashMap<>();
            response.put("stats", stats);
            response.put("rows", rows);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to load mobile users list", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load mobile users: " + e.getMessage()));
        }
    }

    @GetMapping("/generate-password")
    @ResponseBody
    public ResponseEntity<?> generatePassword() {
        log.info("Inside generate temp password");
        return ResponseEntity.ok(Map.of("password", familyAccountService.generateTempPassword()));
    }

    @CheckAccess(screen = "ADMIN_MOBILE_USERS", type = AccessType.EDIT)
    @PostMapping("/{id}/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        log.info("Inside reset password - familyAccountId={}", id);
        try {
            String newPassword = payload != null ? payload.get("newPassword") : null;
            if (newPassword == null || newPassword.trim().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters."));
            }
            FamilyAccount account = familyAccountService.findById(id).orElse(null);
            if (account == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Family account not found."));
            }
            familyAccountService.adminResetPassword(account, newPassword.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Failed to reset password for familyAccountId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Reset failed: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "ADMIN_MOBILE_USERS", type = AccessType.EDIT)
    @PostMapping("/{id}/force-logout")
    @ResponseBody
    public ResponseEntity<?> forceLogout(@PathVariable Long id) {
        log.info("Inside force logout - familyAccountId={}", id);
        try {
            FamilyAccount account = familyAccountService.findById(id).orElse(null);
            if (account == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Family account not found."));
            }
            familyAccountService.forceLogoutFamily(account);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Failed to force logout familyAccountId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Force logout failed: " + e.getMessage()));
        }
    }
}
