package com.smsweb.sms.models.grievance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Dedicated model for the "Grievance" message type (formerly "Activities").
 * Kept as its own table — deliberately NOT layered onto the shared SmsMessage
 * table (which also backs Complaint/Notification and has no academic_year_id
 * column) — so grievance-specific fields (dueDate, closerStatementRemark,
 * closedAt) and scoped queries stay simple and don't touch the shared model.
 */
@Entity
@Table(name = "grievance")
@Getter
@Setter
public class Grievance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_student_id", nullable = false)
    @NotNull(message = "Student should be available")
    private AcademicStudent academicStudent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    @NotNull(message = "Academic-Year should be available")
    private AcademicYear academicYear;

    @Column(nullable = false, columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Column(name = "due_date", nullable = false)
    private Date dueDate;

    /**
     * Filled only when the grievance is closed. The close endpoint requires this
     * to be non-blank before it will set closedAt — so a non-null value here always
     * means the record was closed with a remark (there's no "closed with no remark" state).
     */
    @Column(name = "closer_statement_remark", nullable = true, columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String closerStatementRemark;

    /**
     * Null = still open/pending. Non-null = closed (and is the source of truth for
     * "closed" state — due-date reschedule and re-closing are both blocked once set).
     */
    @Column(name = "closed_at", nullable = true)
    private Date closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by", nullable = true)
    @JsonIgnore
    private UserEntity closedBy;
}
