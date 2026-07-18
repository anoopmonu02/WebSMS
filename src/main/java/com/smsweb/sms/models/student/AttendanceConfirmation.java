package com.smsweb.sms.models.student;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.Date;

/**
 * One row per (school, academic year, calendar day) — marks whether that
 * day's attendance has been reviewed by staff and is safe to show parents
 * in the mobile app. Attendance itself stays instantly visible to staff on
 * the admin side the moment it's marked (unchanged); this table only gates
 * what MobileAttendanceController's parent-facing endpoints return once
 * that filtering is wired in.
 *
 * Deliberately a single upserted row per day, not an append-only log — a
 * unique constraint enforces "only 1 entry per day" at the DB level, and
 * a repeat confirm action just overwrites who/when on the same row.
 * isConfirmed is a real boolean column (not just row existence) so
 * un-confirming later, if ever needed, is just flipping this flag — no
 * schema change or delete logic required.
 *
 * attendanceDate is deliberately java.time.LocalDate (SQL DATE, no time
 * component) rather than java.util.Date — this row only ever needs to
 * identify a calendar day, so there's no timezone-instant ambiguity to get
 * wrong here, unlike the attendance_date timezone bug fixed earlier this
 * session. LocalDate.now() (used when writing this) relies on the JVM
 * default timezone being pinned to Asia/Kolkata (SmsApplication.main()).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "attendance_confirmation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_confirmation_day",
                columnNames = {"school_id", "academic_year_id", "attendance_date"}))
public class AttendanceConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "is_confirmed", nullable = false)
    private boolean isConfirmed = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private Date creationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private Date lastUpdated;
}
