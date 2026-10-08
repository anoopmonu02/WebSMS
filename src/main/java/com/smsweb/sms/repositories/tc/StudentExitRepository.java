package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.universal.Grade;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentExitRepository extends JpaRepository<StudentExit, Long>, JpaSpecificationExecutor<StudentExit> {

    Optional<StudentExit> findByUuidAndSchool_Id(UUID uuid, Long schoolId);

    /** Locks the exit row. Must run inside the caller's transaction (first lock in the order). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM StudentExit e WHERE e.uuid = :uuid AND e.school.id = :schoolId")
    Optional<StudentExit> findByUuidForUpdate(@Param("uuid") UUID uuid, @Param("schoolId") Long schoolId);

    /** The open exit of a student (status EXITED or APPROVED), if any. */
    Optional<StudentExit> findFirstByStudent_IdAndSchool_IdAndStatusIn(Long studentId, Long schoolId,
                                                                        Collection<ExitStatus> statuses);

    /** The exit of one enrollment in the given status (used by the fee screen to find a collectable exit). */
    Optional<StudentExit> findFirstByAcademicStudent_IdAndSchool_IdAndStatus(Long academicStudentId, Long schoolId,
                                                                             ExitStatus status);

    /** True if the school has an exit in the given statuses for the grade (used before unmapping a grade). */
    boolean existsBySchool_IdAndGrade_IdAndStatusIn(Long schoolId, Long gradeId, Collection<ExitStatus> statuses);

    List<StudentExit> findByStudent_IdAndSchool_IdOrderByIdDesc(Long studentId, Long schoolId);

    /** Classes that have at least one exit in the school (for the exit list filter). */
    @Query("SELECT DISTINCT g FROM StudentExit e JOIN e.grade g WHERE e.school.id = :schoolId ORDER BY g.gradeName")
    List<Grade> findDistinctGrades(@Param("schoolId") Long schoolId);

    List<StudentExit> findBySchool_IdAndAcademicYear_IdAndStatus(Long schoolId, Long academicYearId, ExitStatus status);

    /**
     * Exits of the school whose student matches the typed text (name, father, mother, SR no, registration
     * no or PSRN), newest first, for the re-registration search. Cancelled exits are left out.
     * {@code term} is a ready-made lower-case LIKE pattern such as "%ram%"; the caller removes % and _ from
     * what the user typed. Pass a Pageable to cap the rows.
     */
    @Query("SELECT e FROM StudentExit e JOIN FETCH e.student s JOIN FETCH e.grade JOIN FETCH e.section " +
            "JOIN FETCH e.academicStudent a JOIN FETCH e.academicYear " +
            "WHERE e.school.id = :schoolId AND e.status <> :excluded " +
            "AND (LOWER(s.studentName) LIKE :term OR LOWER(s.fatherName) LIKE :term OR LOWER(s.motherName) LIKE :term " +
            "OR LOWER(a.classSrNo) LIKE :term OR LOWER(s.registrationNo) LIKE :term OR CAST(s.psrn AS string) LIKE :term) " +
            "ORDER BY e.id DESC")
    List<StudentExit> searchForReRegistration(@Param("schoolId") Long schoolId,
                                              @Param("excluded") ExitStatus excluded,
                                              @Param("term") String term,
                                              Pageable pageable);
}
