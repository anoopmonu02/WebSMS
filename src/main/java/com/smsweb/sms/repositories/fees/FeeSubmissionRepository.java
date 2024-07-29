package com.smsweb.sms.repositories.fees;


import com.smsweb.sms.models.fees.FeeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeeSubmissionRepository extends JpaRepository<FeeSubmission, Long> {


    List<FeeSubmission> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<FeeSubmission> findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(Long school_id, Long academic_id, Long academic_student_id);

    List<FeeSubmission> findAllByAcademicStudent_Id(Long academic_student_id);

    @Query("SELECT f FROM FeeSubmission f JOIN f.feeSubmissionBalance b WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.Id = :academicStudentId AND f.status='Active' AND b.status='Active' ORDER BY f.id DESC")
    Optional<FeeSubmission> findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(@Param("schoolId") Long schoolId, @Param("academicYearId") Long academicYearId, @Param("academicStudentId") Long academicStudentId);

    @Query("SELECT fs FROM FeeSubmission fs JOIN fs.academicStudent astu WHERE fs.school.id = :schoolId and fs.academicYear.id = :academicId AND fs.academicStudent.Id = :academicStudentId AND fs.status='Active'")
    List<FeeSubmission> findAllBySchoolIdAndAcademicIdAndAcademicStudentId(@Param("schoolId") Long schoolId, @Param("academicId") Long academicId, @Param("academicStudentId") Long academicStudentId);

    int countAllByAcademicYear_IdAndSchool_IdAndAcademicStudent_IdAndStatus(Long academic_id, Long school_id, Long astudent_id, String status);




}
