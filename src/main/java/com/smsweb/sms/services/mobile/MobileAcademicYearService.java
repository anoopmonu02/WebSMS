package com.smsweb.sms.services.mobile;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.mobile.MobileAcademicStudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NEW service (feature #7), brand-new package (services.mobile). Does NOT
 * modify AcademicStudentService.java. Depends only on the new
 * MobileAcademicStudentRepository — never touches the existing
 * AcademicStudentRepository / AcademicStudentService the admin web
 * dashboard also uses.
 */
@Service
public class MobileAcademicYearService {

    private static final Logger log = LoggerFactory.getLogger(MobileAcademicYearService.class);
    private static final int MAX_SUCCESSOR_DEPTH = 5;

    @Autowired
    private MobileAcademicStudentRepository academicStudentRepository;

    /**
     * Returns every enrollment year (AcademicStudent row) for the same physical
     * student as `academicStudentId`, newest year first. Powers the "switch
     * academic year" picker on Attendance / Fees / Results.
     */
    /**
     * Mid Session Migration: a parent's app session opened before the student was moved to another
     * branch still carries the OLD enrollment row. For the Fees endpoints that row must show the
     * student's current record, so this follows the migration link to the row the student was moved
     * into (and on, if that one was moved again) and returns the first Active one.
     *
     * Ownership is unchanged: the caller has already verified the parent owns {@code resolved};
     * the successor is reached only through the link written by the migration of that same row.
     * Only a row flagged as migrated is looked at, so an ordinary student costs no extra query.
     * A year-end migration writes no link and is not affected. If nothing is found, or the lookup
     * fails, {@code resolved} is returned unchanged (the previous behaviour).
     */
    public AcademicStudent followMidSessionMigration(AcademicStudent resolved) {
        if (resolved == null || !Boolean.TRUE.equals(resolved.getIsMigrated())) return resolved;
        try {
            AcademicStudent cursor = resolved;
            for (int depth = 0; depth < MAX_SUCCESSOR_DEPTH; depth++) {
                Optional<AcademicStudent> next = academicStudentRepository.findByCarriedFromAcademicStudent_Id(cursor.getId());
                if (next.isEmpty()) return resolved;
                cursor = next.get();
                if (AcademicStudent.STATUS_ACTIVE.equalsIgnoreCase(cursor.getStatus())) return cursor;
            }
        } catch (Exception e) {
            log.warn("followMidSessionMigration failed for academicStudentId={}, using the original row: {}",
                    resolved.getId(), e.toString());
        }
        return resolved;
    }

    public List<AcademicStudent> getAcademicYearsForStudent(Long academicStudentId) {
        Optional<AcademicStudent> current = academicStudentRepository.findById(academicStudentId);
        if (current.isEmpty()) {
            return Collections.emptyList();
        }
        Long studentId = current.get().getStudent().getId();
        return academicStudentRepository.findAllByStudentIdOrderByYearDesc(studentId);
    }

    /**
     * Security-critical helper: resolves which AcademicStudent row a mobile
     * request should actually query against.
     *
     * - null or same id as the JWT → returns the JWT's own AcademicStudent
     *   (normal, no-year-switch case).
     * - a DIFFERENT id → only returned if it belongs to the SAME underlying
     *   student.id as the JWT. Otherwise Optional.empty(), and the caller
     *   MUST respond 403 — never trust a client-supplied academicStudentId
     *   without this check, or a valid login for one child could be used to
     *   read another family's attendance/fees/results by guessing ids.
     */
    public Optional<AcademicStudent> resolveTargetAcademicStudent(
            Long requestedAcademicStudentId, Long jwtAcademicStudentId) {

        if (requestedAcademicStudentId == null || requestedAcademicStudentId.equals(jwtAcademicStudentId)) {
            return academicStudentRepository.findById(jwtAcademicStudentId);
        }

        Optional<AcademicStudent> jwtAs = academicStudentRepository.findById(jwtAcademicStudentId);
        Optional<AcademicStudent> requestedAs = academicStudentRepository.findById(requestedAcademicStudentId);

        if (jwtAs.isEmpty() || requestedAs.isEmpty()) {
            return Optional.empty();
        }
        if (!requestedAs.get().getStudent().getId().equals(jwtAs.get().getStudent().getId())) {
            return Optional.empty(); // ownership mismatch — caller must return 403
        }
        return requestedAs;
    }
}
