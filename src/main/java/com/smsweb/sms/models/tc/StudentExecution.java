package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only student timeline. Rows are never edited; a correction voids the row and adds a
 * new one. "Current state" is not stored here. Plan section 5.6.
 *
 * <p>{@code createdBy} is nullable only because SYSTEM or IMPORT events may have no user.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_execution",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_execution_uuid", columnNames = {"uuid"})
        },
        indexes = {
                @Index(name = "idx_student_execution_student", columnList = "student_id, event_date, id"),
                @Index(name = "idx_student_execution_school_event", columnList = "school_id, event_type, event_date"),
                @Index(name = "idx_student_execution_ref", columnList = "ref_type, ref_id")
        })
public class StudentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    /** Session in which it happened. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    /** Enrollment affected, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", updatable = false)
    private AcademicStudent academicStudent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private EventType eventType;

    /** Real-world date. */
    @Column(name = "event_date", nullable = false, updatable = false)
    private LocalDate eventDate;

    /** When it was saved (can differ from eventDate for backdated entries). */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exit_reason", updatable = false, length = 30)
    private ExitReason exitReason;

    // Class snapshot at that moment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", updatable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", updatable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medium_id", updatable = false)
    private Medium medium;

    @Column(name = "old_value", updatable = false, length = 255)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = 255)
    private String newValue;

    @Column(name = "remark", updatable = false, length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ref_type", updatable = false, length = 30)
    private RefType refType;

    @Column(name = "ref_id", updatable = false)
    private Long refId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", nullable = false, updatable = false, length = 10)
    private EventSource source = EventSource.WEB;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "record_status", nullable = false, length = 10)
    private RecordStatus recordStatus = RecordStatus.VALID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private UserEntity voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
