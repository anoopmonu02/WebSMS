package com.smsweb.sms.models.messaging;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Entity
@Data
public class SmsConversation {

    public static final String INITIATED_BY_SCHOOL = "SCHOOL";

    public static final String INITIATED_BY_GUARDIAN = "GUARDIAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "sms_id", nullable = false)
    @JsonBackReference
    private SmsMessage smsMessage;

    private boolean hasAttachment;

    private boolean haveDocAttachment;

    @Column(nullable = true)
    private String docFilePath;

    private Boolean seen;

    private Boolean isDeleted;

    private String initiatedBy;

    private Date sentAt = new Date();
}
