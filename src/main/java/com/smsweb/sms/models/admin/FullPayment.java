package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.universal.Grade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_full_payment_discount",columnNames = {"grade_id", "academic_year_id", "school_id"})})

public class FullPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    School school;

    /*@DateTimeFormat(pattern = "yyyy-MM-dd")*/
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    @NotNull(message = "Last payment date should be available")
    private Date paymentLastDate;

    @NotNull(message = "Discount amount must be present")
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "grade_id")
    @NotNull(message = "Grade should be available")
    private Grade grade;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    String description;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    @UpdateTimestamp
    private Date lastUpdated;

    @JoinColumn(name = "created_by", updatable = false)
    private String createdBy;

    @JoinColumn(name = "updated_by")
    private String updatedBy;
}
