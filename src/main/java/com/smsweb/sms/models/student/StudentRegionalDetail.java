package com.smsweb.sms.models.student;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smsweb.sms.models.Users.UserEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

/**
 * Regional-language (e.g. Hindi) counterpart of a Student's name/address fields.
 *
 * Kept as a separate, parallel table (one row per Student, keyed by student_id)
 * rather than adding columns to the core `students` table:
 *   - Isolates a feature that is only ever written via the admin bulk-Excel
 *     screen from the day-to-day student registration/edit flows.
 *   - Leaves room to add more government-mandated regional-language columns
 *     later without touching the heavily-used `students` table.
 *
 * Only ever populated/edited through the Admin Config > Regional Language
 * Details screen (ROLE_ADMIN / ROLE_SUPERADMIN only).
 */
@Getter
@Setter
@Entity
@Table(name = "student_regional_detail")
public class StudentRegionalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Student student;

    @Size(max = 200, message = "Student name (regional) should not exceed 200 characters")
    @Column(name = "student_name_regional")
    private String studentNameRegional;

    @Size(max = 200, message = "Father's name (regional) should not exceed 200 characters")
    @Column(name = "father_name_regional")
    private String fatherNameRegional;

    @Size(max = 200, message = "Mother's name (regional) should not exceed 200 characters")
    @Column(name = "mother_name_regional")
    private String motherNameRegional;

    @Size(max = 500, message = "Address (regional) should not exceed 500 characters")
    @Column(name = "address_regional", columnDefinition = "TEXT")
    private String addressRegional;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "created_by", updatable = false)
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;
}
