package com.smsweb.sms.models.mobile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

/**
 * NEW, isolated entity (brand-new table) — one row per AcademicStudent
 * (i.e. per physical student, per enrolled academic year). Backs the
 * student self-service "health info" fields (height/weight/health issues)
 * added to the mobile profile-edit feature.
 *
 * Deliberately its own table rather than new columns on Student or
 * AcademicStudent: a student's height/weight/health status naturally
 * changes year to year, so this is scoped per-enrollment-year, and keeping
 * it separate means zero changes to the shared Student/AcademicStudent
 * entities or the services/repositories built around them.
 *
 * academicYearId / schoolId are a denormalised snapshot copied once from
 * the owning AcademicStudent at creation time (never independently
 * edited) — purely so admin reporting ("all students with health issues
 * in School X, Year Y") doesn't need a join back to academic_students.
 */
@Getter
@Setter
@Entity
@Table(name = "student_health_info")
public class StudentHealthInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false, unique = true)
    @JsonIgnore
    private AcademicStudent academicStudent;

    /** Snapshot only — set once at creation, never updated afterwards. */
    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private Long academicYearId;

    /** Snapshot only — set once at creation, never updated afterwards. */
    @Column(name = "school_id", nullable = false, updatable = false)
    private Long schoolId;

    private Integer height;
    private Integer weight;

    @Column(nullable = false)
    private Boolean haveHealthIssues = false;

    @Column(nullable = false)
    private Boolean haveEyeIssue = false;

    @Column(columnDefinition = "TEXT")
    private String healthIssueDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
}
