package com.smsweb.sms.config.mobile;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Initializes the Firebase Admin SDK from a service-account JSON file on
 * startup, for sending push notifications (see PushNotificationService).
 *
 * Deliberately fails soft, not hard: push notifications are a nice-to-have
 * feature, not something the rest of the app depends on to function — unlike
 * app.jwt.secret or the DB password, a missing/bad Firebase credential here
 * should never take down the whole backend. If app.firebase.credentials.path
 * is blank or the file can't be read, this just logs a warning and leaves
 * Firebase uninitialized; PushNotificationService checks for that and no-ops
 * instead of throwing.
 *
 * The credentials file itself must NEVER be committed to source control —
 * point app.firebase.credentials.path (via FIREBASE_CREDENTIALS_PATH) at a
 * file that lives outside the repo, the same way the image-storage paths do.
 */
@Component
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${app.firebase.credentials.path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("app.firebase.credentials.path not set — push notifications are disabled.");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return; // already initialized (e.g. test context reload)
        }
        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized — push notifications enabled.");
        } catch (IOException e) {
            log.warn("Could not read Firebase credentials at {} — push notifications are disabled.",
                    credentialsPath, e);
        }
    }
}
