package com.smsweb.sms.controllers;


import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/login")
    public String login(HttpServletResponse response) {
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

        return "login";
    }

}
