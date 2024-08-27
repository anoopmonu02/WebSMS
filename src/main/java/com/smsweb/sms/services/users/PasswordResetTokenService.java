package com.smsweb.sms.services.users;

import com.smsweb.sms.models.Users.PasswordResetToken;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.users.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetToken createToken(UserEntity user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));  // 5 minutes expiry time
        return passwordResetTokenRepository.save(token);
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        return passwordResetTokenRepository.findByToken(token);
    }

    public PasswordResetToken findByUser(UserEntity user) {
        return passwordResetTokenRepository.findByUser(user);
    }

    // New method to delete an existing token
    public void delete(PasswordResetToken token) {
        passwordResetTokenRepository.delete(token);
    }
}
