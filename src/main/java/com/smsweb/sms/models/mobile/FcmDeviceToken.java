package com.smsweb.sms.models.mobile;

import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per (physical device, student) pair. A single device token can
 * have several rows — one per child in the signed-in parent's family — so
 * that a push aimed at any sibling reaches this device, not just whichever
 * child is currently active in the app.
 *
 * Changed 2026-08-12: previously the token column was unique on its own,
 * meaning one device could only ever be registered against ONE student at a
 * time (whichever was last active), so a push targeted at a sibling the
 * parent wasn't currently viewing found zero rows and was never even
 * attempted. PushNotificationService.registerDevice() now upserts one row
 * per family member (via the same SiblingGroup-first, FamilyAccount-fallback
 * lookup MobileAuthController already uses for the Switch Student screen),
 * so "family" here correctly includes non-blood wards under one guardian's
 * mobile number, not just literal siblings.
 *
 * Not a secret like MobileRefreshToken — this is just "where to deliver a
 * push," so it's stored as plain text, no hashing needed.
 */
@Entity
@Table(
    name = "fcm_device_tokens",
    indexes = {
        @Index(name = "idx_fdt_token", columnList = "token"),
        @Index(name = "idx_fdt_student", columnList = "academic_student_id"),
        @Index(name = "idx_fdt_token_student", columnList = "token, academic_student_id", unique = true)
    }
)
public class FcmDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false)
    private AcademicStudent academicStudent;

    @Column(name = "token", nullable = false, length = 255)
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
