package com.smsweb.sms.models.mobile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Bank;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * NEW, isolated, append-only audit table. One row is written every time a
 * student changes any of their own bank details via the mobile self-service
 * profile-edit feature — never updated or deleted afterwards. Purely a
 * safety net (bank details feed fee refunds/reimbursements elsewhere in the
 * system) so a wrong entry can always be traced: what it was, who changed
 * it, and when.
 *
 * Not shown anywhere in the app UI today — queryable directly against the
 * DB if ever needed. An admin screen could be added later without any
 * rework here.
 */
@Getter
@Setter
@Entity
@Table(name = "bank_change_log")
public class BankChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false)
    @JsonIgnore
    private AcademicStudent academicStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    @JsonIgnore
    private UserEntity changedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_bank_id")
    private Bank oldBank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_bank_id")
    private Bank newBank;

    private String oldAccountNo;
    private String newAccountNo;

    private String oldBranchName;
    private String newBranchName;

    private String oldIfscCode;
    private String newIfscCode;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private Date changedAt;
}
