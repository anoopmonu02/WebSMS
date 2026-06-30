package com.smsweb.sms.models.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "config_name", length = 100)
    private String configName;  // unique key e.g. "TUITION_FEE_HEAD_ID"

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue; // e.g. "1" or "May"

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;
}
