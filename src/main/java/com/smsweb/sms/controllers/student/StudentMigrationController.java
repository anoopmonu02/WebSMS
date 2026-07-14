package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.admin.AcademicyearService;
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

/**
 * Brand-new controller for the "Migrate Student" feature (view rendering only).
 * Does not touch StudentController or any existing route.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentMigrationController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentMigrationController.class);

    private final DropdownService dropdownService;
    private final SchoolService schoolService;
    private final AcademicyearService academicyearService;

    @Autowired
    public StudentMigrationController(DropdownService dropdownService, SchoolService schoolService, AcademicyearService academicyearService) {
        this.dropdownService = dropdownService;
        this.schoolService = schoolService;
        this.academicyearService = academicyearService;
    }

    @CheckAccess(screen = "STUDENT_MIGRATE", type = AccessType.VIEW)
    @GetMapping("/migrate-student")
    public String migrateStudentForm(Model model) {
        log.info("Inside migrateStudentForm");
        School currentSchool = (School) model.getAttribute("school");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("schools", schoolService.getAllSchools());

        // Source academic year is locked (not user-selectable) to the session immediately
        // before the current school's latest session - same rule the old Grails
        // migrateStudent.gsp used ("id < max(id) order by id desc limit 1"), just scoped to
        // this school instead of globally. Needs at least 2 sessions on record to resolve.
        if (currentSchool != null) {
            model.addAttribute("currentSchoolId", currentSchool.getId());
            List<AcademicYear> schoolYearsDesc = academicyearService.getAllAcademiyears(currentSchool.getId());
            AcademicYear sourceAcademicYear = (schoolYearsDesc != null && schoolYearsDesc.size() >= 2)
                    ? schoolYearsDesc.get(1) : null;
            model.addAttribute("sourceAcademicYear", sourceAcademicYear);
        }
        return "student/migrate-student";
    }
}
