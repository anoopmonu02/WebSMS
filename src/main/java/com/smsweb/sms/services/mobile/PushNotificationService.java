package com.smsweb.sms.services.mobile;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.smsweb.sms.models.mobile.FcmDeviceToken;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.mobile.FcmDeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * @Transactional is required here (not on the private sendOne/cleanup
     * path — self-invoked private methods bypass Spring's proxy, so the
     * annotation has no effect there) because dead-token cleanup below does
     * a repository delete, which needs an active EntityManager transaction.
     * Without it, the very first dead token in a batch throws
     * TransactionRequiredException, which is only caught by this method's
     * own outer catch — aborting delivery to every remaining
     * student/device in the same call. (Root-caused 2026-08-12 from
     * production logs: a stale token from an app reinstall triggered this
     * exact abort, silently blocking the whole notification batch.)
     */
    @Transactional
    public void sendToStudents(List<AcademicStudent> students, String title, String body, String type) {
        try {
            if (!isEnabled()) {
                log.debug("Push notifications disabled (Firebase not configured) — skipping send.");
                return;
            }
            if (students == null || students.isEmpty()) return;

            log.info("Push send starting: type={} title={} recipientStudents={}",
                    type, title, students.size());

            int totalTokens = 0;
            int sent = 0;
            int failed = 0;
            int removed = 0;

            for (AcademicStudent student : students) {
                List<FcmDeviceToken> tokens = tokenRepository.findAllByAcademicStudent_Id(student.getId());
                if (tokens.isEmpty()) {
                    log.debug("No registered device tokens for studentId={} — nothing to send.", student.getId());
                    continue;
                }
                totalTokens += tokens.size();
                for (FcmDeviceToken deviceToken : tokens) {
                    switch (sendOne(deviceToken.getToken(), title, body, type, student.getId())) {
                        case SENT -> sent++;
                        case FAILED -> failed++;
                        case REMOVED -> removed++;
                    }
                }
            }

            log.info("Push send finished: type={} recipientStudents={} tokensAttempted={} sent={} failed={} deadTokensRemoved={}",
                    type, students.size(), totalTokens, sent, failed, removed);
        } catch (Exception e) {
            log.warn("sendToStudents failed — push notification skipped, caller unaffected", e);
        }
    }

    /** Back-compat overload — defaults to the original "complaint" tagging. */
    public void sendToStudents(List<AcademicStudent> students, String title, String body) {
        sendToStudents(students, title, body, TYPE_COMPLAINT);
    }

    /** Outcome of a single-token send attempt — rolled up into the summary log line in sendToStudents. */
    private enum SendResult { SENT, FAILED, REMOVED }

    private SendResult sendOne(String token, String title, String body, String type, Long academicStudentId) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", type != null ? type : TYPE_COMPLAINT);
        if (academicStudentId != null) {
            builder.putData("academicStudentId", String.valueOf(academicStudentId));
        }
        Message message = builder.build();
        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("Push sent OK: studentId={} type={} messageId={} token=...{}",
                    academicStudentId, type, messageId, tail(token));
            return SendResult.SENT;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                // App was uninstalled, or the token is otherwise dead — stop trying it.
                log.info("Push token dead, removing: studentId={} errorCode={} token=...{}",
                        academicStudentId, e.getMessagingErrorCode(), tail(token));
                try {
                    tokenRepository.deleteByToken(token);
                    return SendResult.REMOVED;
                } catch (Exception cleanupEx) {
                    // Never let a cleanup failure abort delivery to the rest of the
                    // batch — log it and move on; the dead row just gets retried
                    // (and re-logged) on the next send instead of blocking today's.
                    log.warn("Failed to remove dead FCM token (non-fatal — will retry cleanup next send): studentId={} token=...{}",
                            academicStudentId, tail(token), cleanupEx);
                    return SendResult.FAILED;
                }
            } else {
                log.warn("Push send failed: studentId={} type={} errorCode={} token=...{}",
                        academicStudentId, type, e.getMessagingErrorCode(), tail(token), e);
                return SendResult.FAILED;
            }
        }
    }

    /** Last 8 chars of a token, for log correlation without dumping the full value repeatedly. */
    private static String tail(String token) {
        if (token == null) return "";
        return token.length() <= 8 ? token : token.substring(token.length() - 8);
    }
}
