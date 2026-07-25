package com.smsweb.sms.models.messaging;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per file attached to a Notification-type SmsMessage (feature:
 * "Send Document" — image/PDF/doc sharing, added to the existing Send
 * Message → Notification flow only; Complaint and Activities messages never
 * get rows here).
 *
 * Deliberately hangs off SmsMessage rather than SmsConversation — for this
 * flow the file(s) ARE the notification's content, not an addendum to a
 * chat-style reply, so this stays fully decoupled from the Complaint
 * reply-threading model.
 *
 * contentType is NOT restricted to images here — pdf/doc/xls/etc. are stored
 * the same way. Only the admin web UI and the mobile app's rendering logic
 * special-case "is this an image" (inline preview vs. file icon).
 */
@Entity
@Getter
@Setter
@Table(
        name = "sms_message_attachments",
        indexes = {
                @Index(name = "idx_sma_sms_message", columnList = "sms_message_id")
        }
)
public class SmsMessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sms_message_id", nullable = false)
    @JsonBackReference
    private SmsMessage smsMessage;

    /** The actual filename on disk — UUID-prefixed, never guessable, never collides. */
    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    /** What the admin uploaded — kept for display and for eventual download. */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** e.g. image/jpeg, application/pdf — this alone decides inline-preview vs. file-icon rendering. */
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public SmsMessageAttachment() {}

    public SmsMessageAttachment(SmsMessage smsMessage, String storedFileName, String originalFileName,
                                 String contentType, Long fileSize, LocalDateTime uploadedAt) {
        this.smsMessage = smsMessage;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
    }

    /** Convenience — the one piece of logic every consumer (web + eventually mobile) needs. */
    @Transient
    public boolean isImage() {
        return contentType != null && contentType.toLowerCase().startsWith("image/");
    }
}
