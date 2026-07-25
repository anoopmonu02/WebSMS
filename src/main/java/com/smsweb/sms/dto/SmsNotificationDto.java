package com.smsweb.sms.dto;

import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Data
public class SmsNotificationDto {
    private String className;
    private String sectionName;
    private String recipientType;
    private String smsHeading;
    private String smsContent;
    private Date smsDate;

    // Empty (never null) for every notification without a file, and for every
    // Complaint/Activities row — this DTO is Notification-only, so those
    // message types never populate this field.
    private List<SmsMessageAttachmentDto> attachments = Collections.emptyList();
}