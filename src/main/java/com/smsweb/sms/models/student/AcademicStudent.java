package com.smsweb.sms.models.student;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "academic_students")  // Specify the table name explicitly

public class AcademicStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-Year should be available")
    private AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "student_id")
    @NotNull(message = "Student should be available")
    private Student student;

    @ManyToOne
    @JoinColumn(name = "medium_id")
    @NotNull(message = "Medium should be available")
    private Medium medium;

    @ManyToOne
    @JoinColumn(name = "grade_id")
    @NotNull(message = "Grade should be available")
    private Grade grade;

    @ManyToOne
    @JoinColumn(name = "section_id")
    @NotNull(message = "Section should be available")
    private Section section;

    @CreationTimestamp
    @Column(updatable = false)
    private Date migrationDate;

    private String classSrNo;
    private String boardSrNo;
    private String rollNo;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 characters")
    private String description;

    @Column(nullable = false)
    private String status = "Active";

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;

    /*@JoinColumn(name = "created_by", updatable = false)
    private String createdBy;

    @JoinColumn(name = "updated_by")
    private String updatedBy;*/

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    // Set uuid on create
    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    // Update the lastUpdated field on updates
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = new Date();
    }

    /**
     * Opening balance carried forward from before this system was used,
     * or from the previous academic year during year-end promotion.
     * Defaults to 0. Only read by the fee submission form when no
     * FeeSubmissionBalance row exists yet for this student in this year.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** Optional remark for the opening balance (e.g. "Balance from 2023-24") */
    @Size(max = 500)
    private String openingBalanceRemark;

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";
}
