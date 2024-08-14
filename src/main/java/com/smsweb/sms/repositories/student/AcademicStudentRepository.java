package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AcademicStudentRepository extends JpaRepository<AcademicStudent, Long> {

    //Fetching all active student


    //Fetching student by ID
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND a.Id = :academicStudentId")
    AcademicStudent findByAcademicYearAndSchoolAndAcademicStudentId(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("academicStudentId") Long academic_stu_id);

    //Fetching All students by Name
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND s.studentName LIKE %:studentName% OR s.fatherName LIKE %:studentName% OR s.motherName LIKE %:studentName%")
    Page<AcademicStudent> findAllByAcademicYearAndSchoolAndStudentName(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("studentName") String studentName, Pageable pageable);

    int countByStudent(Student student);

    List<AcademicStudent> findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(Long school, Long medium, Long grade, Long section, Long academic_year, String status);
}
