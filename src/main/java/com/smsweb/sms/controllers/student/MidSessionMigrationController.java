package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.globalaccess.DropdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Brand-new controller for the "Mid Session Migration" feature (view rendering only).
 * Does not touch StudentController, StudentMigrationController, or any existing route.
 *
 * Deliberately restricted to ROLE_ADMIN/ROLE_SUPERADMIN only (unlike the year-end Migrate
 * Student page, which is also open to staff/accountant) - this moves a student's entire
 * record + carries forward pending dues to a different branch, and is the one place that can
 * unlock the "Mid Year Migration Discount" field on Fee Submission for that student.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class MidSessionMigrationController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(MidSessionMigrationController.class);

    private final DropdownService dropdownService;
    private final SchoolService schoolService;

    @Autowired
    public MidSessionMigrationController(DropdownService dropdownService, SchoolService schoolService) {
        this.dropdownService = dropdownService;
        this.schoolService = schoolService;
    }

    @CheckAccess(screen = "STUDENT_MIDSESSION_MIGRATE", type = AccessType.VIEW)
    @GetMapping("/mid-session-migration")
    public String midSessionMigrationForm(Model model) {
        log.info("Inside midSessionMigrationForm");
        School currentSchool = (School) model.getAttribute("school");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());

        List<School> allSchools = schoolService.getAllSchools();
        if (currentSchool != null && allSchools != null) {
            // Destination is always a different branch/school - the current one is never a
            // valid destination, so it's left out of the dropdown entirely rather than relying
            // only on the server-side validation in MidSessionMigrationService.
            allSchools = allSchools.stream()
                    .filter(s -> !s.getId().equals(currentSchool.getId()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("schools", allSchools);
        if (currentSchool != null) {
            model.addAttribute("currentSchoolId", currentSchool.getId());
        }
        model.addAttribute("noOtherSchools", allSchools == null || allSchools.isEmpty());

        return "student/mid-session-migration";
    }
}
