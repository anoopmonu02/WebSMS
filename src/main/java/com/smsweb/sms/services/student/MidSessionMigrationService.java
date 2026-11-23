package com.smsweb.sms.services.student;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service dedicated to the "Mid Session Migration" feature (Students menu, visible only to
 * ROLE_ADMIN/ROLE_SUPERADMIN - see MidSessionMigrationController). Does not modify
 * StudentService/FeeSubmissionService's existing methods - it only calls existing, already-
 * tested public methods (StudentMigrationService.migrateStudents) and the read-only
 * MidSessionFeeClearanceService, same pattern already used by StudentMigrationService for
 * the year-end Migrate Student feature.
 *
 * Always cross-school (a student moving to a different branch mid-year due to some
 * circumstance) - never same-school. The actual copy/deactivate mechanics are 100% reused from
 * StudentMigrationService.migrateStudents() rather than duplicated here.
 */
@Service
public class MidSessionMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MidSessionMigrationService.class);

    private final AcademicStudentRepository academicStudentRepository;
    private final StudentMigrationService studentMigrationService;
    private final MidSessionFeeClearanceService feeClearanceService;

    @Autowired
    public MidSessionMigrationService(AcademicStudentRepository academicStudentRepository,
                                       StudentMigrationService studentMigrationService,
                                       MidSessionFeeClearanceService feeClearanceService) {
        this.academicStudentRepository = academicStudentRepository;
        this.studentMigrationService = studentMigrationService;
        this.feeClearanceService = feeClearanceService;
    }

    /**
     * Performs the actual mid-session migration for exactly one student:
     *  1. Validates the destination school is not the student's current school (cross-branch
     *     only - this feature never supports a same-school move).
     *  2. Fee clearance gate (server side, never trusts the page): every month before the
     *     current month must be paid at the current branch, including late fine, and the
     *     previous balance must be 0 - see MidSessionFeeClearanceService. If the check fails
     *     or cannot be completed, NOTHING is written. The tick-box on the page is only a
     *     confirmation and does not bypass this. There is no flag and no override.
     *  3. Delegates the entire copy/create/deactivate mechanic to the existing, already-tested
     *     StudentMigrationService.migrateStudents() - passing a single-entry map so it takes
     *     its normal cross-school branch (new Student + new AcademicStudent at destination,
     *     old Student + old AcademicStudent + old login deactivated).
     *  4. Flags the newly-created destination AcademicStudent row isMidSessionMigration=true,
     *     links it to the old branch's row (carriedFromAcademicStudent) and replaces the generic
     *     "Pending from ..." opening-balance remark with one that matches what really happened.
     */
    @Transactional
    public Map<String, Object> migrateStudentMidSession(Long sourceAcademicStudentId,
                                                          Long currentSchoolId,
                                                          Long destSchoolId,
                                                          Long destAcademicYearId,
                                                          Long destMediumId,
                                                          Long destGradeId,
                                                          Long destSectionId,
                                                          Boolean clearanceConfirmed,
                                                          UserEntity loggedInUser) throws IOException {
        log.info("Inside migrateStudentMidSession - sourceAcademicStudentId={}, destSchoolId={}", sourceAcademicStudentId, destSchoolId);

        if (destSchoolId == null || destSchoolId.equals(currentSchoolId)) {
            throw new IllegalArgumentException("Mid Session Migration only supports moving to a different branch/school - please pick a different destination school.");
        }

        AcademicStudent sourceRecord = academicStudentRepository.findById(sourceAcademicStudentId)
                .orElseThrow(() -> new IllegalArgumentException("Student record not found"));

        if (sourceRecord.getSchool() == null || currentSchoolId == null || !currentSchoolId.equals(sourceRecord.getSchool().getId())) {
            throw new IllegalArgumentException("This student does not belong to your current school");
        }
        if (Boolean.TRUE.equals(sourceRecord.getIsMigrated())
                || AcademicStudent.STATUS_INACTIVE.equalsIgnoreCase(sourceRecord.getStatus())) {
            throw new IllegalArgumentException("This student has already been migrated");
        }

        // Fee clearance gate. Runs before anything is written; fail-closed.
        MidSessionFeeClearanceService.Result clearance = feeClearanceService.check(sourceRecord);
        if (!clearance.canMigrate()) {
            throw new IllegalStateException(clearance.getMessage());
        }
        if (!Boolean.TRUE.equals(clearanceConfirmed)) {
            throw new IllegalArgumentException("Please tick the fee clearance confirmation before saving.");
        }

        // Everything is cleared at the old branch, so nothing is carried forward.
        Map<Long, BigDecimal> singleEntry = new LinkedHashMap<>();
        singleEntry.put(sourceAcademicStudentId, BigDecimal.ZERO);

        StudentMigrationService.MigrationResult result = studentMigrationService.migrateStudents(
                singleEntry, currentSchoolId, destSchoolId, destAcademicYearId,
                destMediumId, destGradeId, destSectionId, loggedInUser);

        if (result.crossSchoolCount == 0 || result.newAcademicStudentIds.isEmpty()) {
            String failureMsg = result.failures.isEmpty() ? "Migration failed" : String.join("; ", result.failures);
            throw new IllegalStateException(failureMsg);
        }

        Long newAcademicStudentId = result.newAcademicStudentIds.get(0);
        AcademicStudent newRecord = academicStudentRepository.findById(newAcademicStudentId)
                .orElseThrow(() -> new IllegalStateException("Migrated record not found immediately after creation"));
        newRecord.setIsMidSessionMigration(true);
        // Link to the old branch's enrollment row so fees paid there stay visible/locked here.
        newRecord.setCarriedFromAcademicStudent(sourceRecord);
        // migrateStudents() always writes "Pending from <session>", which would be misleading here.
        String fromBranch = sourceRecord.getSchool().getSchoolName() != null
                ? sourceRecord.getSchool().getSchoolName() : "previous branch";
        if (clearance.getRequiredUpTo() != null && !clearance.getRequiredUpTo().isEmpty()) {
            newRecord.setOpeningBalanceRemark("Mid-session migration from " + fromBranch
                    + ": fees cleared up to " + clearance.getRequiredUpTo() + ", no dues carried");
        } else {
            newRecord.setOpeningBalanceRemark("Mid-session migration from " + fromBranch + ": no dues carried");
        }
        academicStudentRepository.save(newRecord);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("newAcademicStudentId", newAcademicStudentId);
        response.put("studentName", newRecord.getStudent() != null ? newRecord.getStudent().getStudentName() : "");
        return response;
    }
}
