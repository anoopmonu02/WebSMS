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
import java.util.Optional;
import java.util.UUID;

public interface AcademicStudentRepository extends JpaRepository<AcademicStudent, Long> {

    //Fetching all active student


    //Fetching student by ID
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND a.Id = :academicStudentId")
    AcademicStudent findByAcademicYearAndSchoolAndAcademicStudentId(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("academicStudentId") Long academic_stu_id);

    //Fetching All students by Name
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND (s.studentName LIKE %:studentName% OR s.fatherName LIKE %:studentName% OR s.motherName LIKE %:studentName%)")
    Page<AcademicStudent> findAllByAcademicYearAndSchoolAndStudentName(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("studentName") String studentName, Pageable pageable);

    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND (s.studentName LIKE %:studentName% OR s.fatherName LIKE %:studentName% OR s.motherName LIKE %:studentName%)")
    Page<AcademicStudent> findAllByAcademicYearAndStudentName(@Param("acadecmicYear") Long acadecmicYear, @Param("studentName") String studentName, Pageable pageable);

    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND (s.fatherName LIKE %:father_name% OR s.motherName LIKE %:mother_name%)")
    List<AcademicStudent> findAllByAcademicYear(@Param("acadecmicYear") Long acadecmicYear, @Param("father_name") String father_name, @Param("mother_name") String mother_name);

    int countByStudent(Student student);

    List<AcademicStudent> findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(Long school, Long medium, Long grade, Long section, Long academic_year, String status);
    List<AcademicStudent> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school, Long academic_year, String status);

    List<AcademicStudent> findAllByStudent_IdAndStatus(Long student, String status);
    Optional<AcademicStudent> findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(UUID uuid, String status, Long academic, Long school);
}
