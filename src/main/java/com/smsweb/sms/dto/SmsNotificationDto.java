package com.smsweb.sms.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SmsNotificationDto {
    private String className;
    private String sectionName;
    private String recipientType;
    private String smsHeading;
    private String smsContent;
    private Date smsDate;
}