package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance,Long> {

    List<Attendance> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    @Query("SELECT a.academicStudent.medium.mediumName, a.academicStudent.grade.gradeName, " +
            "       a.academicStudent.section.sectionName, " +
            "       SUM(CASE WHEN a.isPresent = true THEN 1 ELSE 0 END), " +
            "       SUM(CASE WHEN a.isPresent = false THEN 1 ELSE 0 END) " +
            "FROM Attendance a " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId and FUNCTION('DATE', a.attendanceDate)=current_date " +
            "GROUP BY a.academicStudent.medium.mediumName, a.academicStudent.grade.gradeName, a.academicStudent.section.sectionName")
    List<Object[]> findAttendanceSummaryBySchoolAndAcademicYear(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query("SELECT a.academicStudent.medium.mediumName, a.academicStudent.grade.gradeName, " +
            "       SUM(CASE WHEN a.isPresent = true THEN 1 ELSE 0 END), " +
            "       SUM(CASE WHEN a.isPresent = false THEN 1 ELSE 0 END) " +
            "FROM Attendance a " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId and FUNCTION('DATE', a.attendanceDate)=current_date " +
            "GROUP BY a.academicStudent.medium.mediumName, a.academicStudent.grade.gradeName")
    List<Object[]> findAttendanceCollectedSummaryBySchoolAndAcademicYear(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query("SELECT a FROM Attendance a WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId AND a.academicStudent.grade.id = :gradeId AND a.academicStudent.section.id = :sectionId " +
           " AND FUNCTION('DATE', a.attendanceDate) = current_date AND a.academicStudent.status = 'ACTIVE' AND a.academicStudent.medium.id = :medium ")
    List<Attendance> findAllAttendanceSummaryForSchoolAndAcademicAndGrade(@Param("gradeId") Long gradeId, @Param("sectionId") Long sectionId, @Param("schoolId") Long schoolId,
                                                                          @Param("academicYearId") Long academicYearId, @Param("medium") Long medium);

    @Query("SELECT a FROM Attendance a WHERE a.academicStudent = :academicStudent " +
            "AND FUNCTION('DATE', a.attendanceDate) = FUNCTION('DATE', :attendanceDate)")
    Optional<Attendance> findByAcademicStudentAndAttendanceDate(
            @Param("academicStudent") AcademicStudent academicStudent,
            @Param("attendanceDate") Date attendanceDate
    );

    List<Attendance> findByAcademicStudentInAndAttendanceDateBetween(List<AcademicStudent> students, Date startDate, Date endDate);

    @Query("SELECT a.attendanceDate, " +
            "SUM(CASE WHEN a.isPresent = true THEN 1 ELSE 0 END) as presentCount, " +
            "SUM(CASE WHEN a.isPresent = false THEN 1 ELSE 0 END) as absentCount " +
            "FROM Attendance a " +
            "WHERE a.attendanceDate BETWEEN :startDate AND :endDate AND a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
            "AND a.academicStudent.grade.id = :gradeId AND a.academicStudent.section.id = :sectionId AND a.academicStudent.medium.id = :medium " +
            "GROUP BY a.attendanceDate " +
            "ORDER BY a.attendanceDate ASC")
    List<Object[]> fetchAttendanceSummaryByDate(@Param("startDate") Date startDate,
                                                @Param("endDate") Date endDate, @Param("schoolId") Long schoolId, @Param("gradeId") Long gradeId, @Param("sectionId") Long sectionId,
                                                @Param("academicYearId") Long academicYearId, @Param("medium") Long medium);

    int countAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_StatusAndIsPresentAndAttendanceDate(Long school, Long academic, String status, boolean present, Date currentDate);

    /*@Query("SELECT COUNT(a) FROM Attendance a " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId " +
            "AND a.isPresent = true AND a.academicStudent.status = :status " +
            "AND a.attendanceDate = CURRENT_DATE  AND a.academicStudent.student.gender = :gender")
    int countPresentStudentsByGenderToday(@Param("schoolId") Long school,
                                    @Param("academicYearId") Long academic,
                                    @Param("status") String status,
                                    @Param("gender") String gender);*/

    @Query(value =
            "SELECT COUNT(*) FROM attendance a " +
                    "JOIN academic_students ast ON a.academic_student_id = ast.id " +
                    "JOIN students s ON ast.student_id = s.id " +
                    "WHERE a.school_id = :schoolId " +
                    "AND a.academic_year_id = :academicYearId " +
                    "AND a.is_present = 1 " +
                    "AND ast.status = :status " +
                    "AND DATE(a.attendance_date) = CURDATE() " +
                    "AND s.gender = :gender",
            nativeQuery = true)
    int countPresentStudentsByGenderToday(@Param("schoolId") Long school,
                                          @Param("academicYearId") Long academic,
                                          @Param("status") String status,
                                          @Param("gender") String gender);

    /**
     * Fast existence check for any attendance rows for TODAY for the given school & academic year.
     * Uses a date-range so DB indexes on attendance_date can be used.
     */
    @Query(value = "SELECT EXISTS (" +
            "   SELECT 1 FROM attendance a " +
            "   WHERE a.school_id = :schoolId " +
            "   AND a.academic_year_id = :academicYearId " +
            "   AND a.attendance_date >= CURDATE() " +
            "   AND a.attendance_date < DATE_ADD(CURDATE(), INTERVAL 1 DAY)" +
            ")",
            nativeQuery = true)
    int existsAnyAttendanceForToday(@Param("schoolId") Long schoolId,
                                        @Param("academicYearId") Long academicYearId);


}
