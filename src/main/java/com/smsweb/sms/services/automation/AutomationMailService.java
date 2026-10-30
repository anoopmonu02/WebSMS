package com.smsweb.sms.services.automation;

import com.smsweb.sms.services.security.SecretCipher;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * Sends the automatic emails (attendance reminder, absence report, database backup, test email) through the
 * school's own SMTP account from the Email Account tab. A fresh sender is built per call so a changed account
 * takes effect immediately. {@code EmailService.sendBackupEmail} is left untouched (legacy fallback).
 */
@Service
public class AutomationMailService {

    private static final Logger log = LoggerFactory.getLogger(AutomationMailService.class);

    private final SecretCipher secretCipher;

    public AutomationMailService(SecretCipher secretCipher) {
        this.secretCipher = secretCipher;
    }

    /**
     * @param to         receiver addresses (at least one)
     * @param html       true when {@code body} is an HTML fragment, false for plain text
     * @param attachment optional file to attach (database backup zip), may be null
     * @throws IllegalStateException when the account is not set up or its password cannot be decrypted
     * @throws Exception             when the SMTP send itself fails
     */
    public void send(AutomationSettings account, List<String> to, String subject, String body, boolean html, File attachment) throws Exception {
        if (account == null || !account.isAccountReady()) {
            throw new IllegalStateException("The email account is not set up yet. Please fill in the Email Account tab.");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalStateException("No receiver email is set.");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getMailHost());
        sender.setPort(account.getMailPort());
        Properties props = sender.getJavaMailProperties();
        if (account.isMailAuth()) {
            sender.setUsername(account.getMailSender());
            sender.setPassword(secretCipher.decrypt(account.getMailPasswordEncrypted()));
            props.put("mail.smtp.auth", "true");
        } else {
            props.put("mail.smtp.auth", "false");
        }
        switch (account.getMailSecurity()) {
            case AutomationSettings.SECURITY_SSL:
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
                break;
            case AutomationSettings.SECURITY_NONE:
                break;
            default:
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        String timeoutMs = String.valueOf(account.getMailTimeoutSeconds() * 1000);
        props.put("mail.smtp.connectiontimeout", timeoutMs);
        props.put("mail.smtp.timeout", timeoutMs);
        props.put("mail.smtp.writetimeout", timeoutMs);

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
        if (account.getMailFromName().isBlank()) {
            helper.setFrom(account.getMailSender());
        } else {
            helper.setFrom(account.getMailSender(), account.getMailFromName());
        }
        helper.setTo(to.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(body, html);
        if (attachment != null) {
            helper.addAttachment(attachment.getName(), attachment);
        }
        sender.send(message);
        log.info("Automation email sent: school={}, receivers={}, subject={}", account.getSchoolId(), to.size(), subject);
    }

    public void send(AutomationSettings account, List<String> to, String subject, String body, boolean html) throws Exception {
        send(account, to, subject, body, html, null);
    }
}
