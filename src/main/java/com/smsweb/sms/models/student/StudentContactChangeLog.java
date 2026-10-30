package com.smsweb.sms.models.student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit trail: one row each time a student's mobile1 is changed from the Fee Submission screen.
 * Plain id columns (no relations) so the log never blocks or cascades into other tables.
 */
@Entity
@Table(name = "student_contact_change_log",
       indexes = {@Index(name = "idx_sccl_student", columnList = "student_id")})
@Getter @Setter @NoArgsConstructor
public class StudentContactChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "academic_year_id")
    private Long academicYearId;

    @Column(name = "old_mobile", length = 20)
    private String oldMobile;

    @Column(name = "new_mobile", length = 20)
    private String newMobile;

    @Column(name = "old_family_account_id")
    private Long oldFamilyAccountId;

    @Column(name = "new_family_account_id")
    private Long newFamilyAccountId;

    @Column(name = "sessions_revoked", nullable = false)
    private boolean sessionsRevoked;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}
