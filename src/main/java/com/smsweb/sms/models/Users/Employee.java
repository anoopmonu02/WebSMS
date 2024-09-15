package com.smsweb.sms.models.Users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "employees")  // Specifies the table name for Employee entities
@ToString(exclude = {"createdBy", "updatedBy"})
public class Employee extends UserEntity {  // Extend UserEntity

    // Removed the @Id field as it is inherited from UserEntity

    @Column(nullable = false, unique = true)
    private String employeeCode; // Auto-generated unique code for each employee

    @CreationTimestamp
    @Column(updatable = false)
    private Date joiningDate;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Employee name must not contain special characters")
    @NotBlank(message = "Employee name should not be blank")
    @Size(max = 200, message = "Employee name should not exceed 200 characters")
    private String employeeName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Father name must not contain special characters")
    @Column(nullable = true)
    @Size(max = 200, message = "Father name should not exceed 200 characters")
    private String fatherName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Mother name must not contain special characters")
    @Size(max = 200, message = "Mother name should not exceed 200 characters")
    @Column(nullable = true)
    private String motherName;

    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date dob;

    @Column(nullable = false)
    private String nationality = "INDIAN";

    private String designation;

    private String department;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 characters")
    private String description;

    private String pic;

    // Contact Info
    @NotBlank(message = "Address should not be blank")
    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 characters")
    private String address;

    private String landmark;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile1;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile2;

    @Column(nullable = false)
    private String status = "Active";

    // Additional Fields
    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    // Metadata fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = new Date();  // Update lastUpdated timestamp during update
    }
}
