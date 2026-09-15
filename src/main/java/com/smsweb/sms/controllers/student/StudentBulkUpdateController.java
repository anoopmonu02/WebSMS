package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.student.StudentFieldGroupRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * NEW, isolated page controller for "Update Student Details (Group-wise)" —
 * does not modify StudentController or any existing controller.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentBulkUpdateController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentBulkUpdateController.class);

    @Autowired
    private DropdownService dropdownService;

    @CheckAccess(screen = "STUDENT_UPDATE_DETAILS", type = AccessType.VIEW)
    @GetMapping("/update-student-details")
    public String updateStudentDetailsPage(Model model) {
        log.info("Inside updateStudentDetailsPage");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("fieldGroups", StudentFieldGroupRegistry.getAllGroups());
        model.addAttribute("categories", dropdownService.getCategories());
        model.addAttribute("casts", dropdownService.getCasts());
        model.addAttribute("banks", dropdownService.getBanks());
        model.addAttribute("provinces", dropdownService.getProvinces());
        model.addAttribute("bloodGroups", dropdownService.getBloodGroups());
        model.addAttribute("bodyTypes", dropdownService.getBodyTypes());
        model.addAttribute("religions", dropdownService.getReligions());
        model.addAttribute("qualifications", dropdownService.getQualifications());
        return "student/update-student-details";
    }
}
