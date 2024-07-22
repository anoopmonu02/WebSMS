package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.MonthMaster;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_feemonthmap",columnNames = {"month_master_id", "feehead_id", "academic_year_id", "school_id"})})
public class FeeMonthMap {

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

    @ManyToOne
    @JoinColumn(name = "feehead_id")
    @NotNull(message = "Feehead should be available")
    private Feehead feehead;

    @ManyToOne
    @JoinColumn(name = "month_master_id")
    @NotNull(message = "Month should be available")
    private MonthMaster monthMaster;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

    private Boolean isApplicable = false;

    //TODO-will add 2 more attributes - createdBy, updatedBy
}
