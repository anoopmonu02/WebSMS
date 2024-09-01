package com.smsweb.sms.models.Users;

import jakarta.persistence.*;
import lombok.Data;


import java.time.LocalDateTime;

@Entity
@Data
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private UserEntity user;

    private LocalDateTime expiryDate; // Expiry date of the token

    private boolean used = false; // Flag to track if the token has been used

    // Helper method to check if the token is expired
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}
