package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.dto.mobile.MobileProfileUpdateRequest;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.mobile.MobileAcademicYearService;
import com.smsweb.sms.services.mobile.MobileStudentProfileService;
import com.smsweb.sms.services.student.AcademicStudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Student profile endpoints.
 *
 * GET  /api/v1/student/profile          — full profile data (read-only fields)
 * GET  /api/v1/student/profile/editable — current values of the self-editable fields
 * PUT  /api/v1/student/profile          — save self-editable fields (blood group,
 *                                          qualifications, email, bank details, health info)
 * POST /api/v1/student/profile/photo    — upload + compress a new profile photo
 * GET  /api/v1/student/banks            — bank list, for the bank-name dropdown
 * GET  /api/v1/student/pic/{filename}   — serve student photo file
 *
 * Uses AcademicStudentService (existing service) for reads, and the new
 * isolated MobileStudentProfileService for the self-edit write path.
 */
@RestController
@RequestMapping("/api/v1/student")
public class MobileStudentController {
    private static final Logger log = LoggerFactory.getLogger(MobileStudentController.class);


    @Value("${student.image.storage.path}")
    private String studentImagePath;

    private final AcademicStudentService academicStudentService;      // existing, unchanged
    private final MobileAcademicYearService academicYearService;     // new, mobile-only
    private final MobileStudentProfileService profileService;        // new, mobile-only

    public MobileStudentController(AcademicStudentService academicStudentService,
                                    MobileAcademicYearService academicYearService,
                                    MobileStudentProfileService profileService) {
        this.academicStudentService = academicStudentService;
        this.academicYearService = academicYearService;
        this.profileService = profileService;
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

    // ── GET /api/v1/student/academic-years ────────────────────────────────────

    /**
     * Every year this (physical) student has been enrolled, so the app can
     * show a year picker on Attendance / Fees / Results and let the user
     * switch (feature #7). Uses the new MobileAcademicYearService — does not
     * touch AcademicStudentService.
     */
    @GetMapping("/academic-years")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAcademicYears(
            HttpServletRequest request) {
        log.info("Inside getAcademicYears");

        Long currentAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        List<AcademicStudent> rows = academicYearService.getAcademicYearsForStudent(currentAcademicStudentId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AcademicStudent a : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("academicStudentId", a.getId());
            entry.put("academicYearId",    a.getAcademicYear().getId());
            entry.put("sessionFormat",     a.getAcademicYear().getSessionFormat());
            entry.put("gradeName",         a.getGrade()   != null ? a.getGrade().getGradeName()     : null);
            entry.put("sectionName",       a.getSection() != null ? a.getSection().getSectionName() : null);
            entry.put("schoolId",          a.getSchool().getId());
            entry.put("schoolName",        a.getSchool().getSchoolName());
            entry.put("status",            a.getStatus());
            entry.put("isCurrent",         a.getId().equals(currentAcademicStudentId));
            result.add(entry);
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Self-edit profile (new, mobile-only) ──────────────────────────────────

    /** Resolves the caller's own, CURRENT AcademicStudent row from the JWT only — never a client-supplied id. */
    private Optional<AcademicStudent> resolveOwnAcademicStudent(HttpServletRequest request) {
        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        if (academicStudentId == null) return Optional.empty();
        return academicStudentService.findById(academicStudentId);
    }

    @GetMapping("/profile/editable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEditableProfile(HttpServletRequest request) {
        log.info("Inside getEditableProfile");
        Optional<AcademicStudent> optAs = resolveOwnAcademicStudent(request);
        if (optAs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Student record not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(profileService.getEditableProfile(optAs.get())));
    }

    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBanks(HttpServletRequest request) {
        log.info("Inside getBanks");
        return ResponseEntity.ok(ApiResponse.success(profileService.getBankList()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
            @RequestBody MobileProfileUpdateRequest req, HttpServletRequest request) {
        log.info("Inside updateProfile");
        Optional<AcademicStudent> optAs = resolveOwnAcademicStudent(request);
        if (optAs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Student record not found"));
        }
        try {
            profileService.updateProfile(optAs.get(), req);
            return ResponseEntity.ok(ApiResponse.success("Profile updated", profileService.getEditableProfile(optAs.get())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("updateProfile failed", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Could not update profile"));
        }
    }

    @PostMapping("/profile/photo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePhoto(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        log.info("Inside updatePhoto");
        Optional<AcademicStudent> optAs = resolveOwnAcademicStudent(request);
        if (optAs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Student record not found"));
        }
        try {
            String url = profileService.updatePhoto(optAs.get(), file);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("profilePicUrl", url);
            return ResponseEntity.ok(ApiResponse.success("Photo updated", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("updatePhoto failed", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Could not update photo"));
        }
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
