package com.smsweb.sms.models.student;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.models.Users.UserEntity; // Added UserEntity for createdBy and updatedBy
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "academic_students")  // Specify the table name explicitly
@ToString(exclude = {"createdBy", "updatedBy"})
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

    // Added createdBy and updatedBy fields
    // Metadata fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @JsonIgnore
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
}
