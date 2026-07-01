package com.smsweb.sms.services.users;

import com.smsweb.sms.models.Users.PasswordResetToken;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.users.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class PasswordResetTokenService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);


    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetToken createToken(UserEntity user) {
        log.info("Inside createToken");
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5)); // Token expires in 5 minutes
        return passwordResetTokenRepository.save(token);
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        log.info("Inside findByToken");
        return passwordResetTokenRepository.findByToken(token);
    }

    public PasswordResetToken findByUser(UserEntity user) {
        log.info("Inside findByUser");
        return passwordResetTokenRepository.findByUser(user);
    }

    // Method to invalidate token after successful password reset
    public void invalidateToken(PasswordResetToken token) {
        log.info("Inside invalidateToken");
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    public void delete(PasswordResetToken token) {
        log.info("Inside delete");
        passwordResetTokenRepository.delete(token);
    }

    // Check if the token is valid (not expired and not used)
    public boolean isTokenValid(PasswordResetToken token) {
        return !token.isExpired() && !token.isUsed();
    }
}
