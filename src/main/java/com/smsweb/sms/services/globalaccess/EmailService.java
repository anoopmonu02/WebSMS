package com.smsweb.sms.services.globalaccess;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.io.File;
import java.util.Properties;

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

    /**
     * NEW (feature: Database Backup). Sends from an account whose credentials come
     * from system_config (EMAIL_DB_BACKUP / EMAIL_DB_BKP_PASSWORD), not from the
     * app-wide spring.mail.* bean — those settings can change at runtime via the
     * Database Backup admin screen, so a fresh JavaMailSenderImpl is built per call
     * instead of reusing the singleton autoconfigured @Autowired mailSender above.
     *
     * @param attachment optional — pass null to send a plain notification email
     *                    (used when the backup zip is too large to attach).
     */
    public void sendBackupEmail(String fromEmail, String appPassword, String to, String subject, String body, File attachment) throws Exception {
        log.info("Inside sendBackupEmail - to={}, hasAttachment={}", to, attachment != null);
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(fromEmail);
        sender.setPassword(appPassword);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null);
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body);
        if (attachment != null) {
            helper.addAttachment(attachment.getName(), attachment);
        }
        sender.send(message);
    }
}
