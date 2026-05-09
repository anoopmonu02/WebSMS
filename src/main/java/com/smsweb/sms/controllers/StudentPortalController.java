package com.smsweb.sms.controllers;


import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student-portal")
@PreAuthorize("hasRole('ROLE_STUDENT')")   // entire controller locked to STUDENT only
public class StudentPortalController {

    @GetMapping("/home")
    public String studentHome(Model model) {
        return "student-portal/home";  // separate Thymeleaf page
    }
}