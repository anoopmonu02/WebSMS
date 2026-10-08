package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.TcStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Transfer Certificate that was issued and handed over (every issue and re-issue is a row).
 * A row exists only after the student collected the TC. Table name is {@code tc}. Plan section 5.5.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tc",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tc_uuid", columnNames = {"uuid"}),
                @UniqueConstraint(name = "uk_tc_division_number", columnNames = {"division_id", "tc_number"})
        },
        indexes = {
                @Index(name = "idx_tc_student", columnList = "student_id"),
                @Index(name = "idx_tc_exit", columnList = "exit_id")
        })
public class TransferCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exit_id", nullable = false, updatable = false)
    private StudentExit exit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_student_id", nullable = false, updatable = false)
    private AcademicStudent academicStudent;

    /** Register the number came from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "division_id", nullable = false, updatable = false)
    private ClassDivision division;

    @Column(name = "tc_number", nullable = false, updatable = false)
    private Long tcNumber;

    /** Copied from the division at issue time. */
    @Column(name = "tc_format", nullable = false, updatable = false, length = 30)
    private String tcFormat;

    /** Also the hand-over date. */
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "collected_by_name", nullable = false, length = 150)
    private String collectedByName;

    /** PARENT, GUARDIAN or STUDENT (validated in the service). */
    @Column(name = "collected_by_relation", nullable = false, length = 20)
    private String collectedByRelation;

    /** 1 = original; counts the TCs of this student. */
    @Column(name = "reissue_no", nullable = false)
    private Integer reissueNo = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_tc_id", updatable = false)
    private TransferCertificate previousTc;

    /** Null for the original; required for a re-issue. */
    @Column(name = "cause", length = 500)
    private String cause;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 15)
    private TcStatus status = TcStatus.VALID;

    /** Placeholder, still undecided by the customer. */
    @Column(name = "tc_category", length = 50)
    private String tcCategory;

    /** JSON string of the printed values (TEXT, not the JSON type, for MySQL 5.7 portability). */
    @Column(name = "tc_data", columnDefinition = "TEXT")
    private String tcData;

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
