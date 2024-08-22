package com.smsweb.sms.models.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.Discounthead;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;


@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_student_sibling_grp",columnNames = {"academic_student_id", "sibling_group_id", "academic_year_id", "school_id", "discount_head_id"})})
public class SiblingDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sibling_group_id")
    @NotNull(message = "Sibling-Group should be available")
    private SiblingGroup siblingGroup;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;
    private String status = "Active";

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-Year should be available")
    private AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "academic_student_id")
    @NotNull(message = "Student should be available")
    private AcademicStudent academicStudent;

    @ManyToOne
    @JoinColumn(name = "discount_class_id")
    @NotNull(message = "Discount-Class Map should be available")
    private DiscountClassMap discountClassMap;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
    //TODO-will add 2 more attributes - createdBy, updatedBy
}
