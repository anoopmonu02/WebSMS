package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Complaint endpoints for the student mobile app.
 *
 * GET  /api/v1/complaints          — list of complaints submitted by or for this student
 * GET  /api/v1/complaints/{id}     — single complaint with full conversation thread
 *
 * Uses SmsMessageService (existing service):
 *  - getMessagesByStudentId()  ← already exists in SmsMessageService
 *  - findById()                ← already exists in SmsMessageService
 */
@RestController
@RequestMapping("/api/v1/complaints")
public class MobileComplaintsController {
    private static final Logger log = LoggerFactory.getLogger(MobileComplaintsController.class);


    private final SmsMessageService smsMessageService;

    public MobileComplaintsController(SmsMessageService smsMessageService) {
        this.smsMessageService = smsMessageService;
    }

    // ── GET /api/v1/complaints ────────────────────────────────────────────────

    /**
     * Returns all complaints for this academic student (where messageType = "complaint").
     *
     * Each entry: {
     *   "id":           5,
     *   "heading":      "Books not provided",
     *   "resolution":   "UNRESOLVED",
     *   "isResolved":   false,
     *   "createdAt":    "2025-04-10T11:00:00",
     *   "recipientType":"STUDENT",
     *   "replyCount":   2
     * }
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getComplaints(
            HttpServletRequest request) {
        log.info("Inside getComplaints");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        // Uses SmsMessageService.getMessagesByStudentId() — already exists in service
        // Returns ALL message types; filter to complaints only
        List<SmsMessage> all = smsMessageService.getMessagesByStudentId(academicStudentId);

        List<Map<String, Object>> complaints = all.stream()
                .filter(m -> SmsMessage.MESSAGE_TYPE_COMPLAINT.equals(m.getMessageType()))
                .map(this::toComplaintSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(complaints));
    }

    // ── GET /api/v1/complaints/{id} ───────────────────────────────────────────

    /**
     * Returns a single complaint with its full conversation thread.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComplaintDetail(
            @PathVariable Long id,
            HttpServletRequest request) {
        log.info("Inside getComplaintDetail");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        // Uses SmsMessageService.findById() — already exists in service
        Optional<SmsMessage> optMsg = smsMessageService.findById(id);
        if (optMsg.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Complaint not found"));
        }

        SmsMessage msg = optMsg.get();

        // Security: verify this complaint is linked to the authenticated student
        boolean isRecipient = msg.getRecipients() != null &&
                msg.getRecipients().stream()
                        .anyMatch(r -> r.getId().equals(academicStudentId));
        if (!isRecipient) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }

        Map<String, Object> detail = toComplaintSummary(msg);

        // Add full conversation thread
        List<Map<String, Object>> thread = new ArrayList<>();
        if (msg.getConversations() != null) {
            for (SmsConversation conv : msg.getConversations()) {
                Map<String, Object> reply = new LinkedHashMap<>();
                reply.put("id",      conv.getId());
                reply.put("message", conv.getContent());
                reply.put("sentAt",  conv.getSentAt());
                reply.put("sentBy",  conv.getInitiatedBy());
                thread.add(reply);
            }
        }
        detail.put("thread", thread);

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toComplaintSummary(SmsMessage m) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id",            m.getId());
        entry.put("heading",       m.getSmsHeading());
        // Normalise resolution: treat legacy typo "RESLOVED" same as "RESOLVED"
        String resolution = m.getResolution();
        if (SmsMessage.RESOLUTION_TYPE_RESOLVED_LEGACY.equals(resolution)) {
            resolution = SmsMessage.RESOLUTION_TYPE_RESOLVED;
        }
        entry.put("resolution",    resolution != null ? resolution : SmsMessage.RESOLUTION_TYPE_UNRESOLVED);
        entry.put("isResolved",    SmsMessage.RESOLUTION_TYPE_RESOLVED.equals(resolution));
        entry.put("createdAt",     m.getCreatedAt());
        entry.put("recipientType", m.getRecipientType());
        entry.put("replyCount",    m.getConversations() != null ? m.getConversations().size() : 0);
        return entry;
    }
}
