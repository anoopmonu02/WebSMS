package com.smsweb.sms.models.employee;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@Entity
public class Employee{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeCode; // auto-generated unique code for each employee

    @CreationTimestamp
    @Column(updatable = false)
    private Date joiningDate;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Employee name must not contain special characters")
    @NotBlank(message = "Employee name should not be blank")
    @Size(max = 200, message = "Employee name should not exceed 200 chars")
    private String employeeName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Father name must not contain special characters")
    @NotBlank(message = "Father name should not be blank")
    @Size(max = 200, message = "Father name should not exceed 200 chars")
    private String fatherName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Mother name must not contain special characters")
    @NotBlank(message = "Mother name should not be blank")
    @Size(max = 200, message = "Mother name should not exceed 200 chars")
    private String motherName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dob;

    @Column(nullable = false)
    private String nationality = "INDIAN";

    private String designation;

    private String department;


    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

    private String pic;

    // Contact Info
    @NotBlank(message = "Address should not be blank")
    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 chars")
    private String address;

    private String landmark;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile1;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile2;

    @Email(message = "Please enter a valid email")
    private String email;

    // Additional Fields
    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    // TODO: Add createdBy, updatedBy, and other fields if needed

    // @JsonIgnore - will use for user or to avoid circular reference
}
