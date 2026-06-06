package com.smsweb.sms.models.messaging;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Section;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static final String MESSAGE_TYPE_COMPLAINT = "complaint";
    public static final String MESSAGE_TYPE_NOTIFICATION = "notification";
    public static final String MESSAGE_TYPE_ACTIVITIES = "activities";

    public static final String RECIPIENT_TYPE_ALL = "ALL";
    public static final String RECIPIENT_TYPE_CLASS = "CLASS";
    public static final String RECIPIENT_TYPE_STUDENT  = "STUDENT";

    public static final String RESOLUTION_TYPE_RESOLVED   = "RESOLVED";
    public static final String RESOLUTION_TYPE_UNRESOLVED = "UNRESOLVED";
    /** Kept for backward-compat: old DB rows may have the misspelled value. */
    @Deprecated
    public static final String RESOLUTION_TYPE_RESOLVED_LEGACY = "RESLOVED";

    @ManyToMany
    @JoinTable(
            name = "sms_message_recipients",
            joinColumns = @JoinColumn(name = "sms_message_id"),
            inverseJoinColumns = @JoinColumn(name = "academic_student_id")
    )
    private List<AcademicStudent> recipients;


    @Column(nullable = false)
    private String recipientType;

    @OneToMany(mappedBy = "smsMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SmsConversation> conversations;


    @Column(nullable = false)
    private String messageType;

    @Column(nullable = true)
    private String resolution;

    @Column(nullable = false)
    private String smsHeading;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    @NotNull(message = "School should be available")
    private School school;

    @Column(nullable = true)
    private Date createdAt;

    @ManyToOne
    @JoinColumn(name = "grade_id", nullable = true)
    private Grade grade;

    @ManyToOne
    @JoinColumn(name = "section_id", nullable = true)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    @Column(nullable = true)
    private Date updatedAt;
}
