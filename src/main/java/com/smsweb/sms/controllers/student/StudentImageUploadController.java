package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.globalaccess.DropdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * NEW, isolated page controller for "Update Student Images (Group-wise)" -
 * does not modify StudentController, StudentBulkUpdateController, or any
 * existing controller. Same page shell/filters as "Update Student Details
 * (Group-wise)" minus the "Select Data" dropdown (this page only ever does
 * one thing: photo upload), and each row saves its own photo immediately -
 * there is no page-wide "Save Changes" button here.
 *
 * Role gate matches StudentBulkUpdateController (Update Student Details)
 * exactly, per the project owner's explicit choice, so an Admin who already
 * knows how to grant that page to a user grants this one the same way.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentImageUploadController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentImageUploadController.class);

    @Autowired
    private DropdownService dropdownService;

    @CheckAccess(screen = "STUDENT_UPDATE_IMAGES", type = AccessType.VIEW)
    @GetMapping("/update-student-images")
    public String updateStudentImagesPage(Model model) {
        log.info("Inside updateStudentImagesPage");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "student/update-student-images";
    }
}
