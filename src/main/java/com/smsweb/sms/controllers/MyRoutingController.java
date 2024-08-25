package com.smsweb.sms.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyRoutingController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login"; // Redirect to the login page
    }
}
