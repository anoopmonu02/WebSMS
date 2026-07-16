package com.smsweb.sms.models.messaging;

import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-recipient read state for a mobile notification (feature #5).
 *
 * NEW entity — additive only. Does not touch SmsMessage, SmsConversation, or
 * the existing sms_message_recipients join table. Absence of a row for a
 * given (smsMessage, academicStudent) pair means "unread" — no backfill is
 * needed for historical messages.
 *
 * The index below is declared on the entity so it gets created automatically
 * wherever spring.jpa.hibernate.ddl-auto=update is active (e.g. local/dev).
 * In any environment where ddl-auto is not "update" (commonly disabled in
 * production for safety), run backend_01_migration_sms_message_read_status.sql
 * manually instead — it creates the same table/constraint/index explicitly.
 */
@Entity
@Table(
    name = "sms_message_read_status",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_smrs_message_student",
        columnNames = {"sms_message_id", "academic_student_id"}
    ),
    indexes = @Index(
        name = "idx_smrs_student_read",
        columnList = "academic_student_id, is_read"
    )
)
public class SmsMessageReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sms_message_id", nullable = false)
    private SmsMessage smsMessage;

    // Plain FK to AcademicStudent (no reverse navigation needed), mirroring
    // how sms_message_recipients already links the two.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false)
    private AcademicStudent academicStudent;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public SmsMessageReadStatus() {}

    public SmsMessageReadStatus(SmsMessage smsMessage, AcademicStudent academicStudent) {
        this.smsMessage = smsMessage;
        this.academicStudent = academicStudent;
        this.isRead = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SmsMessage getSmsMessage() { return smsMessage; }
    public void setSmsMessage(SmsMessage smsMessage) { this.smsMessage = smsMessage; }

    public AcademicStudent getAcademicStudent() { return academicStudent; }
    public void setAcademicStudent(AcademicStudent academicStudent) { this.academicStudent = academicStudent; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
