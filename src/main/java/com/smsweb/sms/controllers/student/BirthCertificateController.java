package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.BirthCertificateStudentDto;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.student.BirthCertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reports > Student Report > Birth Certificate.
 *
 * Search a single active student and print a bilingual (English + regional
 * language) birth certificate. Read-only — nothing is ever saved here; the
 * data comes straight from `students` + `student_regional_detail`.
 */
@Controller
@RequestMapping("/student/birth-certificate")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT')")
public class BirthCertificateController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(BirthCertificateController.class);

    private final BirthCertificateService birthCertificateService;

    public BirthCertificateController(BirthCertificateService birthCertificateService) {
        this.birthCertificateService = birthCertificateService;
    }

    @CheckAccess(screen = "STUDENT_BIRTH_CERTIFICATE", type = AccessType.VIEW)
    @GetMapping
    public String showPage(Model model) {
        log.info("Inside showPage");
        model.addAttribute("page", "plain");
        return "reports/birth-certificate";
    }

    /** Live search (name / father name / mother name / SR no) — same behaviour app-wide. */
    @CheckAccess(screen = "STUDENT_BIRTH_CERTIFICATE", type = AccessType.VIEW)
    @GetMapping("/search-student/{query}")
    @ResponseBody
    public ResponseEntity<?> searchStudent(@PathVariable("query") String query,
                                           @RequestParam(defaultValue = "0") int page,
                                           Model model) {
        log.info("Inside searchStudent");
        try {
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No school/session selected."));
            }
            List<BirthCertificateStudentDto> results =
                    birthCertificateService.searchStudents(query, academicYear.getId(), school.getId(), page);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Birth certificate student search failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }
}
