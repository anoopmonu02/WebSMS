package com.smsweb.sms.repositories.student;

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
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE s.status='Active' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND a.id = :academicStudentId")
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

    @Query("SELECT f FROM AcademicStudent f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.medium.id = :medium AND f.status='Active'")
    List<AcademicStudent> findAllStudentsDetails(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium);

    Optional<AcademicStudent> findById(Long academicStudentId);

    int countAllBySchool_IdAndAcademicYear_IdAndStatus(Long school, Long academic_year, String status);

    @Query("""
    SELECT a FROM AcademicStudent a 
    JOIN a.student s 
    WHERE a.academicYear.id = :academicYear 
    AND a.school.id = :school 
    AND (
        LOWER(s.studentName) LIKE LOWER(CONCAT('%', :studentName, '%')) OR 
        LOWER(s.fatherName) LIKE LOWER(CONCAT('%', :studentName, '%')) OR 
        LOWER(s.motherName) LIKE LOWER(CONCAT('%', :studentName, '%')) OR 
        LOWER(a.classSrNo) LIKE LOWER(CONCAT('%', :studentName, '%')) OR 
        s.mobile1 LIKE CONCAT('%', :studentName, '%') OR 
        LOWER(s.address) LIKE LOWER(CONCAT('%', :studentName, '%'))
    )
    """)
    List<AcademicStudent> findAllByAcademicYearAndSchoolAndStudentNames(
            @Param("academicYear") Long academicYear,
            @Param("school") Long school,
            @Param("studentName") String studentName
    );

    int countAllBySchool_IdAndAcademicYear_IdAndStatusAndStudent_Gender(Long school, Long academic, String status, String gender);

    int countAllBySchool_IdAndAcademicYear_IdAndStatusAndClassSrNoIsNotNull(Long school, Long academic, String status);
    @Query("SELECT COUNT(a) FROM AcademicStudent a " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
            "AND a.status = :status " +
            "AND a.student.aadharNo IS NOT NULL AND a.student.aadharNo <> ''")
    int countWhereAadharNoIsPresent(@Param("schoolId") Long school,
                                    @Param("academicYearId") Long academic,
                                    @Param("status") String status);

    @Query(value = "SELECT s.dob, s.student_name " +
            "FROM academic_students a " +
            "JOIN students s ON a.student_id = s.id " +
            "WHERE a.school_id = :schoolId " +
            "AND a.academic_year_id = :academicYearId " +
            "AND a.status = :status " +
            "AND s.status = :status " +
            "AND s.dob IS NOT NULL " +
            "AND DATE_FORMAT(s.dob, '%m-%d') BETWEEN DATE_FORMAT(CURDATE(), '%m-%d') " +
            "AND DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '%m-%d')",
            nativeQuery = true)
    List<Object[]> findUpcomingBirthdaysInNext7Days(@Param("schoolId") Long school,
                                                    @Param("academicYearId") Long academic,
                                                    @Param("status") String status);

    @Query("SELECT a FROM AcademicStudent a WHERE a.academicYear.id = :academicYearId AND a.school.id = :schoolId AND a.status = :status AND a.student.status = :status GROUP BY a")
    List<Object[]> fetchAllStudentsByGradewise(@Param("schoolId") Long school,
                                               @Param("academicYearId") Long academic,
                                               @Param("status") String status);

    @Query(value = """
        SELECT 
            g.grade_name AS gradeName,
            COUNT(DISTINCT ast.id) AS totalStudents,
            COALESCE(SUM(CASE WHEN att.is_present = TRUE THEN 1 ELSE 0 END), 0) AS presentCount,
            COUNT(DISTINCT ast.id) - COALESCE(SUM(CASE WHEN att.is_present = TRUE THEN 1 ELSE 0 END), 0) AS absentCount
        FROM academic_students ast
        JOIN grade g ON g.id = ast.grade_id
        LEFT JOIN attendance att 
            ON att.academic_student_id = ast.id
            AND DATE(att.attendance_date) = CURRENT_DATE
            AND att.status = 'Active'
        WHERE ast.school_id = :schoolId
          AND ast.academic_year_id = :academicYearId
          AND ast.status = 'Active'
        GROUP BY g.grade_name
        ORDER BY g.grade_name
    """, nativeQuery = true)
    List<Object[]> getGradeWiseAttendanceSummary(@Param("schoolId") Long schoolId,
                                                 @Param("academicYearId") Long academicYearId);


}
