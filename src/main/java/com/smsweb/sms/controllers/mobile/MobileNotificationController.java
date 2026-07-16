package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.mobile.MobileNotificationReadService;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public MobileNotificationController(SmsMessageService smsMessageService,
                                         MobileNotificationReadService readService) {
        this.smsMessageService = smsMessageService;
        this.readService = readService;
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMessageList(List<SmsMessage> messages, Set<Long> readIds) {
        List<Map<String, Object>> result = new ArrayList<>();
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
            result.add(entry);
        }
        return result;
    }
}
