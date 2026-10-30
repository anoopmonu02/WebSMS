package com.smsweb.sms.models.automation;

import jakarta.persistence.*;

import java.util.Date;

/**
 * One cheerful line for the "all clear" absence-report email of a school. The school can add its own,
 * switch lines off or delete them. Text may contain "{days}" (replaced with the report's absent-days number).
 */
@Entity
@Table(name = "all_clear_message", indexes = @Index(name = "idx_all_clear_school", columnList = "school_id"))
public class AllClearMessage {

    public static final int MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "message_text", nullable = false, length = MAX_LENGTH)
    private String messageText;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_on", updatable = false)
    private Date createdOn = new Date();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Date getCreatedOn() { return createdOn; }
    public void setCreatedOn(Date createdOn) { this.createdOn = createdOn; }
}
