package com.smsweb.sms.config.mobile;

import com.smsweb.sms.services.admin.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * NEW filter (feature: mobile-app maintenance mode). Registered ONLY in
 * WebSecurityConfig.mobileApiSecurityFilterChain (the /api/v1/** chain), before
 * JwtAuthenticationFilter — so it blocks every mobile request, including login, while
 * MOBILE_APP_ACCESS is DISABLED, without ever touching the web/Thymeleaf security chain.
 * The web app cannot regress from this change: the two chains are fully separate
 * (see WebSecurityConfig's class-level doc), and this filter is never added to Chain 2.
 *
 * Fail-open: any unexpected exception reading the cached flag lets the request through
 * rather than accidentally locking out the whole mobile app.
 */
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeFilter.class);

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceModeFilter(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean enabled;
        try {
            enabled = maintenanceModeService.isMobileAccessEnabled();
        } catch (Exception e) {
            log.warn("Maintenance-mode check failed, failing open (allowing request through)", e);
            enabled = true;
        }

        if (!enabled) {
            String message;
            try {
                message = maintenanceModeService.getMaintenanceMessage();
            } catch (Exception e) {
                message = MaintenanceModeService.DEFAULT_MESSAGE;
            }
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
            response.setContentType("application/json");
            String safeMessage = message.replace("\"", "\\\"").replace("\n", " ");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"" + safeMessage + "\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
