package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.FamilyAccount;
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
    @Query("SELECT a FROM AcademicStudent a LEFT JOIN FETCH a.student s WHERE UPPER(s.status)='ACTIVE' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND a.id = :academicStudentId")
    AcademicStudent findByAcademicYearAndSchoolAndAcademicStudentId(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("academicStudentId") Long academic_stu_id);

    //Fetching All students by Name
    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE UPPER(s.status)='ACTIVE' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND a.school.id = :school AND (s.studentName LIKE %:studentName% OR s.fatherName LIKE %:studentName% OR s.motherName LIKE %:studentName% OR a.classSrNo LIKE :studentName)")
    Page<AcademicStudent> findAllByAcademicYearAndSchoolAndStudentName(@Param("acadecmicYear") Long acadecmicYear, @Param("school")Long school, @Param("studentName") String studentName, Pageable pageable);

    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE UPPER(s.status)='ACTIVE' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND (s.studentName LIKE %:studentName% OR s.fatherName LIKE %:studentName% OR s.motherName LIKE %:studentName%)")
    Page<AcademicStudent> findAllByAcademicYearAndStudentName(@Param("acadecmicYear") Long acadecmicYear, @Param("studentName") String studentName, Pageable pageable);

    @Query("SELECT a FROM AcademicStudent a JOIN a.student s WHERE UPPER(s.status)='ACTIVE' AND a.status='Active' AND a.academicYear.id = :acadecmicYear AND (s.fatherName LIKE %:father_name% OR s.motherName LIKE %:mother_name%)")
    List<AcademicStudent> findAllByAcademicYear(@Param("acadecmicYear") Long acadecmicYear, @Param("father_name") String father_name, @Param("mother_name") String mother_name);

    int countByStudent(Student student);

    List<AcademicStudent> findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(Long school, Long medium, Long grade, Long section, Long academic_year, String status);
    List<AcademicStudent> findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_Id(Long school, Long medium, Long grade, Long section, Long academic_year);
    List<AcademicStudent> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school, Long academic_year, String status);
    List<AcademicStudent> findAllBySchool_IdAndStatus(Long school, String status);

    List<AcademicStudent> findAllByStudent_IdAndStatus(Long student, String status);
    Optional<AcademicStudent> findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(UUID uuid, String status, Long academic, Long school);

    /** UUID is globally unique — use this for exam result upload to avoid academicYear/school mismatch issues */
    Optional<AcademicStudent> findByUuid(UUID uuid);

    @Query("SELECT f FROM AcademicStudent f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.medium.id = :medium AND f.status='Active'")
    List<AcademicStudent> findAllStudentsDetails(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium);


    @Query("SELECT f FROM AcademicStudent f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.medium.id = :medium")
    List<AcademicStudent> findAllStudentsDetailsBySession(
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

    /** Returns [dob, studentName, gradeName, sectionName] for students whose birthday is TODAY. */
    @Query(value = "SELECT s.dob, s.student_name, g.grade_name, sec.section_name " +
            "FROM academic_students a " +
            "JOIN students s  ON s.id  = a.student_id " +
            "JOIN grade g     ON g.id  = a.grade_id " +
            "JOIN section sec ON sec.id = a.section_id " +
            "WHERE a.school_id = :schoolId " +
            "AND a.academic_year_id = :academicYearId " +
            "AND a.status = :status " +
            "AND s.status = :status " +
            "AND s.dob IS NOT NULL " +
            "AND DATE_FORMAT(s.dob, '%m-%d') = DATE_FORMAT(CURDATE(), '%m-%d') " +
            "ORDER BY s.student_name",
            nativeQuery = true)
    List<Object[]> findTodaysBirthdays(@Param("schoolId") Long school,
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


    /** Attendance trend last 7 days — returns [date, gender, present_count] */
    @Query(value = """
        SELECT DATE(att.attendance_date) AS att_date,
               s.gender,
               SUM(CASE WHEN att.is_present = TRUE THEN 1 ELSE 0 END) AS present_count
        FROM attendance att
        JOIN academic_students ast ON ast.id = att.academic_student_id
        JOIN students s            ON s.id   = ast.student_id
        WHERE ast.school_id         = :schoolId
          AND ast.academic_year_id  = :academicYearId
          AND ast.status            = 'Active'
          AND UPPER(s.status)       = 'ACTIVE'
          AND att.status            = 'Active'
          AND DATE(att.attendance_date) >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        GROUP BY DATE(att.attendance_date), s.gender
        ORDER BY att_date, s.gender
    """, nativeQuery = true)
    List<Object[]> getAttendanceTrendLast7Days(@Param("schoolId") Long schoolId,
                                               @Param("academicYearId") Long academicYearId);

    /** Birthday count per calendar month — returns [month_number(1-12), count] */
    @Query(value = """
        SELECT MONTH(s.dob) AS birth_month, COUNT(*) AS student_count
        FROM academic_students a
        JOIN students s ON s.id = a.student_id
        WHERE a.school_id        = :schoolId
          AND a.academic_year_id = :academicYearId
          AND a.status           = 'Active'
          AND UPPER(s.status)    = 'ACTIVE'
          AND s.dob IS NOT NULL
        GROUP BY MONTH(s.dob)
        ORDER BY birth_month
    """, nativeQuery = true)
    List<Object[]> getBirthdayDistributionByMonth(@Param("schoolId") Long schoolId,
                                                  @Param("academicYearId") Long academicYearId);

    @Query(value = "SELECT a.grade.gradeName, a.section.sectionName, a.grade.id as gradeId, a.section.id as sectionId, count(a.student.id) as TotalStudents FROM AcademicStudent a WHERE a.academicYear.id = :academicYearId AND a.school.id = :schoolId AND a.status = :status AND a.student.status = :status group by a.grade, a.section")
    List<Object[]> getGradesAndSectionList(@Param("schoolId") Long schoolId,
                                 @Param("academicYearId") Long academicYearId,
                                 @Param("status") String status);

    // ── Mobile API queries ────────────────────────────────────────────────────

    /**
     * Finds an active AcademicStudent by SR Number (classSrNo) for mobile login.
     * FETCH joins ensure student + userEntity are loaded in a single query
     * (avoids LazyInitializationException outside a transaction).
     */
    @Query("SELECT a FROM AcademicStudent a " +
           "JOIN FETCH a.student s " +
           "JOIN FETCH s.userEntity u " +
           "JOIN FETCH a.school sc " +
           "JOIN FETCH a.academicYear ay " +
           "JOIN FETCH a.grade g " +
           "JOIN FETCH a.section sec " +
           "JOIN FETCH a.medium m " +
           "WHERE a.classSrNo = :classSrNo AND a.status = 'Active' AND s.status = 'ACTIVE'")
    Optional<AcademicStudent> findActiveByClassSrNo(@Param("classSrNo") String classSrNo);


    /**
     * Finds all active AcademicStudents whose parent mobile1 matches.
     * Used by mobile login to support families with multiple children.
     */
    @Query("SELECT a FROM AcademicStudent a " +
           "JOIN FETCH a.student s " +
           "JOIN FETCH s.userEntity u " +
           "JOIN FETCH a.school sc " +
           "JOIN FETCH a.academicYear ay " +
           "JOIN FETCH a.grade g " +
           "JOIN FETCH a.section sec " +
           "JOIN FETCH a.medium m " +
           "WHERE s.mobile1 = :mobile AND a.status = 'Active' AND s.status = 'ACTIVE'")
    List<AcademicStudent> findActiveByMobile(@Param("mobile") String mobile);


    /**
     * Finds all active AcademicStudents linked to a FamilyAccount (via student FK).
     * Used at login time — replaces the fragile mobile1 string match.
     */
    @Query("SELECT a FROM AcademicStudent a " +
           "JOIN FETCH a.student s " +
           "JOIN FETCH s.userEntity u " +
           "JOIN FETCH a.school sc " +
           "JOIN FETCH a.academicYear ay " +
           "JOIN FETCH a.grade g " +
           "JOIN FETCH a.section sec " +
           "JOIN FETCH a.medium m " +
           "WHERE s.familyAccount = :familyAccount AND a.status = 'Active' AND s.status = 'ACTIVE'")
    List<AcademicStudent> findActiveByFamilyAccount(@Param("familyAccount") FamilyAccount familyAccount);


    /**
     * PRIMARY child lookup at mobile login — via SiblingGroup.
     *
     * Finds all active AcademicStudents that belong to the same SiblingGroup
     * as any student whose parent mobile1 matches the login mobile.
     *
     * Logic:
     *   1. Find any active AcademicStudent where student.mobile1 = :mobile
     *   2. Get their SiblingGroup (via SiblingGroupStudent)
     *   3. Return ALL active AcademicStudents in that same SiblingGroup
     *
     * Returns empty list if no SiblingGroup is found → caller falls back to mobile1/FK.
     */
    @Query("SELECT DISTINCT a FROM AcademicStudent a " +
           "JOIN FETCH a.student s " +
           "JOIN FETCH s.userEntity u " +
           "JOIN FETCH a.school sc " +
           "JOIN FETCH a.academicYear ay " +
           "JOIN FETCH a.grade g " +
           "JOIN FETCH a.section sec " +
           "JOIN FETCH a.medium m " +
           "JOIN SiblingGroupStudent sgs ON sgs.academicStudent = a " +
           "WHERE sgs.siblingGroup IN (" +
           "  SELECT DISTINCT sgs2.siblingGroup FROM SiblingGroupStudent sgs2 " +
           "  WHERE sgs2.academicStudent.student.mobile1 = :mobile " +
           "  AND sgs2.academicStudent.status = 'Active' " +
           "  AND sgs2.siblingGroup.status = 'Active'" +
           ") AND a.status = 'Active' AND s.status = 'ACTIVE'")
    List<AcademicStudent> findSiblingsByMobile(@Param("mobile") String mobile);

}
