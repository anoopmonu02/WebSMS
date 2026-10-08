package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.AdmissionBasis;
import com.smsweb.sms.models.tc.enums.LinkType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Links an old student record to the new record created when the student returns in a later
 * session (re-registration). A new record links to exactly one old record; a student can be
 * re-registered more than once, so timelines walk the chain in both directions. Plan section 5.7.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_link",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_link_uuid", columnNames = {"uuid"}),
                @UniqueConstraint(name = "uk_student_link_new_student", columnNames = {"new_student_id"})
        },
        indexes = {
                @Index(name = "idx_student_link_old_student", columnList = "old_student_id")
        })
public class StudentLink {

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
    @JoinColumn(name = "old_student_id", nullable = false, updatable = false)
    private Student oldStudent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_student_id", nullable = false, updatable = false)
    private Student newStudent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_academic_student_id", nullable = false, updatable = false)
    private AcademicStudent oldAcademicStudent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_academic_student_id", nullable = false, updatable = false)
    private AcademicStudent newAcademicStudent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "link_type", nullable = false, updatable = false, length = 20)
    private LinkType linkType = LinkType.RE_REGISTRATION;

    /** Registration numbers (SRN-...). */
    @Column(name = "old_registration_no", nullable = false, updatable = false, length = 50)
    private String oldRegistrationNo;

    @Column(name = "new_registration_no", nullable = false, updatable = false, length = 50)
    private String newRegistrationNo;

    @Column(name = "old_psrn", updatable = false)
    private Long oldPsrn;

    @Column(name = "new_psrn", updatable = false)
    private Long newPsrn;

    /** The "previous SR". The new SR is assigned later on the existing Assign SR screen. */
    @Column(name = "old_class_sr_no", updatable = false, length = 50)
    private String oldClassSrNo;

    /** TC used for re-registration, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tc_id", updatable = false)
    private TransferCertificate tc;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "admission_basis", nullable = false, updatable = false, length = 20)
    private AdmissionBasis admissionBasis;

    @Column(name = "remark", updatable = false, length = 500)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
