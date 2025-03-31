package com.smsweb.sms.models.admin;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Entity
@Data
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_holiday",columnNames = {"holidayName", "academic_year_id", "school_id"})})
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    @UpdateTimestamp
    private Date lastUpdated;

    @NotBlank(message = "Holiday should not blank")
    @Size(max = 200, message = "Holiday should not exceed 200 chars")
    private String holidayName;
    @NotNull(message = "Holiday start date is mandatory")
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date holidayStartDate;
    @NotNull(message = "Holiday end date is mandatory")
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date holidayEndDate;
    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

}
