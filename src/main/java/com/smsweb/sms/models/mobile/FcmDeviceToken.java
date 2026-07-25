package com.smsweb.sms.models.mobile;

import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * NEW entity — one row per physical device that has registered for push
 * notifications, pointed at whichever student it's currently logged in as.
 *
 * The token itself is unique per row (not per student): if the same phone
 * switches from Child A to Child B, re-registering moves this same row's
 * academicStudent to B rather than creating a duplicate — a device should
 * only ever receive pushes for whichever child it's currently signed in as.
 * A family with two children on two separate phones simply gets two rows.
 *
 * Not a secret like MobileRefreshToken — this is just "where to deliver a
 * push," so it's stored as plain text, no hashing needed.
 */
@Entity
@Table(
    name = "fcm_device_tokens",
    indexes = {
        @Index(name = "idx_fdt_token", columnList = "token", unique = true),
        @Index(name = "idx_fdt_student", columnList = "academic_student_id")
    }
)
public class FcmDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false)
    private AcademicStudent academicStudent;

    @Column(name = "token", nullable = false, length = 255, unique = true)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FcmDeviceToken() {}

    public FcmDeviceToken(AcademicStudent academicStudent, String token,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.academicStudent = academicStudent;
        this.token = token;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AcademicStudent getAcademicStudent() { return academicStudent; }
    public void setAcademicStudent(AcademicStudent academicStudent) { this.academicStudent = academicStudent; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
