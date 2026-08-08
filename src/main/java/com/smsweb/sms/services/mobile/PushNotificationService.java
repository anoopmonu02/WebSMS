package com.smsweb.sms.services.mobile;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.smsweb.sms.models.mobile.FcmDeviceToken;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.mobile.FcmDeviceTokenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends push notifications via Firebase Cloud Messaging and owns the
 * FcmDeviceToken table (registration / lookup / cleanup of dead tokens).
 *
 * Every public method here no-ops safely if Firebase was never initialized
 * (see FirebaseConfig) — push notifications are a nice-to-have, not
 * something any existing feature depends on, so a missing/misconfigured
 * credential must never break a caller like SmsMessageService.saveSmsMessage.
 */
@Service
public class PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final FcmDeviceTokenRepository tokenRepository;

    public PushNotificationService(FcmDeviceTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    private boolean isEnabled() {
        return !FirebaseApp.getApps().isEmpty();
    }

    // ── Device registration (called from the mobile app after login) ────────

    /**
     * Registers (or moves) a device token to point at the given student.
     * Upserts on the token itself — a single physical device only ever gets
     * one row, re-pointed to whichever student it's currently logged in as
     * (e.g. after switching child on the same phone).
     */
    public void registerDevice(AcademicStudent academicStudent, String token) {
        if (token == null || token.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        FcmDeviceToken row = tokenRepository.findByToken(token).orElse(null);
        if (row == null) {
            tokenRepository.save(new FcmDeviceToken(academicStudent, token, now, now));
        } else {
            row.setAcademicStudent(academicStudent);
            row.setUpdatedAt(now);
            tokenRepository.save(row);
        }
    }

    /**
     * Unregisters a device token — but only if it currently belongs to the
     * calling student. Without this check, any authenticated mobile user
     * could unregister an arbitrary device token (e.g. a leaked/observed
     * one) and silently kill another family's push notifications.
     */
    public void unregisterDevice(String token, Long academicStudentId) {
        if (token == null || token.isBlank()) return;
        FcmDeviceToken row = tokenRepository.findByToken(token).orElse(null);
        if (row == null) return; // already gone — nothing to do
        if (row.getAcademicStudent() == null
                || !row.getAcademicStudent().getId().equals(academicStudentId)) {
            log.warn("unregisterDevice: token does not belong to academicStudentId={} — ignored", academicStudentId);
            return;
        }
        tokenRepository.deleteByToken(token);
    }

    // ── Sending ───────────────────────────────────────────────────────────

    // Data-payload "type" values — the Flutter app reads this to decide which
    // tab to jump to on tap (see push_notification_service.dart /
    // MainShell._onIssuesPush and friends). Keep these two lists in sync.
    public static final String TYPE_COMPLAINT     = "complaint";     // → Issues tab
    public static final String TYPE_NOTIFICATION  = "notification";  // → Home/Notices tab
    public static final String TYPE_FEE           = "fee";           // → Fees tab
    public static final String TYPE_RESULT        = "result";        // → Results tab (feature: result-declared push)

    /**
     * Sends the same title/body to every device registered for each of the
     * given students, tagged with [type] so the app knows which tab to open
     * on tap. Called from several places now (complaints, general notices,
     * absent-student notices, birthday notices, fee reminders, fee
     * submission confirmations, exam-result declarations) — some of those
     * callers are inside their own @Transactional method, so this must never
     * let an exception escape and roll back a save that already succeeded.
     * Every failure is logged and swallowed instead.
     *
     * Every push's data payload also carries the recipient's own
     * academicStudentId (student.getId()) — additive field, existing app
     * builds that don't read it are unaffected. Lets the Flutter app switch
     * to the right child before deep-linking, for a parent who has this
     * push's student as a sibling of, not currently, their active child.
     */
    public void sendToStudents(List<AcademicStudent> students, String title, String body, String type) {
        try {
            if (!isEnabled()) {
                log.debug("Push notifications disabled (Firebase not configured) — skipping send.");
                return;
            }
            if (students == null || students.isEmpty()) return;

            for (AcademicStudent student : students) {
                List<FcmDeviceToken> tokens = tokenRepository.findAllByAcademicStudent_Id(student.getId());
                for (FcmDeviceToken deviceToken : tokens) {
                    sendOne(deviceToken.getToken(), title, body, type, student.getId());
                }
            }
        } catch (Exception e) {
            log.warn("sendToStudents failed — push notification skipped, caller unaffected", e);
        }
    }

    /** Back-compat overload — defaults to the original "complaint" tagging. */
    public void sendToStudents(List<AcademicStudent> students, String title, String body) {
        sendToStudents(students, title, body, TYPE_COMPLAINT);
    }

    private void sendOne(String token, String title, String body, String type, Long academicStudentId) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", type != null ? type : TYPE_COMPLAINT);
        if (academicStudentId != null) {
            builder.putData("academicStudentId", String.valueOf(academicStudentId));
        }
        Message message = builder.build();
        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                // App was uninstalled, or the token is otherwise dead — stop trying it.
                log.info("Removing dead FCM token: {}", e.getMessagingErrorCode());
                tokenRepository.deleteByToken(token);
            } else {
                log.warn("Failed to send push notification", e);
            }
        }
    }
}
