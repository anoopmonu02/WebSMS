package com.smsweb.sms.models.messaging;

import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "sms_message_recipients",
            joinColumns = @JoinColumn(name = "sms_message_id"),
            inverseJoinColumns = @JoinColumn(name = "academic_student_id")
    )
    private List<AcademicStudent> recipients; // List of students receiving the message

    private String content;

    private LocalDateTime sentAt;

    private Boolean seen; // To mark if the message has been seen by the student

    private Boolean isDeleted;

    private String messageType;

    @Column(nullable = true)
    private String resolution;

    @JoinColumn(name = "created_by", updatable = false)
    private String createdBy;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    private boolean hasAttachment;

    private boolean haveDocAttachment;

    @Column(nullable = true)
    private String docFilePath;

}
