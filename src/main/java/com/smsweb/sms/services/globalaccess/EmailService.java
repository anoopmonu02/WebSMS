package com.smsweb.sms.services.globalaccess;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("akccoding@gmail.com");
            message.setTo(to);
            message.setSubject("Password Reset Request");
            message.setText("Click the link to reset your password: " + resetLink);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }
}
