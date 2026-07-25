package com.smsweb.sms.dto;

import lombok.Data;

/**
 * Wire shape for one notification attachment — used by SmsNotificationDto
 * (admin web UI, /message/notifications) and intended to be the same shape
 * the mobile API returns later, so both clients can share one rendering
 * component (image thumbnail if isImage, file icon + name otherwise).
 */
@Data
public class SmsMessageAttachmentDto {
    private Long id;
    private String originalFileName;
    private String contentType;
    private boolean image;
    private Long fileSize;
    /** Admin web UI: GET /message/attachment/{id} — authenticated, role-gated by the existing /message/** rule. */
    private String url;
}
