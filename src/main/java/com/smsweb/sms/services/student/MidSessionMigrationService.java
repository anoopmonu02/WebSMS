package com.smsweb.sms.services.student;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FeeDate;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.services.admin.FeedateService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brand-new service dedicated to the "Mid Session Migration" feature (Students menu, visible
 * only to ROLE_ADMIN/ROLE_SUPERADMIN - see MidSessionMigrationController). Does not modify
 * StudentService/FeeSubmissionService's existing methods - it only calls existing, already-
 * tested public methods (StudentMigrationService.migrateStudents, FeeSubmissionService.
 * calculateFeeReminder) and otherwise works with its own logic, same pattern already used by
 * StudentMigrationService for the year-end Migrate Student feature.
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
    private final FeeSubmissionService feeSubmissionService;
    private final FeedateService feedateService;

    @Autowired
    public MidSessionMigrationService(AcademicStudentRepository academicStudentRepository,
                                       StudentMigrationService studentMigrationService,
                                       FeeSubmissionService feeSubmissionService,
                                       FeedateService feedateService) {
        this.academicStudentRepository = academicStudentRepository;
        this.studentMigrationService = studentMigrationService;
        this.feeSubmissionService = feeSubmissionService;
        this.feedateService = feedateService;
    }

    /**
     * Pending dues as of today, so they can be carried forward as the destination school's
     * opening balance. Reuses FeeSubmissionService.calculateFeeReminder() verbatim (same
     * balance + unpaid-fee + fine - discount math already live on the Fee Reminder page) rather
     * than re-implementing it - passing just today's month is enough, because that method
     * already expands a single checked month to every unpaid month from session start through
     * it (via MonthmappingRepository.findMonthsByPriority), exactly mirroring what ticking
     * every checkbox up to today by hand would produce.
     *
     * Falls back to ZERO (with a logged warning) rather than blocking the migration outright if
     * fee-date/fee-class-map configuration is missing for this school/grade - the computed
     * amount is shown to the admin in the confirmation step before they commit, so they can
     * still see and sanity-check it.
     */
    public BigDecimal calculatePendingDueAsOfToday(AcademicStudent sourceRecord, School school, AcademicYear academicYear) {
        log.info("Inside calculatePendingDueAsOfToday - academicStudentId={}", sourceRecord.getId());
        try {
            int currentMonthNum = Calendar.getInstance().get(Calendar.MONTH) + 1;
            List<FeeDate> feeDates = feedateService.getByGivenMonth(academicYear.getId(), school.getId(), currentMonthNum);
            if (feeDates == null || feeDates.isEmpty() || feeDates.get(0).getMonthMaster() == null) {
                log.warn("No fee-date configured for the current month - falling back to zero pending amount for academicStudent id={}", sourceRecord.getId());
                return BigDecimal.ZERO;
            }
            Long currentMonthMasterId = feeDates.get(0).getMonthMaster().getId();

            Map<String, String> paramsMap = new HashMap<>();
            paramsMap.put("grade", String.valueOf(sourceRecord.getGrade().getId()));
            paramsMap.put("section", String.valueOf(sourceRecord.getSection().getId()));
            paramsMap.put("medium", String.valueOf(sourceRecord.getMedium().getId()));
            paramsMap.put("month", String.valueOf(currentMonthMasterId));

            Map reminderResult = feeSubmissionService.calculateFeeReminder(paramsMap, school, academicYear);
            Object finalDataObj = reminderResult != null ? reminderResult.get("finalData") : null;
            if (finalDataObj instanceof Map) {
                Map<?, ?> finalData = (Map<?, ?>) finalDataObj;
                Object entryObj = finalData.get(sourceRecord.getId());
                if (entryObj instanceof Map) {
                    Object amount = ((Map<?, ?>) entryObj).get("amount");
                    if (amount instanceof BigDecimal) return (BigDecimal) amount;
                    if (amount != null) return new BigDecimal(String.valueOf(amount));
                }
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Pending-due calculation failed for academic-student #" + sourceRecord.getId(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Performs the actual mid-session migration for exactly one student:
     *  1. Validates the destination school is not the student's current school (cross-branch
     *     only - this feature never supports a same-school move).
     *  2. Calculates pending dues as of today (above).
     *  3. Delegates the entire copy/create/deactivate mechanic to the existing, already-tested
     *     StudentMigrationService.migrateStudents() - passing a single-entry map so it takes
     *     its normal cross-school branch (new Student + new AcademicStudent at destination,
     *     old Student + old AcademicStudent + old login deactivated).
     *  4. Flags the newly-created destination AcademicStudent row isMidSessionMigration=true,
     *     so Fee Submission knows to offer the one-time "Mid Year Migration Discount" field.
     */
    @Transactional
    public Map<String, Object> migrateStudentMidSession(Long sourceAcademicStudentId,
                                                          Long currentSchoolId,
                                                          Long destSchoolId,
                                                          Long destAcademicYearId,
                                                          Long destMediumId,
                                                          Long destGradeId,
                                                          Long destSectionId,
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

        BigDecimal pendingDue = calculatePendingDueAsOfToday(sourceRecord, sourceRecord.getSchool(), sourceRecord.getAcademicYear());

        Map<Long, BigDecimal> singleEntry = new LinkedHashMap<>();
        singleEntry.put(sourceAcademicStudentId, pendingDue);

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
        academicStudentRepository.save(newRecord);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("newAcademicStudentId", newAcademicStudentId);
        response.put("studentName", newRecord.getStudent() != null ? newRecord.getStudent().getStudentName() : "");
        response.put("pendingDueCarried", pendingDue);
        return response;
    }
}
