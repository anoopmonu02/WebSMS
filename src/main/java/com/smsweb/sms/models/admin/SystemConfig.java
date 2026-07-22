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

    // Comma-separated school IDs this config applies to (e.g. "1,3"). Null or
    // blank means it applies to every school — config_name itself stays the
    // primary key/unique, so a given setting is either global or scoped to
    // one specific set of schools, not both at once for the same name.
    @Column(name = "school_ids", length = 255)
    private String schoolIds;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;
}
