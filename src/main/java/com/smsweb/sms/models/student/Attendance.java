package com.smsweb.sms.models.student;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attendance",
        indexes = {
                @Index(name = "idx_attendance_student", columnList = "academic_student_id"),
                @Index(name = "idx_attendance_date", columnList = "attendance_date")
        })
//@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_attendance",columnNames = {"academic_student_id", "academic_year_id", "school_id", "attendance_date"})})
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_student_id")
    @NotNull(message = "Academic student should be available")
    private AcademicStudent academicStudent;

    @CreationTimestamp
    @Column(updatable = false)
    @JoinColumn(name = "attendance_date")
    private Date attendanceDate;

    @JsonProperty("isPresent")
    private boolean isPresent = false;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Remark should not exceed 500 characters")
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @Column(nullable = false)
    private String status = "Active";
    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;
    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";

    /*@JoinColumn(name = "created_by", updatable = false)
    private String createdBy;

    @JoinColumn(name = "updated_by")
    private String updatedBy;*/
    /*
     * Audit Fields
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    @Override
    public String toString() {
        return "Attendance{" +
                "id=" + id +
                ", present=" + isPresent +
                ", attendanceDate=" + attendanceDate +
                ", status='" + status + '\'' +
                '}';
    }

}
