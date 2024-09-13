package com.smsweb.sms.models.admin;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Data
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_gconfig",columnNames = {"configName", "school_id"})})
public class GConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @NotBlank(message = "Configuration name is mandatory")
    @Size(max = 100, message = "Configuration name should not exceed 100 chars")
    @Column(unique = true)
    private String configName;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Configuration data should not exceed 100 chars")
    private String configData;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    School school;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
    @UpdateTimestamp
    private Date lastUpdated;

    //TODO - will add 2 more attributes - createdBy, updatedBy
}
