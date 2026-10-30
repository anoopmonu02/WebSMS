package com.smsweb.sms.repositories.student;

import com.smsweb.sms.dto.BulkMobileLinkRow;
import com.smsweb.sms.dto.ContactFamilyChildRow;
import com.smsweb.sms.dto.FamilyMappingRowDto;
import com.smsweb.sms.models.student.AcademicStudent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only queries for the Family Mapping screen. Kept in its own interface so the
 * existing AcademicStudentRepository is not touched.
 *
 * "Active" rules match the rest of the app (AcademicStudent.status = 'Active',
 * Student.status = 'ACTIVE', not migrated away).
 */
public interface FamilyMappingQueryRepository extends Repository<AcademicStudent, Long> {

    /** One query for the whole list: student, class and current family account in one go (no N+1). */
    @Query("SELECT new com.smsweb.sms.dto.FamilyMappingRowDto(" +
           "s.id, s.studentName, g.gradeName, sec.sectionName, s.fatherName, a.classSrNo, s.psrn, " +
           "s.mobile1, s.mobile2, fa.id, fa.mobile, fa.status) " +
           "FROM AcademicStudent a JOIN a.student s JOIN a.grade g JOIN a.section sec " +
           "LEFT JOIN s.familyAccount fa " +
           "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
           "AND a.medium.id = :mediumId AND a.status = 'Active' AND a.isMigrated = false " +
           "AND s.status = 'ACTIVE' " +
           "ORDER BY g.gradeName, sec.sectionName, s.studentName")
    List<FamilyMappingRowDto> findRows(@Param("schoolId") Long schoolId,
                                       @Param("academicYearId") Long academicYearId,
                                       @Param("mediumId") Long mediumId);

    /** Link state of the given enrollments (by uuid), scoped to this school and session. */
    @Query("SELECT new com.smsweb.sms.dto.BulkMobileLinkRow(a.uuid, s.mobile1, fa.mobile, fa.status) " +
           "FROM AcademicStudent a JOIN a.student s LEFT JOIN s.familyAccount fa " +
           "WHERE a.uuid IN :uuids AND a.school.id = :schoolId AND a.academicYear.id = :academicYearId")
    List<BulkMobileLinkRow> findLinkRows(@Param("uuids") Collection<UUID> uuids,
                                         @Param("schoolId") Long schoolId,
                                         @Param("academicYearId") Long academicYearId);

    /** Is this student an Active student of this school and session? (scope check for Map) */
    @Query("SELECT COUNT(a) FROM AcademicStudent a " +
           "WHERE a.student.id = :studentId AND a.school.id = :schoolId " +
           "AND a.academicYear.id = :academicYearId AND a.status = 'Active' " +
           "AND a.isMigrated = false AND a.student.status = 'ACTIVE'")
    long countActiveEnrollment(@Param("studentId") Long studentId,
                               @Param("schoolId") Long schoolId,
                               @Param("academicYearId") Long academicYearId);

    /** Does at least one Active student of this school and session have this mobile1? */
    @Query("SELECT COUNT(a) FROM AcademicStudent a " +
           "WHERE a.student.mobile1 = :mobile1 AND a.school.id = :schoolId " +
           "AND a.academicYear.id = :academicYearId AND a.status = 'Active' " +
           "AND a.isMigrated = false AND a.student.status = 'ACTIVE'")
    long countActiveByMobile1(@Param("mobile1") String mobile1,
                              @Param("schoolId") Long schoolId,
                              @Param("academicYearId") Long academicYearId);

    /**
     * Other Active children (any branch) who share this mobile1, or who are already linked to
     * this family account. Used by the Fee Submission "update contact" confirmation.
     * Pass familyAccountId = -1 when no account exists for the number.
     */
    @Query("SELECT new com.smsweb.sms.dto.ContactFamilyChildRow(s.id, s.studentName, g.gradeName, sec.sectionName, a.school.id) " +
           "FROM AcademicStudent a JOIN a.student s JOIN a.grade g JOIN a.section sec " +
           "LEFT JOIN s.familyAccount fa " +
           "WHERE a.status = 'Active' AND a.isMigrated = false AND s.status = 'ACTIVE' " +
           "AND s.id <> :studentId AND (s.mobile1 = :mobile OR fa.id = :familyAccountId) " +
           "ORDER BY s.studentName")
    List<ContactFamilyChildRow> findFamilyChildren(@Param("studentId") Long studentId,
                                                   @Param("mobile") String mobile,
                                                   @Param("familyAccountId") Long familyAccountId);

    /** Every enrollment id of a student (all years), to revoke that child's app sessions. */
    @Query("SELECT a.id FROM AcademicStudent a WHERE a.student.id = :studentId")
    List<Long> findAcademicStudentIds(@Param("studentId") Long studentId);
}
