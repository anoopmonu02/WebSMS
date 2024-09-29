package com.smsweb.sms.controllers;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;

public abstract class BaseController {
    @Autowired
    private AcademicyearService academicyearService;
    @Autowired
    private SchoolService schoolService;

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
                throw new IllegalStateException("No active academic year found.");
            }
        }
        return academicYear;
    }


    @ModelAttribute("school")
    public School setSchoolInModel(HttpSession session) {
        School school = (School) session.getAttribute("school");
        if (school == null) {
            school = schoolService.getAllSchoolByName("United Avash Inter College").get(0);
            session.setAttribute("school", school);
        }
        return school;
    }
}
