package com.smsweb.sms.controllers;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final HttpSession session;

    public HomeController(HttpSession httpSession) {
        this.session = httpSession;
    }

    @GetMapping("/dashboard")
    public String index(HttpSession session, Model model){
        School school = (School) session.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) session.getAttribute("activeAcademicYear");
        System.out.println("school--"+school);
        System.out.println("academicYear--"+academicYear);
        model.addAttribute("school", school);
        model.addAttribute("academicYear", academicYear);
        return "index";
    }
}
