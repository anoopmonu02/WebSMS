package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read access to enrollments for the TC screens. Kept apart from the existing
 * AcademicStudentRepository so that file stays untouched.
 */
public interface StudentLookupRepository extends JpaRepository<AcademicStudent, Long> {

    /**
     * Students who can be struck off: Active enrollment and Active student in the given school and
     * session, with no open exit. {@code term} is a ready-made lower-case LIKE pattern such as
     * "%ram%"; the caller must remove % and _ from what the user typed. Pass a Pageable to cap the rows.
     */
    @Query("SELECT a FROM AcademicStudent a JOIN FETCH a.student s JOIN FETCH a.grade JOIN FETCH a.section JOIN FETCH a.medium " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
            "AND a.status = 'Active' AND UPPER(s.status) = 'ACTIVE' " +
            "AND (LOWER(s.studentName) LIKE :term OR LOWER(s.fatherName) LIKE :term OR LOWER(s.motherName) LIKE :term " +
            "OR LOWER(a.classSrNo) LIKE :term OR LOWER(s.registrationNo) LIKE :term) " +
            "AND NOT EXISTS (SELECT 1 FROM StudentExit e WHERE e.student.id = s.id AND e.status IN :openStatuses) " +
            "ORDER BY s.studentName ASC")
    List<AcademicStudent> searchStrikeOffCandidates(@Param("schoolId") Long schoolId,
                                                    @Param("academicYearId") Long academicYearId,
                                                    @Param("term") String term,
                                                    @Param("openStatuses") Collection<ExitStatus> openStatuses,
                                                    Pageable pageable);

    /**
     * One enrollment of the school with a row lock, so two people cannot strike off the same
     * student at the same moment. Must run inside the caller's transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AcademicStudent a WHERE a.id = :id AND a.school.id = :schoolId")
    Optional<AcademicStudent> findByIdForUpdate(@Param("id") Long id, @Param("schoolId") Long schoolId);

    /**
     * The Active enrollment of one student in the given school and session, if the student can be
     * struck off (no open exit). Used when the old Remove Student button sends a student here.
     */
    @Query("SELECT a FROM AcademicStudent a JOIN FETCH a.student s JOIN FETCH a.grade JOIN FETCH a.section JOIN FETCH a.medium " +
            "WHERE s.id = :studentId AND a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
            "AND a.status = 'Active' AND UPPER(s.status) = 'ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM StudentExit e WHERE e.student.id = s.id AND e.status IN :openStatuses)")
    List<AcademicStudent> findStrikeOffCandidateByStudentId(@Param("studentId") Long studentId,
                                                            @Param("schoolId") Long schoolId,
                                                            @Param("academicYearId") Long academicYearId,
                                                            @Param("openStatuses") Collection<ExitStatus> openStatuses);

    /** The enrollment(s) of one student in one session (used to find the enrollment just created by a registration). */
    @Query("SELECT a FROM AcademicStudent a WHERE a.student.id = :studentId AND a.academicYear.id = :academicYearId " +
            "AND a.school.id = :schoolId ORDER BY a.id DESC")
    List<AcademicStudent> findEnrollmentsOfStudentInYear(@Param("studentId") Long studentId,
                                                         @Param("academicYearId") Long academicYearId,
                                                         @Param("schoolId") Long schoolId);

    /** One student of the school by its public id (used by the timeline page). */
    @Query("SELECT s FROM Student s WHERE s.uuid = :uuid AND s.school.id = :schoolId")
    Optional<Student> findStudentByUuid(@Param("uuid") java.util.UUID uuid, @Param("schoolId") Long schoolId);

    /** One enrollment of the school with student, class, section and medium loaded (read only). */
    @Query("SELECT a FROM AcademicStudent a JOIN FETCH a.student JOIN FETCH a.grade JOIN FETCH a.section JOIN FETCH a.medium " +
            "WHERE a.id = :id AND a.school.id = :schoolId")
    Optional<AcademicStudent> findDetailed(@Param("id") Long id, @Param("schoolId") Long schoolId);
}
