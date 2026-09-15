package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.student.StudentImageUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * NEW, isolated REST controller backing "Update Student Images (Group-wise)".
 * Mapped at the same flat (no class-level prefix) scheme StudentBulkUpdateRestController
 * already uses for its AJAX endpoints - does not modify that existing controller
 * or any of its endpoints.
 *
 * Role gate matches StudentBulkUpdateRestController (Update Student Details)
 * exactly, per the project owner's explicit choice.
 */
@RestController
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentImageUploadRestController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentImageUploadRestController.class);

    @Autowired
    private StudentImageUploadService studentImageUploadService;

    @CheckAccess(screen = "STUDENT_UPDATE_IMAGES", type = AccessType.VIEW)
    @PostMapping("/getStudentsForImageUpload")
    public ResponseEntity<?> getStudentsForImageUpload(@RequestBody Map<String, String> requestBody, Model model) {
        log.info("Inside getStudentsForImageUpload");
        try {
            if (requestBody == null) {
                return ResponseEntity.badRequest().body("Request body is missing or invalid.");
            }
            Long mediumId = parseLong(requestBody.get("mediumId"));
            Long gradeId = parseLong(requestBody.get("gradeId"));
            Long sectionId = parseLong(requestBody.get("sectionId"));
            if (mediumId == null || gradeId == null || sectionId == null) {
                return ResponseEntity.badRequest().body("Medium, Grade and Section are all mandatory.");
            }
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to resolve current school/academic year.");
            }
            List<Map<String, Object>> rows = studentImageUploadService.getRowsForImageUpload(
                    mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
            if (rows.isEmpty()) {
                return ResponseEntity.ok("No students found for the given criteria.");
            }
            return ResponseEntity.ok(Map.of("rows", rows));
        } catch (Exception e) {
            log.error("Error in getStudentsForImageUpload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_UPDATE_IMAGES", type = AccessType.EDIT)
    @PostMapping(value = "/saveStudentImage")
    public ResponseEntity<?> saveStudentImage(@RequestParam("uuid") String uuid,
                                               @RequestParam("file") MultipartFile file,
                                               Model model) {
        log.info("Inside saveStudentImage");
        try {
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unable to resolve current school/academic year.");
            }
            Map<String, Object> result = studentImageUploadService.saveStudentImage(
                    uuid, file, academicYear.getId(), school.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in saveStudentImage", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
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
