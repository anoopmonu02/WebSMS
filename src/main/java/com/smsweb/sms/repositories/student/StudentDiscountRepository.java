package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.StudentDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentDiscountRepository extends JpaRepository<StudentDiscount, Long> {

    Optional<StudentDiscount> findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndDiscounthead_Id(Long school_id, Long academic_id, Long student_id, Long discount_id);
    Optional<StudentDiscount> findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(Long school_id, Long academic_id, Long student_id, String status);
    Optional<StudentDiscount> findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(Long school_id, Long academic_id, Long student_id);

    List<StudentDiscount> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school_id, Long academic_id, String status);

    List<StudentDiscount> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    /** All discount rows of one student (any head, any status) for a school + year. List-based, so it is safe when a student holds several discount heads. */
    List<StudentDiscount> findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(Long school_id, Long academic_id, Long student_id);

    /** All Active discounts (any head) held by members of one sibling group - read-only overview for the Assign Sibling Discount screen. */
    @Query("SELECT sd FROM StudentDiscount sd " +
           "JOIN FETCH sd.academicStudent acs JOIN FETCH sd.discounthead " +
           "WHERE sd.school.id = :schoolId AND sd.academicYear.id = :academicYearId AND sd.status = 'Active' " +
           "AND acs.id IN (SELECT sgs.academicStudent.id FROM SiblingGroupStudent sgs WHERE sgs.siblingGroup.id = :groupId)")
    List<StudentDiscount> findActiveForGroup(@Param("schoolId") Long schoolId,
                                             @Param("academicYearId") Long academicYearId,
                                             @Param("groupId") Long groupId);

    /** Active discounts of the given head (e.g. "Sibling Discount") held by members of one sibling group. */
    @Query("SELECT sd FROM StudentDiscount sd " +
           "JOIN FETCH sd.academicStudent acs JOIN FETCH acs.student " +
           "WHERE sd.school.id = :schoolId AND sd.academicYear.id = :academicYearId " +
           "AND sd.status = 'Active' AND sd.discounthead.discountName = :discountName " +
           "AND acs.id IN (SELECT sgs.academicStudent.id FROM SiblingGroupStudent sgs WHERE sgs.siblingGroup.id = :groupId)")
    List<StudentDiscount> findActiveByHeadNameForGroup(@Param("schoolId") Long schoolId,
                                                       @Param("academicYearId") Long academicYearId,
                                                       @Param("discountName") String discountName,
                                                       @Param("groupId") Long groupId);


    /** All Active discounts of one head (e.g. "Sibling Discount") for a school + year, with student and assigning user fetched (Sibling Report). */
    @Query("SELECT sd FROM StudentDiscount sd " +
           "JOIN FETCH sd.academicStudent acs JOIN FETCH acs.student " +
           "LEFT JOIN FETCH sd.createdBy " +
           "WHERE sd.school.id = :schoolId AND sd.academicYear.id = :academicYearId " +
           "AND sd.status = 'Active' AND sd.discounthead.discountName = :discountName")
    List<StudentDiscount> findAllActiveByHeadName(@Param("schoolId") Long schoolId,
                                                  @Param("academicYearId") Long academicYearId,
                                                  @Param("discountName") String discountName);

}
