package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.MidSessionFeeClearanceService;
import com.smsweb.sms.services.student.MidSessionMigrationService;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brand-new REST controller dedicated to the "Mid Session Migration" feature.
 * Does not modify StudentRestController, StudentMigrationRestController, or any other existing
 * endpoint - these are new routes backed by the new MidSessionMigrationService.
 *
 * Reuses the existing GET /getSchoolSessionsForMigration endpoint (already registered by
 * StudentMigrationRestController) as-is for the destination "latest academic year" lookup -
 * no need to duplicate it here.
 */
@RestController
@RequestMapping("/student/mid-session-migration")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class MidSessionMigrationRestController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(MidSessionMigrationRestController.class);

    private final AcademicStudentService academicStudentService;
    private final StudentService studentService;
    private final MidSessionMigrationService midSessionMigrationService;
    private final MidSessionFeeClearanceService feeClearanceService;
    private final UserService userService;

    @Autowired
    public MidSessionMigrationRestController(AcademicStudentService academicStudentService,
                                              StudentService studentService,
                                              MidSessionMigrationService midSessionMigrationService,
                                              MidSessionFeeClearanceService feeClearanceService,
                                              UserService userService) {
        this.academicStudentService = academicStudentService;
        this.studentService = studentService;
        this.midSessionMigrationService = midSessionMigrationService;
        this.feeClearanceService = feeClearanceService;
        this.userService = userService;
    }

    /** Live search (name / father name / mother name / SR no) scoped to the admin's current school + session. */
    @CheckAccess(screen = "STUDENT_MIDSESSION_MIGRATE", type = AccessType.VIEW)
    @GetMapping("/search-student/{query}")
    public ResponseEntity<?> searchStudent(@PathVariable("query") String query,
                                           @RequestParam(defaultValue = "0") int page,
                                           Model model) {
        log.info("Inside searchStudent (mid session migration)");
        try {
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve your school/session."));
            }
            List<AcademicStudent> raw = academicStudentService.searchStudents(query, academicYear.getId(), school.getId(), page);
            List<Map<String, Object>> leanList = new ArrayList<>();
            if (raw != null) {
                for (AcademicStudent as : raw) {
                    // Already-migrated-out students can't be selected here a second time.
                    if (Boolean.TRUE.equals(as.getIsMigrated())) continue;
                    leanList.add(studentService.toLeanAcademicStudentMap(as));
                }
            }
            return ResponseEntity.ok(leanList);
        } catch (Exception e) {
            log.error("Mid session migration student search failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }

    /**
     * Fee clearance status for the selected student, shown as the "Fee Clearance" card on the
     * page: every month before the current month must be paid at this branch (late fine
     * included) and the previous balance must be 0. Read-only. The same check runs again,
     * server side, inside /save - this endpoint only fills the card.
     */
    @CheckAccess(screen = "STUDENT_MIDSESSION_MIGRATE", type = AccessType.VIEW)
    @GetMapping("/fee-clearance/{academicStudentId}")
    public ResponseEntity<?> getFeeClearance(@PathVariable("academicStudentId") Long academicStudentId, Model model) {
        log.info("Inside getFeeClearance (mid session migration)");
        try {
            School currentSchool = (School) model.getAttribute("school");
            if (currentSchool == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve your school"));
            }
            AcademicStudent sourceRecord = academicStudentService.findById(academicStudentId).orElse(null);
            if (sourceRecord == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Student record not found"));
            }
            if (sourceRecord.getSchool() == null || !currentSchool.getId().equals(sourceRecord.getSchool().getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "This student does not belong to your current school"));
            }
            if (Boolean.TRUE.equals(sourceRecord.getIsMigrated())
                    || AcademicStudent.STATUS_INACTIVE.equalsIgnoreCase(sourceRecord.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "This student has already been migrated"));
            }
            return ResponseEntity.ok(feeClearanceService.check(sourceRecord).toMap());
        } catch (Exception e) {
            log.error("Error checking fee clearance for mid session migration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to check fee clearance: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_MIDSESSION_MIGRATE", type = AccessType.EDIT)
    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body, Model model) {
        log.info("Inside save (mid session migration)");
        try {
            School currentSchool = (School) model.getAttribute("school");
            if (currentSchool == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve your school"));
            }
            Long academicStudentId = Long.valueOf(String.valueOf(body.get("academicStudentId")));
            Long destSchoolId = Long.valueOf(String.valueOf(body.get("destSchoolId")));
            Long destAcademicYearId = Long.valueOf(String.valueOf(body.get("destAcademicYearId")));
            Long destMediumId = Long.valueOf(String.valueOf(body.get("destMediumId")));
            Long destGradeId = Long.valueOf(String.valueOf(body.get("destGradeId")));
            Long destSectionId = Long.valueOf(String.valueOf(body.get("destSectionId")));
            Boolean clearanceConfirmed = Boolean.valueOf(String.valueOf(body.get("clearanceConfirmed")));

            UserEntity loggedInUser = userService.getLoggedInUser();

            Map<String, Object> result = midSessionMigrationService.migrateStudentMidSession(
                    academicStudentId, currentSchool.getId(), destSchoolId, destAcademicYearId,
                    destMediumId, destGradeId, destSectionId, clearanceConfirmed, loggedInUser);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving mid session migration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to save migration: " + e.getMessage()));
        }
    }
}
