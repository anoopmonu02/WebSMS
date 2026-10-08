package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A class group (for example "Primary") that owns one TC register: its own TC format and its
 * own running TC number. Which grades belong to the group is stored in {@link ClassDivisionGrade}.
 * Plan section 5.1.
 *
 * <p>{@code nextNumber} is only changed while the row is locked (PESSIMISTIC_WRITE) inside the
 * TC issue transaction.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "class_division",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_class_division_uuid", columnNames = {"uuid"}),
                @UniqueConstraint(name = "uk_class_division_school_name", columnNames = {"school_id", "name"})
        })
public class ClassDivision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** One of the names in TC_FORMAT_NAMES_&lt;schoolId&gt; (validated in the service, not an enum). */
    @Column(name = "tc_format", nullable = false, length = 30)
    private String tcFormat;

    @Column(name = "start_number", nullable = false)
    private Long startNumber = 1L;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber = 1L;

    /** Active / Inactive (same plain-string style as the rest of the project). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "Active";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
