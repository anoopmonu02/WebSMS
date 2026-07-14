package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.student.StudentMigrationService;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Brand-new REST controller dedicated to the "Migrate Student" feature.
 * Does not modify StudentRestController or any other existing endpoint -
 * these are new routes backed by the new StudentMigrationService.
 */
@RestController
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentMigrationRestController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentMigrationRestController.class);

    private final StudentService studentService;
    private final StudentMigrationService studentMigrationService;
    private final AcademicyearService academicyearService;
    private final UserService userService;

    @Autowired
    public StudentMigrationRestController(StudentService studentService,
                                           StudentMigrationService studentMigrationService,
                                           AcademicyearService academicyearService,
                                           UserService userService) {
        this.studentService = studentService;
        this.studentMigrationService = studentMigrationService;
        this.academicyearService = academicyearService;
        this.userService = userService;
    }

    @CheckAccess(screen = "STUDENT_MIGRATE", type = AccessType.VIEW)
    @PostMapping("/getMigrationSourceStudents")
    public ResponseEntity<?> getMigrationSourceStudents(@RequestBody Map<String, String> body, Model model) {
        log.info("Inside getMigrationSourceStudents");
        try {
            School school = (School) model.getAttribute("school");
            if (school == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve your school"));
            }
            Long mediumId = Long.valueOf(body.get("mediumId"));
            Long gradeId = Long.valueOf(body.get("gradeId"));
            Long sectionId = Long.valueOf(body.get("sectionId"));
            Long academicYearId = Long.valueOf(body.get("academicYearId"));

            List<AcademicStudent> records = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYearId, school.getId());
            if (records == null || records.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No active students found for the selected session/medium/grade/section"));
            }

            // Exclude records already migrated out in an earlier run (same-branch migration
            // deliberately leaves status = Active on the old row, so isMigrated is the only
            // signal that filters them out here). Doesn't touch getAllStudentsByGrade() itself
            // - that method is shared by other, unrelated screens.
            List<AcademicStudent> eligibleRecords = records.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsMigrated()))
                    .collect(Collectors.toList());
            if (eligibleRecords.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "All students in this session/medium/grade/section have already been migrated"));
            }

            List<Map<String, Object>> rows = eligibleRecords.stream()
                    .filter(r -> r.getStudent() != null)
                    .sorted(Comparator.comparing(r -> r.getStudent().getStudentName() == null ? "" : r.getStudent().getStudentName()))
                    .map(r -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("academicStudentId", r.getId());
                        row.put("studentId", r.getStudent().getId());
                        row.put("studentName", r.getStudent().getStudentName());
                        row.put("fatherName", r.getStudent().getFatherName());
                        row.put("motherName", r.getStudent().getMotherName());
                        row.put("rollNo", r.getRollNo());
                        row.put("classSrNo", r.getClassSrNo());
                        return row;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("Error fetching source students for migration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to fetch students: " + e.getMessage()));
        }
    }

    /**
     * Destination academic year is locked to the selected school's single latest session -
     * same rule the old Grails migrateStudent.gsp used for its "current academic year" panel
     * ("order by id desc limit 1"). Returns a clear error instead of an empty list when the
     * selected school has no academic year set up yet, so the UI can explain why migration
     * can't proceed rather than showing a broken/empty dropdown.
     */
    @CheckAccess(screen = "STUDENT_MIGRATE", type = AccessType.VIEW)
    @GetMapping("/getSchoolSessionsForMigration")
    public ResponseEntity<?> getSchoolSessionsForMigration(@RequestParam Long schoolId) {
        log.info("Inside getSchoolSessionsForMigration");
        try {
            List<AcademicYear> years = academicyearService.getAllAcademiyears(schoolId);
            if (years == null || years.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "This school doesn't have any academic session set up yet. Add an academic year for it first, then try migrating students here again."));
            }
            AcademicYear latest = years.get(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", latest.getId());
            row.put("sessionFormat", latest.getSessionFormat());
            return ResponseEntity.ok(row);
        } catch (Exception e) {
            log.error("Error fetching sessions for school {}", schoolId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to fetch sessions: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_MIGRATE", type = AccessType.VIEW)
    @PostMapping("/calculateMigrationPendingFee")
    public ResponseEntity<?> calculateMigrationPendingFee(@RequestBody Map<String, Object> body) {
        log.info("Inside calculateMigrationPendingFee");
        try {
            Object rawIds = body.get("academicStudentIds");
            if (!(rawIds instanceof List) || ((List<?>) rawIds).isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No students to calculate pending fee for"));
            }
            List<Long> academicStudentIds = ((List<?>) rawIds).stream()
                    .map(o -> Long.valueOf(String.valueOf(o)))
                    .collect(Collectors.toList());

            Map<Long, BigDecimal> pendingByAcademicStudentId = studentMigrationService.calculatePendingFeesForMigration(academicStudentIds);

            Map<String, Object> pendingFees = new LinkedHashMap<>();
            pendingByAcademicStudentId.forEach((id, amount) -> pendingFees.put(String.valueOf(id), amount));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("pendingFees", pendingFees);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error calculating pending fee for migration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to calculate pending fee: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_MIGRATE", type = AccessType.EDIT)
    @PostMapping("/saveStudentMigration")
    public ResponseEntity<?> saveStudentMigration(@RequestBody Map<String, Object> body, Model model) {
        log.info("Inside saveStudentMigration");
        try {
            School currentSchool = (School) model.getAttribute("school");
            if (currentSchool == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve your school"));
            }
            Object rawStudents = body.get("sourceStudents");
            if (!(rawStudents instanceof List) || ((List<?>) rawStudents).isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Select at least one student to migrate"));
            }
            Map<Long, BigDecimal> sourceAcademicStudentIdToPendingFee = new LinkedHashMap<>();
            for (Object entryObj : (List<?>) rawStudents) {
                if (!(entryObj instanceof Map)) continue;
                Map<?, ?> entryMap = (Map<?, ?>) entryObj;
                Long academicStudentId = Long.valueOf(String.valueOf(entryMap.get("academicStudentId")));
                Object pendingFeeRaw = entryMap.get("pendingFee");
                BigDecimal pendingFee;
                try {
                    pendingFee = (pendingFeeRaw == null) ? BigDecimal.ZERO : new BigDecimal(String.valueOf(pendingFeeRaw));
                } catch (NumberFormatException nfe) {
                    pendingFee = BigDecimal.ZERO;
                }
                sourceAcademicStudentIdToPendingFee.put(academicStudentId, pendingFee);
            }
            if (sourceAcademicStudentIdToPendingFee.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Select at least one student to migrate"));
            }

            Long destSchoolId = Long.valueOf(String.valueOf(body.get("destSchoolId")));
            Long destAcademicYearId = Long.valueOf(String.valueOf(body.get("destAcademicYearId")));
            Long destMediumId = Long.valueOf(String.valueOf(body.get("destMediumId")));
            Long destGradeId = Long.valueOf(String.valueOf(body.get("destGradeId")));
            Long destSectionId = Long.valueOf(String.valueOf(body.get("destSectionId")));

            UserEntity loggedInUser = userService.getLoggedInUser();

            StudentMigrationService.MigrationResult result = studentMigrationService.migrateStudents(
                    sourceAcademicStudentIdToPendingFee, currentSchool.getId(), destSchoolId, destAcademicYearId,
                    destMediumId, destGradeId, destSectionId, loggedInUser);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sameSchoolCount", result.sameSchoolCount);
            response.put("crossSchoolCount", result.crossSchoolCount);
            response.put("failures", result.failures);
            response.put("totalMoved", result.sameSchoolCount + result.crossSchoolCount);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving student migration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to save migration: " + e.getMessage()));
        }
    }
}
