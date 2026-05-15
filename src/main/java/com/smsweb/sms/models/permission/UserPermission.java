package com.smsweb.sms.models.permission;

import com.smsweb.sms.models.Users.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "screen_id"}))
@Getter @Setter @NoArgsConstructor
public class UserPermission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private AppScreen screen;

    /**
     * The set of access types granted to this user for this screen.
     * Stored in a separate join table: user_permission_access_types(permission_id, access_type).
     * An empty set means NOTHING (no access).
     * A set containing all four = full CRUD (equivalent to the old ALL).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_permission_access_types",
            joinColumns = @JoinColumn(name = "permission_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private Set<AccessType> accessTypes = new HashSet<>();

    // ── Convenience helpers ───────────────────────────────────────────────────

    public boolean canView()   { return accessTypes.contains(AccessType.VIEW);   }
    public boolean canCreate() { return accessTypes.contains(AccessType.CREATE); }
    public boolean canEdit()   { return accessTypes.contains(AccessType.EDIT);   }
    public boolean canDelete() { return accessTypes.contains(AccessType.DELETE); }

    public boolean hasFullAccess() {
        return accessTypes.containsAll(EnumSet.of(
                AccessType.VIEW, AccessType.CREATE, AccessType.EDIT, AccessType.DELETE));
    }

    public boolean hasNoAccess() { return accessTypes.isEmpty(); }
}
