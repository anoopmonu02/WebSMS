package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.RegionalImportRowDto;
import com.smsweb.sms.dto.RegionalStudentSearchResultDto;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.student.StudentRegionalDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Config > Regional Language Details.
 *
 * Bulk download/upload screen letting an admin fill in each student's name/father
 * name/mother name/address in a regional language (e.g. Hindi) via Excel.
 * Only Student Name/Father Name/Mother Name/Address (Regional) columns are ever
 * written — every other field on `students` is untouched.
 *
 * ROLE_ADMIN / ROLE_SUPERADMIN only (mirrors every other Admin Config screen).
 */
@Controller
@RequestMapping("/admin/student-regional")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class StudentRegionalDetailController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentRegionalDetailController.class);

    private final StudentRegionalDetailService regionalDetailService;

    public StudentRegionalDetailController(StudentRegionalDetailService regionalDetailService) {
        this.regionalDetailService = regionalDetailService;
    }

    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.VIEW)
    @GetMapping
    public String showPage(Model model) {
        log.info("Inside showPage");
        model.addAttribute("page", "plain");
        return "admin/studentRegionalUpdate";
    }

    /** Step 1 — Download the pre-filled template for the current school/session. */
    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.VIEW)
    @GetMapping("/download")
    public ResponseEntity<?> download(Model model) {
        log.info("Inside download");
        try {
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No school selected for this session."));
            }
            ByteArrayInputStream file = regionalDetailService.buildTemplate(
                    school.getId(), school.getSchoolName(),
                    academicYear != null ? academicYear.getSessionFormat() : "");
            InputStreamResource in = new InputStreamResource(file);
            String fileName = "Student_Regional_Language_Template.xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(in);
        } catch (Exception e) {
            log.error("Failed to build regional-language template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to build template: " + e.getMessage()));
        }
    }

    /** Step 2 — Upload & preview (AJAX, nothing saved yet). */
    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.VIEW)
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file, Model model) {
        log.info("Inside preview");
        try {
            School school = (School) model.getAttribute("school");
            if (school == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No school selected for this session."));
            }
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Please select a .xlsx file to upload."));
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Only .xlsx files exported from this screen are supported."));
            }

            List<RegionalImportRowDto> rows = regionalDetailService.parsePreview(file, school.getId());
            if (rows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No data rows found in the uploaded file."));
            }

            long matched = rows.stream().filter(RegionalImportRowDto::isMatched).count();
            long warnings = rows.stream().filter(RegionalImportRowDto::isHasWarning).count();
            Map<String, Object> response = new HashMap<>();
            response.put("rows", rows);
            response.put("total", rows.size());
            response.put("matched", matched);
            response.put("skipped", rows.size() - matched);
            response.put("warnings", warnings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Regional-language preview failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }
    }

    /** Step 3 — Confirm & save the matched rows. */
    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.EDIT)
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody List<RegionalImportRowDto> rows, Model model) {
        log.info("Inside save — rows={}", rows == null ? 0 : rows.size());
        try {
            School school = (School) model.getAttribute("school");
            if (school == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No school selected for this session."));
            }
            if (rows == null || rows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No rows to save."));
            }
            // Only ever persist rows the preview already matched — belt-and-suspenders
            // against a tampered client-side payload trying to sneak an unmatched row through.
            List<RegionalImportRowDto> matchedOnly = rows.stream()
                    .filter(RegionalImportRowDto::isMatched)
                    .collect(java.util.stream.Collectors.toList());

            Map<String, Integer> summary = regionalDetailService.saveConfirmed(matchedOnly, school.getId());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Regional-language save failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Save failed: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single Student Update — for the "just 2-3 new admissions" case, so an
    // admin never has to touch the full bulk-Excel workflow for a handful of rows.
    // ─────────────────────────────────────────────────────────────────────────

    /** Live search (name / father name / mother name / SR no) — same behaviour as the Fee Submission form's search. */
    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.VIEW)
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
            List<RegionalStudentSearchResultDto> results =
                    regionalDetailService.searchStudents(query, academicYear.getId(), school.getId(), page);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Regional-language student search failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }

    /** Insert/update exactly one student's regional details — no bulk file involved. */
    @CheckAccess(screen = "ADMIN_STUDENT_REGIONAL", type = AccessType.EDIT)
    @PostMapping("/save-one")
    @ResponseBody
    public ResponseEntity<?> saveOne(@RequestBody RegionalImportRowDto row, Model model) {
        log.info("Inside saveOne — uuid={}", row != null ? row.getStudentUuid() : null);
        try {
            School school = (School) model.getAttribute("school");
            if (school == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No school selected for this session."));
            }
            if (row == null || row.getStudentUuid() == null || row.getStudentUuid().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No student selected."));
            }
            Map<String, Object> result = regionalDetailService.saveOne(row, school.getId());
            if (!Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Student not found, inactive, or belongs to a different school."));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Regional-language single save failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Save failed: " + e.getMessage()));
        }
    }
}
