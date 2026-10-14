package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.student.SiblingReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fee Report > Sibling Report (session-wise list of sibling groups and who holds the Sibling Discount). */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT')")
public class SiblingReportController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(SiblingReportController.class);

    private final SiblingReportService siblingReportService;
    private final AcademicyearService academicyearService;

    public SiblingReportController(SiblingReportService siblingReportService, AcademicyearService academicyearService) {
        this.siblingReportService = siblingReportService;
        this.academicyearService = academicyearService;
    }

    @CheckAccess(screen = "SIBLING_DISCOUNT_REPORT", type = AccessType.VIEW)
    @GetMapping("/sibling-report")
    public String siblingReportPage(Model model) {
        School school = (School) model.getAttribute("school");
        List<AcademicYear> academicYears = academicyearService.getAllAcademiyears(school.getId());
        model.addAttribute("academicYears", academicYears);
        model.addAttribute("page", "datatable");
        return "student/sibling-report";
    }

    @CheckAccess(screen = "SIBLING_DISCOUNT_REPORT", type = AccessType.VIEW)
    @PostMapping("/sibling-report/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> siblingReportData(@RequestBody Map<String, String> requestBody, Model model) {
        Map<String, Object> response = new HashMap<>();
        try {
            School school = (School) model.getAttribute("school");
            Long academicYearId = Long.parseLong(requestBody.getOrDefault("academicYearId", "0"));
            // the selected session must belong to the logged-in school
            boolean allowed = academicyearService.getAllAcademiyears(school.getId()).stream()
                    .anyMatch(y -> y.getId().equals(academicYearId));
            if (!allowed) {
                response.put("error", "Invalid academic year selected.");
                return ResponseEntity.ok(response);
            }
            response.put("rows", siblingReportService.getSiblingReport(school.getId(), academicYearId));
        } catch (NumberFormatException e) {
            response.put("error", "Invalid academic year selected.");
        } catch (Exception e) {
            log.error("Error while loading sibling report", e);
            response.put("error", "Unable to load the report right now. Please try again.");
        }
        return ResponseEntity.ok(response);
    }
}
