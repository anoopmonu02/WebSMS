package com.smsweb.sms.models.admin;

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
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"monthMaster", "academicYear", "priority","school"})})
public class MonthMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "month_master_id")
    @NotNull(message = "Months should be available")
    private MonthMaster monthMaster;

    @NotNull(message = "Priority must be set")
    @Size(min = 1, max = 12, message = "Priority must be in 1-12")
    private int priority;

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
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;
}
