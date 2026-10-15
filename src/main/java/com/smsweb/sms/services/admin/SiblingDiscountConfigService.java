package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NEW service (feature: configurable number of students per sibling group who can receive
 * the Sibling Discount). One row per school in the existing system_config table:
 *
 *   config_name  = SIBLING_DISCOUNT_ALLOWED_PER_GROUP_{schoolId}   (e.g. ..._2)
 *   config_value = "1" .. "3"
 *
 * Same per-school naming precedent as PSRN_START_{schoolId}. Edited from the Discount tab
 * of the App Config admin screen.
 *
 * Reading is fail-safe: a missing, blank, non-numeric or out-of-range value falls back to
 * DEFAULT_ALLOWED (1), which is the existing business rule (only one student per group gets
 * the discount) - so a school that never touches this setting behaves exactly as before.
 *
 * NOTE: this service only stores/reads the setting. Enforcement during discount assignment
 * is a separate step (SiblingDiscountService.save).
 */
@Service
public class SiblingDiscountConfigService {

    private static final Logger log = LoggerFactory.getLogger(SiblingDiscountConfigService.class);

    public static final String CONFIG_KEY_PREFIX = "SIBLING_DISCOUNT_ALLOWED_PER_GROUP_";
    public static final int MIN_ALLOWED = 1;
    public static final int MAX_ALLOWED = 3;
    public static final int DEFAULT_ALLOWED = 1;

    private final SystemConfigRepository systemConfigRepository;

    public SiblingDiscountConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public static String configKey(Long schoolId) {
        return CONFIG_KEY_PREFIX + schoolId;
    }

    /** Number of students per sibling group allowed to hold the Sibling Discount for this school. */
    public int getAllowedPerGroup(Long schoolId) {
        if (schoolId == null) {
            return DEFAULT_ALLOWED;
        }
        return systemConfigRepository.findByConfigName(configKey(schoolId))
                .map(SystemConfig::getConfigValue)
                .map(this::parseOrDefault)
                .orElse(DEFAULT_ALLOWED);
    }

    /**
     * @throws IllegalArgumentException if schoolId is null or value is outside MIN_ALLOWED..MAX_ALLOWED
     */
    @Transactional
    public void setAllowedPerGroup(Long schoolId, int value) {
        if (schoolId == null) {
            throw new IllegalArgumentException("School is required.");
        }
        if (value < MIN_ALLOWED || value > MAX_ALLOWED) {
            throw new IllegalArgumentException("Value must be between " + MIN_ALLOWED + " and " + MAX_ALLOWED + ".");
        }
        String key = configKey(schoolId);
        SystemConfig config = systemConfigRepository.findByConfigName(key)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigName(key);
                    return c;
                });
        config.setConfigValue(String.valueOf(value));
        config.setSchoolIds(String.valueOf(schoolId));
        config.setDescription("Number of students in one sibling group who can receive the Sibling Discount, for school "
                + schoolId + " (allowed " + MIN_ALLOWED + "-" + MAX_ALLOWED + "). Set from the App Config > Discount tab.");
        systemConfigRepository.save(config);
        log.info("Sibling discount allowed-per-group set to {} for school {}", value, schoolId);
    }

    private int parseOrDefault(String raw) {
        try {
            int v = Integer.parseInt(raw == null ? "" : raw.trim());
            return (v >= MIN_ALLOWED && v <= MAX_ALLOWED) ? v : DEFAULT_ALLOWED;
        } catch (NumberFormatException e) {
            return DEFAULT_ALLOWED;
        }
    }
}
