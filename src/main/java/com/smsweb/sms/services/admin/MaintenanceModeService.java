package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * NEW service (feature: mobile-app maintenance mode). Lets ROLE_ADMIN/ROLE_SUPERADMIN
 * flip a global switch, from the "App Config" admin screen, that instantly blocks every
 * mobile API request (/api/v1/**) with a 503 + admin-editable message, without touching
 * the web app at all — see MaintenanceModeFilter, which is wired only into the mobile
 * security chain (WebSecurityConfig.mobileApiSecurityFilterChain).
 *
 * Two rows in the existing global system_config table (same pattern as
 * BirthdayNotificationSettingsController):
 *   - MOBILE_APP_ACCESS            -> "ENABLED" or "DISABLED"
 *   - MOBILE_APP_MAINTENANCE_MSG   -> the message shown on the mobile MaintenanceScreen
 *
 * The current values are cached in memory (volatile fields) so the filter — which runs on
 * every single mobile request — never hits the DB. Writes go through the cache immediately
 * (write-through), and a periodic refresh (every 2 minutes) re-reads from the DB as a
 * safety net for multi-instance deployments where another instance made the change.
 *
 * Fail-open by design: if the DB is unreachable at startup or refresh, the last known good
 * in-memory value is kept (defaults to ENABLED on first-ever boot with no row yet) — a
 * transient DB hiccup must never accidentally lock every mobile user out.
 */
@Service
public class MaintenanceModeService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeService.class);

    public static final String CONFIG_KEY_ACCESS = "MOBILE_APP_ACCESS";
    public static final String CONFIG_KEY_MESSAGE = "MOBILE_APP_MAINTENANCE_MSG";

    private static final String VALUE_ENABLED = "ENABLED";
    private static final String VALUE_DISABLED = "DISABLED";

    public static final String DEFAULT_MESSAGE =
            "We're making a few improvements to serve you better. The app will be back shortly — your data is safe and nothing has changed.";

    private final SystemConfigRepository systemConfigRepository;

    // Cached, read on every mobile request via MaintenanceModeFilter — must stay cheap.
    private volatile boolean mobileAccessEnabled = true;
    private volatile String maintenanceMessage = DEFAULT_MESSAGE;

    public MaintenanceModeService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @PostConstruct
    public void init() {
        refreshFromDb();
    }

    /** Safety-net refresh for multi-instance deployments. Fail-open: keeps last known good
     *  values on any error rather than letting a transient DB issue affect the cache. */
    @Scheduled(fixedRate = 2 * 60 * 1000)
    public void refreshFromDb() {
        try {
            String accessValue = systemConfigRepository.findByConfigName(CONFIG_KEY_ACCESS)
                    .map(SystemConfig::getConfigValue)
                    .orElse(VALUE_ENABLED);
            mobileAccessEnabled = !VALUE_DISABLED.equalsIgnoreCase(accessValue.trim());

            String msgValue = systemConfigRepository.findByConfigName(CONFIG_KEY_MESSAGE)
                    .map(SystemConfig::getConfigValue)
                    .filter(v -> v != null && !v.isBlank())
                    .orElse(DEFAULT_MESSAGE);
            maintenanceMessage = msgValue;
        } catch (Exception e) {
            log.warn("Could not refresh maintenance-mode config from DB, keeping last known values (mobileAccessEnabled={})", mobileAccessEnabled, e);
        }
    }

    public boolean isMobileAccessEnabled() {
        return mobileAccessEnabled;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    /** Write-through: saves to DB then updates the in-memory cache immediately so the very
     *  next mobile request already sees the new state — no need to wait for the scheduled refresh. */
    public void setMobileAccessEnabled(boolean enabled) {
        SystemConfig config = systemConfigRepository.findByConfigName(CONFIG_KEY_ACCESS)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigName(CONFIG_KEY_ACCESS);
                    return c;
                });
        config.setConfigValue(enabled ? VALUE_ENABLED : VALUE_DISABLED);
        config.setDescription("Master switch for the mobile app's API access. When DISABLED, every /api/v1/** request is blocked with a 503 and the mobile app shows a maintenance screen. Set from the App Config admin screen.");
        systemConfigRepository.save(config);
        mobileAccessEnabled = enabled;
        log.info("Mobile app access set to {}", enabled ? VALUE_ENABLED : VALUE_DISABLED);
    }

    public void setMaintenanceMessage(String message) {
        String trimmed = (message == null || message.isBlank()) ? DEFAULT_MESSAGE : message.trim();
        SystemConfig config = systemConfigRepository.findByConfigName(CONFIG_KEY_MESSAGE)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigName(CONFIG_KEY_MESSAGE);
                    return c;
                });
        config.setConfigValue(trimmed);
        config.setDescription("Message shown on the mobile app's maintenance screen while MOBILE_APP_ACCESS is DISABLED.");
        systemConfigRepository.save(config);
        maintenanceMessage = trimmed;
        log.info("Mobile app maintenance message updated");
    }
}
