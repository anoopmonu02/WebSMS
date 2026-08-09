package com.smsweb.sms.services.globalaccess;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
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

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    // system_config keys for the backup email's SMTP timeouts — admin-tunable
    // (via direct system_config edit; no dedicated UI yet) so a network-specific
    // adjustment doesn't require a code change + rebuild + redeploy cycle. Falls
    // back to these same defaults (the values observed to work) if a row is
    // missing, so this never breaks a deployment that hasn't set them.
    private static final String CONFIG_CONNECT_TIMEOUT_MS = "EMAIL_DB_BACKUP_CONNECT_TIMEOUT_MS";
    private static final String CONFIG_READ_TIMEOUT_MS = "EMAIL_DB_BACKUP_READ_TIMEOUT_MS";
    private static final String CONFIG_WRITE_TIMEOUT_MS = "EMAIL_DB_BACKUP_WRITE_TIMEOUT_MS";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    private static final int DEFAULT_WRITE_TIMEOUT_MS = 60000;

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
        // Generous, admin-tunable timeouts — this sends a multi-MB DB dump as an
        // attachment, and on the customer's network a full send (TLS handshake +
        // upload + Gmail's final "250 OK") has been observed taking ~20-23s end to
        // end, exceeding the old 15s read timeout even though the send was otherwise
        // succeeding. This is a background action (manual "Run Now" or scheduled
        // job), not something a user waits on synchronously in the UI, so there's no
        // cost to allowing more time. Read from system_config on every call (not
        // cached) so a network-specific tweak takes effect immediately, same as the
        // DB Backup schedule cron.
        props.put("mail.smtp.connectiontimeout", String.valueOf(resolveTimeoutMs(CONFIG_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS)));
        props.put("mail.smtp.timeout", String.valueOf(resolveTimeoutMs(CONFIG_READ_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS)));
        props.put("mail.smtp.writetimeout", String.valueOf(resolveTimeoutMs(CONFIG_WRITE_TIMEOUT_MS, DEFAULT_WRITE_TIMEOUT_MS)));

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

    /**
     * Reads an integer millisecond timeout from system_config, falling back to
     * defaultMs if the row doesn't exist or its value isn't a valid integer — a
     * missing or malformed config can never break sending, it just behaves as if
     * unset.
     */
    private int resolveTimeoutMs(String configName, int defaultMs) {
        try {
            return systemConfigRepository.findByConfigName(configName)
                    .map(SystemConfig::getConfigValue)
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .orElse(defaultMs);
        } catch (NumberFormatException nfe) {
            log.warn("Invalid value for {} in system_config — using default {}ms", configName, defaultMs);
            return defaultMs;
        }
    }
}
