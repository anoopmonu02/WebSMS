package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.student.AcademicStudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Student profile endpoints.
 *
 * GET /api/v1/student/profile          — full profile data
 * GET /api/v1/student/pic/{filename}   — serve student photo file
 *
 * Uses AcademicStudentService (existing service).
 */
@RestController
@RequestMapping("/api/v1/student")
public class MobileStudentController {
    private static final Logger log = LoggerFactory.getLogger(MobileStudentController.class);


    @Value("${student.image.storage.path}")
    private String studentImagePath;

    private final AcademicStudentService academicStudentService;

    public MobileStudentController(AcademicStudentService academicStudentService) {
        this.academicStudentService = academicStudentService;
    }

    // ── GET /api/v1/student/profile ───────────────────────────────────────────

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile(HttpServletRequest request) {
        log.info("Inside getProfile");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        // Uses existing AcademicStudentService.findById()
        Optional<AcademicStudent> optAs = academicStudentService.findById(academicStudentId);
        if (optAs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Student record not found"));
        }

        AcademicStudent as = optAs.get();
        Student s = as.getStudent();

        Map<String, Object> profile = new LinkedHashMap<>();

        // ── Identity ─────────────────────────────────────────────────────────
        profile.put("academicStudentId", as.getId());
        profile.put("studentName",       s.getStudentName());
        profile.put("fatherName",        s.getFatherName());
        profile.put("motherName",        s.getMotherName());
        profile.put("registrationNo",    s.getRegistrationNo());
        profile.put("classSrNo",         as.getClassSrNo());
        profile.put("rollNo",            as.getRollNo());
        profile.put("boardSrNo",         as.getBoardSrNo());

        // ── Personal ──────────────────────────────────────────────────────────
        profile.put("dob",               s.getDob());
        profile.put("gender",            s.getGender());
        profile.put("religion",          s.getReligion());
        profile.put("nationality",       s.getNationality());
        profile.put("bloodGroup",        s.getBloodGroup());
        profile.put("aadharNo",          maskAadhar(s.getAadharNo()));
        profile.put("apaarId",           s.getApaarId());
        profile.put("penNo",             s.getPenNo());
        profile.put("profilePicUrl",
                s.getPic() != null ? "/sms/api/v1/student/pic/" + s.getPic() : null);

        // ── Academic ─────────────────────────────────────────────────────────
        profile.put("gradeName",        as.getGrade().getGradeName());
        profile.put("sectionName",      as.getSection().getSectionName());
        profile.put("mediumName",       as.getMedium().getMediumName());
        profile.put("academicYearName", as.getAcademicYear().getSessionFormat());
        profile.put("studentType",      s.getStudentType());
        profile.put("admissionStatus",  as.getStatus() != null ? as.getStatus() : "Active");
        profile.put("category",
                s.getCategory() != null ? s.getCategory().getCategoryName() : null);
        profile.put("caste",
                s.getCast() != null ? s.getCast().getCastName() : null);

        // ── Contact ───────────────────────────────────────────────────────────
        profile.put("address",   s.getAddress());
        profile.put("landmark",  s.getLandmark());
        profile.put("city",      s.getCity()     != null ? s.getCity().getCityName()         : null);
        profile.put("province",  s.getProvince() != null ? s.getProvince().getProvinceName() : null);
        profile.put("pincode",   s.getPincode());
        profile.put("mobile1",   s.getMobile1());
        profile.put("mobile2",   s.getMobile2());

        // ── School ────────────────────────────────────────────────────────────
        profile.put("schoolName",      as.getSchool().getSchoolName());
        profile.put("schoolId",        as.getSchool().getId());
        profile.put("academicYearId",  as.getAcademicYear().getId());

        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    // ── GET /api/v1/student/pic/{filename} ────────────────────────────────────

    @GetMapping("/pic/{filename}")
    public ResponseEntity<Resource> getProfilePic(@PathVariable String filename) {
        log.info("Inside getProfilePic");

        // Strip everything except letters, digits, hyphens, underscores, and a single dot.
        // Blocks path traversal attempts like ../../etc/passwd or %2F encoded variants.
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "");
        if (safeName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            File base = new File(studentImagePath).getCanonicalFile();
            File file = new File(base, safeName).getCanonicalFile();

            // Double-lock: even if the sanitised name somehow resolves outside the
            // images folder, the canonical path check blocks it.
            if (!file.getPath().startsWith(base.getPath() + File.separator)) {
                log.warn("Path traversal attempt blocked: requested={} resolved={}", filename, file.getPath());
                return ResponseEntity.status(403).build();
            }

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Detect content type from file extension instead of assuming JPEG.
            String lower = safeName.toLowerCase();
            MediaType mediaType = lower.endsWith(".png")  ? MediaType.IMAGE_PNG
                                : lower.endsWith(".gif")  ? MediaType.IMAGE_GIF
                                : MediaType.IMAGE_JPEG;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(new FileSystemResource(file));

        } catch (java.io.IOException e) {
            log.error("Error resolving profile pic path: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Masks Aadhaar: show only last 4 digits — "XXXX XXXX 1234" */
    private String maskAadhar(String aadhar) {
        if (aadhar == null || aadhar.length() < 4) return aadhar;
        return "XXXX XXXX " + aadhar.substring(aadhar.length() - 4);
    }
}
