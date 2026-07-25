package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.messaging.SmsMessageAttachment;
import com.smsweb.sms.services.mobile.MobileNotificationReadService;
import com.smsweb.sms.services.mobile.PushNotificationService;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.student.AcademicStudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notice / Notification endpoints for the student mobile app.
 *
 * GET  /api/v1/notifications              — list of notices for this student
 * GET  /api/v1/notifications/unread-count — real unread count (feature #5)
 * POST /api/v1/notifications/{id}/read    — mark one notice as read
 * POST /api/v1/notifications/read-all     — mark all notices as read
 *
 * Uses SmsMessageService (existing, unchanged) for message lookup, and the
 * new MobileNotificationReadService (services.mobile) for read-state.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class MobileNotificationController {
    private static final Logger log = LoggerFactory.getLogger(MobileNotificationController.class);

    private final SmsMessageService smsMessageService;               // existing, unchanged
    private final MobileNotificationReadService readService;         // new, mobile-only
    private final PushNotificationService pushNotificationService;   // new, mobile-only — FCM device tokens
    private final AcademicStudentService academicStudentService;     // existing, unchanged
    private final FileHandleHelper fileHandleHelper;                 // existing (used by admin web attachments too)

    public MobileNotificationController(SmsMessageService smsMessageService,
                                         MobileNotificationReadService readService,
                                         PushNotificationService pushNotificationService,
                                         AcademicStudentService academicStudentService,
                                         FileHandleHelper fileHandleHelper) {
        this.smsMessageService = smsMessageService;
        this.readService = readService;
        this.pushNotificationService = pushNotificationService;
        this.academicStudentService = academicStudentService;
        this.fileHandleHelper = fileHandleHelper;
    }

    // ── POST /api/v1/notifications/register-device ───────────────────────────
    // Called by the mobile app right after login (and whenever the FCM token
    // refreshes) so the backend knows where to deliver push notifications for
    // whichever student this device is currently signed in as.

    @PostMapping("/register-device")
    public ResponseEntity<ApiResponse<Void>> registerDevice(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        log.info("Inside registerDevice");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        if (academicStudentId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }

        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("token is required"));
        }

        var academicStudent = academicStudentService.findById(academicStudentId).orElse(null);
        if (academicStudent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Student record not found"));
        }

        pushNotificationService.registerDevice(academicStudent, token);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── POST /api/v1/notifications/unregister-device ─────────────────────────
    // Called on logout so a shared/reused device stops receiving pushes meant
    // for the account that just signed out.

    @PostMapping("/unregister-device")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(@RequestBody Map<String, String> body) {
        log.info("Inside unregisterDevice");
        pushNotificationService.unregisterDevice(body.get("token"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── GET /api/v1/notifications ─────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getNotifications(
            HttpServletRequest request) {
        log.info("Inside getNotifications");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        List<SmsMessage> messages = smsMessageService.getNotificationsByStudentId(academicStudentId);
        Set<Long> readIds = readService.getReadMessageIds(academicStudentId);

        return ResponseEntity.ok(ApiResponse.success(buildMessageList(messages, readIds)));
    }

    // ── GET /api/v1/notifications/unread-count ────────────────────────────────

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            HttpServletRequest request) {
        log.info("Inside getUnreadCount");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        List<SmsMessage> messages = smsMessageService.getNotificationsByStudentId(academicStudentId);
        Set<Long> readIds = readService.getReadMessageIds(academicStudentId);

        long unread = messages.stream().filter(m -> !readIds.contains(m.getId())).count();

        Map<String, Object> countData = new LinkedHashMap<>();
        countData.put("count", unread);
        return ResponseEntity.ok(ApiResponse.success(countData));
    }

    // ── POST /api/v1/notifications/{id}/read ──────────────────────────────────

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            HttpServletRequest request) {
        log.info("Inside markAsRead — id={}", id);

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        boolean owns = smsMessageService.getNotificationsByStudentId(academicStudentId)
                .stream().anyMatch(m -> m.getId().equals(id));
        if (!owns) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Notification not found for this student"));
        }

        readService.markNotificationAsRead(id, academicStudentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── POST /api/v1/notifications/read-all ───────────────────────────────────

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(HttpServletRequest request) {
        log.info("Inside markAllAsRead");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        List<Long> visibleIds = smsMessageService.getNotificationsByStudentId(academicStudentId)
                .stream().map(SmsMessage::getId).collect(Collectors.toList());

        readService.markAllAsRead(academicStudentId, visibleIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── GET /api/v1/notifications/attachment/{id} ─────────────────────────────
    // Serves one notification attachment file to the mobile app. Ownership is
    // checked (the attachment's SmsMessage must actually be one this student
    // is a recipient of) before the file is streamed — this endpoint is
    // JWT-authenticated (student's own token) rather than session/role-based
    // like the admin-web equivalent (/message/attachment/{id}), since mobile
    // callers never have an ADMIN/TEACHER/etc. session.
    //
    // Per product decision (image/PDF/doc support in storage, but mobile UI
    // only *renders* images inline for now — non-image download comes later):
    // this endpoint itself doesn't restrict by content type, it's just a safe
    // file stream. The Flutter app is expected to only call it for jpg/png
    // attachments until download support for other types is built.

    @GetMapping("/attachment/{id}")
    public ResponseEntity<Resource> getAttachment(@PathVariable Long id, HttpServletRequest request) {
        log.info("Inside getAttachment (mobile) — id={}", id);

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        if (academicStudentId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<SmsMessageAttachment> attOpt = smsMessageService.findAttachmentById(id);
        if (attOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SmsMessageAttachment att = attOpt.get();

        // Ownership check — same "is this student a recipient of this
        // message" logic already used by markAsRead() above, so a student
        // can't fetch another student's attachment just by guessing an id.
        boolean owns = smsMessageService.getNotificationsByStudentId(academicStudentId)
                .stream().anyMatch(m -> m.getId().equals(att.getSmsMessage().getId()));
        if (!owns) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            File file = fileHandleHelper.resolveMessageAttachmentFile(att.getStoredFileName());
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(file);
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(att.getContentType());
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            String disposition = att.isImage() ? "inline" : "attachment";
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + att.getOriginalFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve attachment id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMessageList(List<SmsMessage> messages, Set<Long> readIds) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Same N+1-safe batch lookup used by the admin-web notification DTO
        // builder (SmsMessageService.getNotificationDtosByStudentId) — one
        // query for every message's attachments, not one per row.
        List<Long> messageIds = messages.stream().map(SmsMessage::getId).collect(Collectors.toList());
        Map<Long, List<SmsMessageAttachment>> attachmentsByMessageId =
                smsMessageService.getAttachmentsGroupedByMessageId(messageIds);

        for (SmsMessage m : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",            m.getId());
            entry.put("title",         m.getSmsHeading());
            entry.put("heading",       m.getSmsHeading());
            entry.put("body",          m.getConversations() != null && !m.getConversations().isEmpty()
                                           ? m.getConversations().get(0).getContent() : "");
            entry.put("createdAt",     m.getCreatedAt());
            entry.put("recipientType", m.getRecipientType());
            entry.put("type",          m.getMessageType());
            entry.put("isRead",        readIds.contains(m.getId()));
            if (m.getConversations() != null && !m.getConversations().isEmpty()) {
                entry.put("previewText", m.getConversations().get(0).getContent());
            } else {
                entry.put("previewText", "");
            }

            // New, additive field — empty list for every existing notice
            // (and always for Complaint/Activities rows, which never get
            // attachments). Existing mobile app builds that don't know about
            // this field simply ignore the extra JSON key; nothing about the
            // other fields changes.
            List<SmsMessageAttachment> msgAttachments = attachmentsByMessageId.getOrDefault(m.getId(), Collections.emptyList());
            List<Map<String, Object>> attachmentList = new ArrayList<>();
            for (SmsMessageAttachment att : msgAttachments) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", att.getId());
                a.put("originalFileName", att.getOriginalFileName());
                a.put("contentType", att.getContentType());
                a.put("image", att.isImage());
                a.put("fileSize", att.getFileSize());
                a.put("url", "/api/v1/notifications/attachment/" + att.getId());
                attachmentList.add(a);
            }
            entry.put("attachments", attachmentList);

            result.add(entry);
        }
        return result;
    }
}
