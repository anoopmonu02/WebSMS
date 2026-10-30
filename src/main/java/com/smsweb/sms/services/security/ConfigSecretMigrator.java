package com.smsweb.sms.services.security;

import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.admin.DbBackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NEW component (feature: encrypted secrets in system_config). Once at startup, upgrades any
 * secret still stored as legacy plain text to the encrypted format, so the database stops
 * holding readable passwords as soon as the master key is configured — no manual migration.
 *
 * Safe by design: does nothing without a valid key; skips values already encrypted; one
 * failing row is logged (never the value) and never stops startup or the other rows.
 * Add a config key to SECRET_KEYS whenever a new secret setting is introduced.
 */
@Component
public class ConfigSecretMigrator {

    private static final Logger log = LoggerFactory.getLogger(ConfigSecretMigrator.class);

    private static final List<String> SECRET_KEYS = List.of(DbBackupService.CONFIG_EMAIL_PASSWORD);

    private final SystemConfigRepository systemConfigRepository;
    private final SecretCipher secretCipher;

    public ConfigSecretMigrator(SystemConfigRepository systemConfigRepository, SecretCipher secretCipher) {
        this.systemConfigRepository = systemConfigRepository;
        this.secretCipher = secretCipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void upgradePlainSecrets() {
        if (!secretCipher.isConfigured()) {
            log.warn("Skipping secret upgrade: SMS_CONFIG_ENCRYPTION_KEY is not configured.");
            return;
        }
        for (String name : SECRET_KEYS) {
            try {
                systemConfigRepository.findByConfigName(name).ifPresent(config -> {
                    String value = config.getConfigValue();
                    if (value != null && !value.isBlank() && !secretCipher.isEncrypted(value)) {
                        config.setConfigValue(secretCipher.encrypt(value));
                        systemConfigRepository.save(config);
                        log.info("Encrypted the stored value of system_config '{}'.", name);
                    }
                });
            } catch (Exception e) {
                log.error("Could not encrypt system_config '{}' — it stays unchanged: {}", name, e.getMessage());
            }
        }
    }
}
