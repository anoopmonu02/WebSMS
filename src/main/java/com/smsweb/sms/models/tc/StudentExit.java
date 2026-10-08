package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per strike-off: the student's last class, the dues (estimated, then frozen at admin
 * approval), the approval, and the state of the exit. Plan section 5.3.
 *
 * <p>Only one open exit (EXITED or APPROVED) is allowed per student; the service enforces it
 * under a row lock. Lock order for every transaction: exit row, then school row, then class
 * division row.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_exit",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_exit_uuid", columnNames = {"uuid"})
        },
        indexes = {
                @Index(name = "idx_student_exit_school_year_status", columnList = "school_id, academic_year_id, status"),
                @Index(name = "idx_student_exit_student", columnList = "student_id"),
                @Index(name = "idx_student_exit_acad_student", columnList = "academic_student_id")
        })
public class StudentExit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    /** Session in which the strike-off happened. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    /** The enrollment the student left from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_student_id", nullable = false, updatable = false)
    private AcademicStudent academicStudent;

    // Snapshot of the last class (used for the TC and the class group lookup).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false, updatable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medium_id", nullable = false, updatable = false)
    private Medium medium;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exit_reason", nullable = false, length = 30)
    private ExitReason exitReason;

    /** Filled for LONG_ABSENCE. */
    @Column(name = "absent_days")
    private Integer absentDays;

    /** The "last date at school" chosen by the user. */
    @Column(name = "exit_date", nullable = false)
    private LocalDate exitDate;

    /** true = count the last date's month in the dues, false = only the months before it (D9). */
    @Column(name = "include_last_month", nullable = false)
    private boolean includeLastMonth = true;

    /** Dues estimate computed at strike-off while the student is still Active. */
    @Column(name = "calc_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal calcAmount = BigDecimal.ZERO;

    /** JSON string: months (and fee heads when available) with amounts. */
    @Column(name = "calc_breakdown", columnDefinition = "TEXT")
    private String calcBreakdown;

    /** true if an admin typed the amount because the calculation failed. */
    @Column(name = "dues_manual", nullable = false)
    private boolean duesManual = false;

    @Column(name = "dues_manual_reason", length = 255)
    private String duesManualReason;

    /** Set at admin approval. Money can be collected and the TC issued only when true. */
    @Column(name = "dues_frozen", nullable = false)
    private boolean duesFrozen = false;

    @Column(name = "frozen_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    /** JSON string, printed on the Due Clearance Receipt. */
    @Column(name = "frozen_breakdown", columnDefinition = "TEXT")
    private String frozenBreakdown;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    /** true only if strike-off itself turned the login off, so reactivation re-enables only that. */
    @Column(name = "login_disabled_by_exit", nullable = false)
    private boolean loginDisabledByExit = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ExitStatus status = ExitStatus.EXITED;

    @Column(name = "remark", length = 500)
    private String remark;

    // Approval (the green flag, admin only)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private UserEntity approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_remark", length = 500)
    private String approvalRemark;

    // Strike-off cancelled before a TC was issued (admin only)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private UserEntity cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
