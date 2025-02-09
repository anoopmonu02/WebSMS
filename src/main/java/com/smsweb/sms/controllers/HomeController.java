package com.smsweb.sms.controllers;

import com.smsweb.sms.config.AcademicYearHolder;
import com.smsweb.sms.config.SchoolHolder;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Controller
public class HomeController {

    private final HttpSession session;
    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;
    private final AcademicYearHolder academicYearHolder;
    private final SchoolHolder schoolHolder;

    public HomeController(HttpSession httpSession, AcademicyearService academicyearService, SchoolService schoolService, AcademicYearHolder academicYearHolder, SchoolHolder schoolHolder) {
        this.session = httpSession;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.academicYearHolder = academicYearHolder;
        this.schoolHolder = schoolHolder;
    }

    @GetMapping("/dashboard")
    public String index(HttpSession session, Model model){
        School school = (School) session.getAttribute("school");
        /*if(session.getAttribute("activeAcademicYear")!=null && ("No active academic year found").equalsIgnoreCase(session.getAttribute("activeAcademicYear").toString())){
            //create new academicyear
            saveAcademicYearIfNotFound();
            session.setAttribute("activeAcademicYear",academicyearService.getCurrentAcademicYear(school.getId()));
        } else {
            AcademicYear academicYear = (AcademicYear) session.getAttribute("activeAcademicYear");
            System.out.println("academicYear--"+academicYear);
            model.addAttribute("academicYear", academicYear);
        }*/
        System.out.println("school--"+school);
        model.addAttribute("school", school);
        /*schoolHolder.setCurrentSchool(school);
        academicYearHolder.setCurrentAcademicYear(academicyearService.getCurrentAcademicYear(school.getId()));*/
        return "index";
    }

    public boolean isSuperAdmin(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName(); // Get logged-in username
            if(username.equalsIgnoreCase("super_admin")){
                return true;
            }
            /*// Get full UserDetails object if needed
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                // Access other user details if required
                if(userDetails.getUsername().equalsIgnoreCase("super_admin")){
                    return true;
                }
            }*/
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    private Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }



}
