package com.smsweb.sms.models.admin;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_examdetails",columnNames = {"examination_id", "academic_year_id", "school_id"})})
public class ExamDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "examination_id")
    @NotNull(message = "Examination should be available")
    private Examination examination;

    @NotNull(message = "Exam date should be available")
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date examDeclaredDate;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Remark should not exceed 500 characters")
    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;
    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
