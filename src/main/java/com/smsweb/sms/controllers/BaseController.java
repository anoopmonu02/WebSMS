package com.smsweb.sms.controllers;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;

public abstract class BaseController {
    @Autowired
    private AcademicyearService academicyearService;
    @Autowired
    private SchoolService schoolService;

    @Autowired
    private UserService userService;

    // This method will run before every request in the extending controllers
    @ModelAttribute("academicYear")
    public AcademicYear setAcademicYearInModel(HttpSession session) {
        AcademicYear academicYear = (AcademicYear) session.getAttribute("activeAcademicYear");
        if (academicYear == null) {
            academicYear = academicyearService.getCurrentAcademicYear();
            if (academicYear != null) {
                session.setAttribute("activeAcademicYear", academicYear);
            } else {
                // Handle the case when there's no academic year available (return a default or log a warning)
                if(isSuperAdminLoggedIn()){
                    return null;
                }
                throw new IllegalStateException("No active academic year found.");
            }
        }
        return academicYear;
    }


    @ModelAttribute("school")
    public School setSchoolInModel(HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Block students and anonymous users entirely
        boolean isStudent = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        if (isStudent || !auth.isAuthenticated()) return null;
        School school = (School) session.getAttribute("school");
        if (school == null) {
            school = schoolService.getAllSchoolByName("United Avadh Inter College").get(0);
            session.setAttribute("school", school);
        }
        return school;
    }

    private boolean isSuperAdminLoggedIn(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if(authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))){
                return true;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

}
