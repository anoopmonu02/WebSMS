package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.admin.AcademicyearService;
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
 * Page controller for "Student Health Report" under Student Report.
 *
 * Filters are Academic Year (Session) + Medium + Health (Student.bodyType:
 * NORMAL / PERSON WITH A DISABILITY) - Grade/Section are intentionally not
 * filters here, since the report can span every grade/section in the
 * selected Medium; the table shows a per-row Grade-Section column instead.
 *
 * Role gate matches the sidebar's "Student Report" sub-group in base.html
 * (Admin / Super Admin / Teacher / Accountant) exactly, so the page and the
 * REST endpoint behind it (StudentHealthReportRestController) never
 * disagree about who can actually use this feature.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT')")
public class StudentHealthReportController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentHealthReportController.class);

    @Autowired
    private DropdownService dropdownService;

    @Autowired
    private AcademicyearService academicyearService;

    @CheckAccess(screen = "STUDENT_HEALTH_REPORT", type = AccessType.VIEW)
    @GetMapping("/student-health-report")
    public String studentHealthReportPage(Model model) {
        log.info("Inside studentHealthReportPage");
        School school = (School) model.getAttribute("school");
        // Same school-scoped lookup already used by the "Total Deposited Fee"
        // report's Academic Year dropdown (AcademicyearService.getAllAcademiyears
        // only returns years belonging to this school), so this session picker
        // can never be populated with another school's academic years.
        model.addAttribute("academicYears", academicyearService.getAllAcademiyears(school.getId()));
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("healthOptions", dropdownService.getBodyTypes());
        return "student/student-health-report";
    }
}
