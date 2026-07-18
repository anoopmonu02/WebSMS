package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.AttendanceConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceConfirmationRepository extends JpaRepository<AttendanceConfirmation, Long> {

    /** At most one row can exist per (school, academic year, day) — enforced by
     *  a unique constraint on the table, so this is always 0 or 1 rows. */
    Optional<AttendanceConfirmation> findBySchool_IdAndAcademicYear_IdAndAttendanceDate(
            Long schoolId, Long academicYearId, LocalDate attendanceDate);
}
