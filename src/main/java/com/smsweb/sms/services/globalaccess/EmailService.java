package com.smsweb.sms.services.globalaccess;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);


    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("Inside sendPasswordResetEmail");
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
