package com.smsweb.sms.models.permission;

import com.smsweb.sms.models.Users.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "user_permission",
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType = AccessType.NOTHING;
}