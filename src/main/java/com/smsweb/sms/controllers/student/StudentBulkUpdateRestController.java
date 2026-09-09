package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.student.StudentBulkUpdateService;
import com.smsweb.sms.services.student.StudentFieldGroupRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW, isolated REST controller backing "Update Student Details (Group-wise)".
 * Mapped at the same flat (no class-level prefix) scheme StudentRestController
 * already uses for its AJAX endpoints (e.g. getStudentsForSR) — does not modify
 * that existing controller or any of its endpoints.
 */
@RestController
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentBulkUpdateRestController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentBulkUpdateRestController.class);

    @Autowired
    private StudentBulkUpdateService studentBulkUpdateService;

    @CheckAccess(screen = "STUDENT_UPDATE_DETAILS", type = AccessType.VIEW)
    @PostMapping("/getStudentsByFieldGroup")
    public ResponseEntity<?> getStudentsByFieldGroup(@RequestBody Map<String, String> requestBody, Model model) {
        log.info("Inside getStudentsByFieldGroup");
        try {
            if (requestBody == null) {
                return ResponseEntity.badRequest().body("Request body is missing or invalid.");
            }
            String groupKey = requestBody.get("groupKey");
            Long mediumId = parseLong(requestBody.get("mediumId"));
            Long gradeId = parseLong(requestBody.get("gradeId"));
            Long sectionId = parseLong(requestBody.get("sectionId"));
            if (groupKey == null || groupKey.isBlank() || mediumId == null || gradeId == null || sectionId == null) {
                return ResponseEntity.badRequest().body("Medium, Grade, Section and Select Data are all mandatory.");
            }
            StudentFieldGroupRegistry.GroupDef group = StudentFieldGroupRegistry.getByKey(groupKey);
            if (group == null) {
                return ResponseEntity.badRequest().body("Unknown data selection.");
            }
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to resolve current school/academic year.");
            }
            List<Map<String, Object>> rows = studentBulkUpdateService.getRowsForGroup(
                    mediumId, gradeId, sectionId, groupKey, academicYear.getId(), school.getId());
            if (rows.isEmpty()) {
                return ResponseEntity.ok("No students found for the given criteria.");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("groupKey", group.getKey());
            result.put("fields", group.getFields());
            result.put("rows", rows);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in getStudentsByFieldGroup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_UPDATE_DETAILS", type = AccessType.EDIT)
    @PostMapping("/saveStudentFieldGroup")
    public ResponseEntity<?> saveStudentFieldGroup(@RequestBody Map<String, Object> requestBody, Model model) {
        log.info("Inside saveStudentFieldGroup");
        try {
            if (requestBody == null) {
                return ResponseEntity.badRequest().body("Request body is missing or invalid.");
            }
            String groupKey = (String) requestBody.get("groupKey");
            Object rowsObj = requestBody.get("rows");
            if (groupKey == null || groupKey.isBlank() || !(rowsObj instanceof List) || ((List<?>) rowsObj).isEmpty()) {
                return ResponseEntity.badRequest().body("Nothing to save.");
            }
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to resolve current school/academic year.");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) rowsObj;
            List<Map<String, Object>> results = studentBulkUpdateService.saveFieldGroup(
                    groupKey, rows, academicYear.getId(), school.getId());
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            log.error("Error in saveStudentFieldGroup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_UPDATE_DETAILS", type = AccessType.VIEW)
    @PostMapping("/checkStudentEmailAvailability")
    public ResponseEntity<?> checkStudentEmailAvailability(@RequestBody Map<String, String> requestBody) {
        log.info("Inside checkStudentEmailAvailability");
        try {
            String email = requestBody != null ? requestBody.get("email") : null;
            String excludeUuid = requestBody != null ? requestBody.get("excludeUuid") : null;
            boolean available = studentBulkUpdateService.isEmailAvailable(email, excludeUuid);
            return ResponseEntity.ok(Map.of("available", available));
        } catch (Exception e) {
            log.error("Error in checkStudentEmailAvailability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("available", false));
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
