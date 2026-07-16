package com.smsweb.sms.models.mobile;

import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * NEW entity (feature #10 — refresh tokens). Brand-new package (models.mobile),
 * additive only — no existing table/entity is touched.
 *
 * One row per mobile login "session". Because the JWT itself is scoped to a
 * specific AcademicStudent (a child, not just a parent), a parent with two
 * children logged in — or the same parent on two devices — will have
 * multiple rows here at once. That's expected.
 *
 * Rotation model: every successful POST /api/v1/auth/refresh call marks the
 * row it used as revoked and INSERTS a new row with a new token. A raw
 * refresh token is therefore single-use — reusing an already-rotated one is
 * a signal of token theft (the caller can react to that, e.g. by revoking
 * every row for that student).
 *
 * Only a SHA-256 hash of the raw token is stored (see MobileRefreshTokenService)
 * — never the raw value — same principle as password hashing: a DB leak alone
 * does not hand out usable tokens.
 */
@Entity
@Table(
    name = "mobile_refresh_tokens",
    indexes = {
        @Index(name = "idx_mrt_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_mrt_student", columnList = "academic_student_id"),
        @Index(name = "idx_mrt_cleanup", columnList = "revoked, expires_at")
    }
)
public class MobileRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_student_id", nullable = false)
    private AcademicStudent academicStudent;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public MobileRefreshToken() {}

    public MobileRefreshToken(AcademicStudent academicStudent, String tokenHash,
                               LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.academicStudent = academicStudent;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AcademicStudent getAcademicStudent() { return academicStudent; }
    public void setAcademicStudent(AcademicStudent academicStudent) { this.academicStudent = academicStudent; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
