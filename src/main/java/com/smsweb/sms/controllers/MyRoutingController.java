package com.smsweb.sms.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
public class MyRoutingController {
    private static final Logger log = LoggerFactory.getLogger(MyRoutingController.class);


    @GetMapping("/")
    public String home() {
        log.info("Inside home");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getPrincipal().equals("anonymousUser")) {
            return "redirect:/dashboard";
        }

        return "redirect:/login";
    }
}
