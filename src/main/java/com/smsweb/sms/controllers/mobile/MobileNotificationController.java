package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Notice / Notification endpoints for the student mobile app.
 *
 * GET /api/v1/notifications              — list of notices for this student
 * GET /api/v1/notifications/unread-count — count of notifications (v1: all treated as unread)
 *
 * Uses SmsMessageService (existing service):
 *  - getNotificationsByStudentId()  ← already exists in SmsMessageService
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class MobileNotificationController {
    private static final Logger log = LoggerFactory.getLogger(MobileNotificationController.class);


    private final SmsMessageService smsMessageService;

    public MobileNotificationController(SmsMessageService smsMessageService) {
        this.smsMessageService = smsMessageService;
    }

    // ── GET /api/v1/notifications ─────────────────────────────────────────────

    /**
     * Returns all notifications (notices) sent to this student.
     * Includes CLASS-wide and ALL-school notices automatically via the service/repository query.
     *
     * Each entry: {
     *   "id":           1,
     *   "heading":      "Annual Day Celebration",
     *   "createdAt":    "2025-04-01T09:00:00",
     *   "recipientType":"ALL",
     *   "type":         "notification",
     *   "previewText":  "..."
     * }
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getNotifications(
            HttpServletRequest request) {
        log.info("Inside getNotifications");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        // Uses SmsMessageService.getNotificationsByStudentId() — already exists in service
        List<SmsMessage> messages =
                smsMessageService.getNotificationsByStudentId(academicStudentId);

        List<Map<String, Object>> result = buildMessageList(messages);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /api/v1/notifications/unread-count ────────────────────────────────

    /**
     * Returns the count of notifications for this student.
     * (v1 simplification: all are treated as "new"; read-tracking is a v2 feature)
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            HttpServletRequest request) {
        log.info("Inside getUnreadCount");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        List<SmsMessage> messages =
                smsMessageService.getNotificationsByStudentId(academicStudentId);

        Map<String, Object> countData = new LinkedHashMap<>();
        countData.put("count", messages.size());

        return ResponseEntity.ok(ApiResponse.success(countData));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMessageList(List<SmsMessage> messages) {
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
            entry.put("isRead",        false);
            // Include first conversation text as preview if present
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
