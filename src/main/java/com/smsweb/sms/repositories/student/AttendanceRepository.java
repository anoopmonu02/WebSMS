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

    @Query("SELECT a.academicStudent.grade.gradeName, " +
            "       a.academicStudent.section.sectionName, " +
            "       SUM(CASE WHEN a.isPresent = true THEN 1 ELSE 0 END), " +
            "       SUM(CASE WHEN a.isPresent = false THEN 1 ELSE 0 END) " +
            "FROM Attendance a " +
            "WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId and FUNCTION('DATE', a.attendanceDate)=current_date " +
            "GROUP BY a.academicStudent.grade.gradeName, a.academicStudent.section.sectionName")
    List<Object[]> findAttendanceSummaryBySchoolAndAcademicYear(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query("SELECT a FROM Attendance a WHERE a.school.id = :schoolId AND a.academicYear.id = :academicYearId AND a.academicStudent.grade.id = :gradeId AND a.academicStudent.section.id = :sectionId " +
           " AND FUNCTION('DATE', a.attendanceDate) = current_date AND a.academicStudent.status = 'ACTIVE'")
    List<Attendance> findAllAttendanceSummaryForSchoolAndAcademicAndGrade(@Param("gradeId") Long gradeId, @Param("sectionId") Long sectionId, @Param("schoolId") Long schoolId,
                                                                          @Param("academicYearId") Long academicYearId);

    @Query("SELECT a FROM Attendance a WHERE a.academicStudent = :academicStudent " +
            "AND FUNCTION('DATE', a.attendanceDate) = FUNCTION('DATE', :attendanceDate)")
    Optional<Attendance> findByAcademicStudentAndAttendanceDate(
            @Param("academicStudent") AcademicStudent academicStudent,
            @Param("attendanceDate") Date attendanceDate
    );

}
