package com.smsweb.sms.models.student;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One login record per unique parent mobile number.
 *
 * Completely separate from student UserEntity accounts.
 * All students sharing the same mobile1 are authenticated via this single record.
 *
 * Default password set by school admin at student registration time.
 * Format: UA@<last4digits>   e.g. mobile 9876543210 → password UA@3210
 *
 * mustChangePassword = true forces the parent to set a new password
 * on their very first login through the app.
 */
@Entity
@Table(name = "family_accounts",
       uniqueConstraints = @UniqueConstraint(columnNames = "mobile"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FamilyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String mobile;

    /** BCrypt-hashed password. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * When true, the mobile app must prompt the parent to change their
     * password before reaching the dashboard.
     */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;

    /** ACTIVE / INACTIVE */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Read-only inverse side of Student.familyAccount — no new column, the FK
     * (family_account_id) already lives on the students table. Added purely so
     * the admin-facing "Mobile Users" screen can look up which students belong
     * to a family account without a separate query. Nothing existing reads this
     * field, so it can't change any current behaviour; @Builder.Default keeps
     * FamilyAccountService.createIfAbsent()'s builder-based construction safe
     * (without it, Lombok's builder would leave this null instead of empty).
     */
    @OneToMany(mappedBy = "familyAccount", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Student> students = new ArrayList<>();
}
