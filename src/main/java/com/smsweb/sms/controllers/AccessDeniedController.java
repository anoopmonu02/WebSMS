package com.smsweb.sms.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
public class AccessDeniedController {
    private static final Logger log = LoggerFactory.getLogger(AccessDeniedController.class);


    @GetMapping("/access-denied")
    public String accessDenied() {
        log.info("Inside accessDenied");
        return "access-denied";  // create access-denied.html
    }
}
