package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.universal.Finehead;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_fine_amount",columnNames = {"finehead_id", "academic_year_id", "school_id"})})
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "finehead_id")
    @NotNull(message = "Fine should be available")
    Finehead finehead;

    @NotNull(message = "Frequency must be set")
    @Min(1)
    @Max(4)
    int frequency;
    //1-Monthly, 2-Quarterly, 3-Half-yearly, 4-Annually

    @NotNull(message = "Fine amount must be present")
    int fineAmount;
    @NotNull(message = "Max Calculated value must be set")
    int maxCalculated;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    String description;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    School school;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    @UpdateTimestamp
    private Date lastUpdated;

    //TODO - will add 2 more attributes - createdBy, updatedBy
}
