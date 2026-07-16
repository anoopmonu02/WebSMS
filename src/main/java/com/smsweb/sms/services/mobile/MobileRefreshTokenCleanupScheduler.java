package com.smsweb.sms.services.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NEW component (feature #10). Runs MobileRefreshTokenService.cleanupNow()
 * automatically once a day. The exact same method is also callable on demand
 * from the new admin UI page (see MobileSessionCleanupController) — this
 * class is just the timer, all the real logic lives in the service.
 *
 * Requires @EnableScheduling on the main application class (added to
 * SmsApplication.java) — previously not present, since this is the first
 * scheduled task in the app.
 */
@Component
public class MobileRefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MobileRefreshTokenCleanupScheduler.class);

    @Autowired
    private MobileRefreshTokenService refreshTokenService;

    /** Runs every day at 2:15 AM server time. */
    @Scheduled(cron = "${app.mobile.refreshToken.cleanupCron:0 15 2 * * *}")
    public void runScheduledCleanup() {
        log.info("Scheduled mobile refresh token cleanup starting");
        int deleted = refreshTokenService.cleanupNow();
        log.info("Scheduled mobile refresh token cleanup finished — {} row(s) deleted", deleted);
    }
}
