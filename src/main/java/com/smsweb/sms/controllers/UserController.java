package com.smsweb.sms.controllers;


import com.smsweb.sms.services.security.ClientIpResolver;
import com.smsweb.sms.services.security.LoginThrottleService;
import com.smsweb.sms.services.security.ThrottleSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final LoginThrottleService throttle;
    private final ClientIpResolver clientIp;
    private final ThrottleSettings throttleSettings;

    public UserController(LoginThrottleService throttle, ClientIpResolver clientIp, ThrottleSettings throttleSettings) {
        this.throttle = throttle;
        this.clientIp = clientIp;
        this.throttleSettings = throttleSettings;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request, HttpServletResponse response, Model model) {
        log.info("Inside login");

        // Prevent browser from caching the login page.
        // A cached page has a stale _csrf token in its hidden field — when the
        // XSRF-TOKEN cookie is refreshed (e.g. after our CSRF fix), the old form
        // token won't match the new cookie → silent CSRF failure → "nothing happened".
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getPrincipal().equals("anonymousUser")) {
            return "redirect:/dashboard";
        }

        // Cloudflare Turnstile: shown only when switched on and this address (or the last attempt) had several wrong
        // passwords. The site key is public. Any failure here simply shows the normal page.
        try {
            if (throttle.captchaEnabled()
                    && (request.getParameter("captcha") != null || throttle.captchaRequiredForIp(clientIp.resolve(request)))) {
                model.addAttribute("captchaSiteKey", throttleSettings.turnstileSiteKey());
            }
        } catch (RuntimeException e) {
            log.warn("Could not decide whether to show the CAPTCHA: {}", e.getMessage());
        }
        return "login";
    }

}
