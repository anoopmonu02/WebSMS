package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.student.AbsentMonthlyReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student Report > "Absent Report (Monthly)". Separate from StudentController on purpose (new feature, no
 * edits to the big existing controller). Same roles as the other /student pages. The school and academic
 * year come from the session (BaseController), never from the request.
 */
@Controller
@RequestMapping("/student/absent-report-monthly")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class AbsentMonthlyReportController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AbsentMonthlyReportController.class);

    private final DropdownService dropdownService;
    private final AbsentMonthlyReportService reportService;

    public AbsentMonthlyReportController(DropdownService dropdownService, AbsentMonthlyReportService reportService) {
        this.dropdownService = dropdownService;
        this.reportService = reportService;
    }

    /** What the page sends. */
    public static class ReportRequest {
        public Long mediumId;
        public Long gradeId;
        public Long sectionId;
        public List<String> months;
        public Integer limit;
    }

    @CheckAccess(screen = "STUDENT_REPORT_ABSENT_MONTHLY", type = AccessType.VIEW)
    @GetMapping
    public String page(Model model) {
        log.info("Inside absent monthly report page");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        List<Map<String, String>> months = new ArrayList<>();
        Object ay = model.getAttribute("academicYear");
        if (ay instanceof AcademicYear) {
            for (YearMonth ym : AbsentMonthlyReportService.academicMonths((AcademicYear) ay)) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("key", AbsentMonthlyReportService.key(ym));
                m.put("label", AbsentMonthlyReportService.label(ym) + " " + ym.getYear());
                months.add(m);
            }
        }
        model.addAttribute("reportMonths", months);
        model.addAttribute("defaultLimit", AbsentMonthlyReportService.DEFAULT_LIMIT);
        model.addAttribute("page", "datatable");
        return "student/absent-report-monthly";
    }

    @CheckAccess(screen = "STUDENT_REPORT_ABSENT_MONTHLY", type = AccessType.VIEW)
    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> data(@RequestBody ReportRequest req, Model model) {
        log.info("Inside absent monthly report data");
        Object schoolAttr = model.getAttribute("school");
        Object yearAttr = model.getAttribute("academicYear");
        if (!(schoolAttr instanceof School) || !(yearAttr instanceof AcademicYear)) {
            return error(HttpStatus.BAD_REQUEST, "No school or academic year is active for your login.");
        }
        if (req == null || req.mediumId == null || req.gradeId == null || req.sectionId == null) {
            return error(HttpStatus.BAD_REQUEST, "Medium, Grade and Section are required.");
        }
        try {
            Map<String, Object> result = reportService.build(((School) schoolAttr).getId(), req.mediumId, req.gradeId,
                    req.sectionId, (AcademicYear) yearAttr, req.months,
                    AbsentMonthlyReportService.clampLimit(req.limit), LocalDate.now());
            if (result.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Absent monthly report failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the report. Please try again.");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", message);
        return ResponseEntity.status(status).body(m);
    }
}
