package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.security.SecurityActivityService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only data for the "Security activity" card in the Notifications tab of App Config. */
@RestController
@RequestMapping("/admin/app-config")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class AppConfigSecurityController {

    private static final Logger log = LoggerFactory.getLogger(AppConfigSecurityController.class);

    private final SecurityActivityService activityService;

    public AppConfigSecurityController(SecurityActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/security-activity")
    public ResponseEntity<Map<String, Object>> activity(HttpSession session) {
        Object school = session.getAttribute("school");
        Long schoolId = school instanceof School ? ((School) school).getId() : null;
        if (schoolId == null) {
            return fail(HttpStatus.BAD_REQUEST, "No school is linked to your login.");
        }
        try {
            return ResponseEntity.ok(activityService.view(schoolId));
        } catch (Exception e) {
            log.error("Could not build the security activity list", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load the security activity.");
        }
    }

    private static ResponseEntity<Map<String, Object>> fail(HttpStatus status, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", error);
        return ResponseEntity.status(status).body(m);
    }
}
