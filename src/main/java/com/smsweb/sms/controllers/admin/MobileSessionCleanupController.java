package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.services.mobile.MobileRefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * NEW controller (feature #10) — brand-new file, does not touch any existing
 * admin controller. Open to ROLE_ADMIN and ROLE_SUPERADMIN — matches the
 * sidebar menu entry (base.html already used hasAnyRole for this item), so
 * school-owner (ADMIN) accounts can trigger cleanup too, not just the
 * developer-level SUPERADMIN account.
 *
 * GET  /admin/mobile-sessions          — stats page (active / expired / revoked counts)
 * POST /admin/mobile-sessions/cleanup  — manually runs the same cleanup the
 *                                         scheduled daily job runs automatically
 *                                         (MobileRefreshTokenService.cleanupNow()).
 */
@Controller
@RequestMapping("/admin/mobile-sessions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class MobileSessionCleanupController {
    private static final Logger log = LoggerFactory.getLogger(MobileSessionCleanupController.class);

    private final MobileRefreshTokenService refreshTokenService;

    public MobileSessionCleanupController(MobileRefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping
    public String view(Model model) {
        log.info("Inside mobile session cleanup page");

        Map<String, Long> stats = refreshTokenService.getStats();
        model.addAttribute("active",  stats.getOrDefault("active", 0L));
        model.addAttribute("expired", stats.getOrDefault("expired", 0L));
        model.addAttribute("revoked", stats.getOrDefault("revoked", 0L));
        model.addAttribute("total",   stats.getOrDefault("total", 0L));
        model.addAttribute("graceDays", refreshTokenService.getCleanupGraceDays());

        return "admin/mobile-sessions";
    }

    @PostMapping("/cleanup")
    public String runCleanup(RedirectAttributes redirectAttributes) {
        log.info("Inside manual mobile session cleanup trigger");

        int deleted = refreshTokenService.cleanupNow();
        redirectAttributes.addFlashAttribute("success",
                "Cleanup complete — " + deleted + " old session record(s) deleted.");

        return "redirect:/admin/mobile-sessions";
    }
}
