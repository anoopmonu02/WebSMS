package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.AttendanceConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceConfirmationRepository extends JpaRepository<AttendanceConfirmation, Long> {

    /** At most one row can exist per (school, academic year, day) — enforced by
     *  a unique constraint on the table, so this is always 0 or 1 rows. */
    Optional<AttendanceConfirmation> findBySchool_IdAndAcademicYear_IdAndAttendanceDate(
            Long schoolId, Long academicYearId, LocalDate attendanceDate);

    /** Batch lookup for a date range (month/year mobile views) — one query
     *  instead of looping the single-day finder above per day. A day with no
     *  row at all simply won't appear in the result, which callers must treat
     *  as "not confirmed" (absence of a row is NOT the same as isConfirmed=false,
     *  but both mean "don't show this day to parents yet"). */
    List<AttendanceConfirmation> findBySchool_IdAndAcademicYear_IdAndAttendanceDateBetween(
            Long schoolId, Long academicYearId, LocalDate startDate, LocalDate endDate);
}
