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
 * NEW, isolated page controller for the "Student Health Report (Grade-wise)"
 * under Student Report — does not modify StudentController or any existing
 * controller.
 *
 * Role gate matches the sidebar's "Student Report" sub-group in base.html
 * (Admin / Super Admin / Teacher / Accountant) exactly, so the page and the
 * REST endpoints behind it (StudentHealthReportRestController) never
 * disagree about who can actually use this feature.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT')")
public class StudentHealthReportController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentHealthReportController.class);

    @Autowired
    private DropdownService dropdownService;

    @CheckAccess(screen = "STUDENT_HEALTH_REPORT", type = AccessType.VIEW)
    @GetMapping("/student-health-report")
    public String studentHealthReportPage(Model model) {
        log.info("Inside studentHealthReportPage");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        return "student/student-health-report";
    }
}
