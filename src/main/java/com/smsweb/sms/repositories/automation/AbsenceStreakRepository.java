package com.smsweb.sms.repositories.automation;

import com.smsweb.sms.models.student.Attendance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only native queries for the long-absence report. Kept in its own repository so the existing
 * AttendanceRepository is not touched. MySQL 5.7 compatible (no CTE, no window functions).
 */
public interface AbsenceStreakRepository extends Repository<Attendance, Long> {

    /**
     * Active students who have been absent on at least {@code threshold} school days in a row, counted
     * from their last present day up to the latest marked day. Sundays and days marked as holiday are
     * ignored (they neither extend nor break a streak).
     *
     * Columns: 0 academic_student id, 1 student name, 2 grade, 3 section, 4 class serial no, 5 father name,
     * 6 mobile, 7 streak days, 8 absent since, 9 last absent day.
     */
    @Query(value =
            "SELECT ast.id, s.student_name, g.grade_name, sec.section_name, ast.class_sr_no, s.father_name, s.mobile1, " +
            "       COUNT(DISTINCT DATE(a.attendance_date)) AS streak_days, " +
            "       MIN(DATE(a.attendance_date)) AS absent_since, MAX(DATE(a.attendance_date)) AS last_absent " +
            "FROM attendance a " +
            "JOIN academic_students ast ON ast.id = a.academic_student_id " +
            "JOIN students s ON s.id = ast.student_id " +
            "JOIN grade g ON g.id = ast.grade_id " +
            "JOIN section sec ON sec.id = ast.section_id " +
            "LEFT JOIN ( " +
            "    SELECT p.academic_student_id AS sid, MAX(DATE(p.attendance_date)) AS last_present " +
            "    FROM attendance p " +
            "    WHERE p.school_id = :schoolId AND p.academic_year_id = :yearId AND p.is_present = 1 " +
            "      AND DAYOFWEEK(p.attendance_date) <> 1 " +
            "      AND (p.remark IS NULL OR LOWER(p.remark) NOT LIKE '%holiday%') " +
            "      AND (p.status IS NULL OR p.status <> 'Holiday') " +
            "    GROUP BY p.academic_student_id " +
            ") lp ON lp.sid = a.academic_student_id " +
            "WHERE a.school_id = :schoolId AND a.academic_year_id = :yearId AND a.is_present = 0 " +
            "  AND DAYOFWEEK(a.attendance_date) <> 1 " +
            "  AND (a.remark IS NULL OR LOWER(a.remark) NOT LIKE '%holiday%') " +
            "  AND (a.status IS NULL OR a.status <> 'Holiday') " +
            "  AND ast.status = 'Active' " +
            "  AND (lp.last_present IS NULL OR DATE(a.attendance_date) > lp.last_present) " +
            "GROUP BY ast.id, s.student_name, g.grade_name, sec.section_name, ast.class_sr_no, s.father_name, s.mobile1, lp.last_present " +
            "HAVING COUNT(DISTINCT DATE(a.attendance_date)) >= :threshold " +
            "ORDER BY streak_days DESC, g.grade_name, sec.section_name, s.student_name",
            nativeQuery = true)
    List<Object[]> findLongAbsentees(@Param("schoolId") Long schoolId,
                                     @Param("yearId") Long yearId,
                                     @Param("threshold") int threshold);

    /** Latest day attendance was marked for the school and year (a date value), or null when nothing is marked. */
    @Query(value = "SELECT MAX(DATE(a.attendance_date)) FROM attendance a " +
            "WHERE a.school_id = :schoolId AND a.academic_year_id = :yearId", nativeQuery = true)
    Object findLatestMarkedDate(@Param("schoolId") Long schoolId, @Param("yearId") Long yearId);
}
