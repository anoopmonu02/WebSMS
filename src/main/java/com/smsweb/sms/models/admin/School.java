package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

public class School {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "School name should not blank")
    @Size(max = 200, message = "School name should not exceed 200 chars")
    @Column(unique = true)
    private String schoolName;
    private String schoolCode;//auto-generated field

    @Size(max=100, message = "Board name should not exceed 100 chars")
    private String board;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 chars")
    private String address;

    @ManyToOne
    @JoinColumn(name = "province_id")
    @NotNull(message = "Province should be available")
    private Province province;

    @ManyToOne
    @JoinColumn(name = "city_id")
    @NotNull(message = "City should be available")
    private City city;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be a 6-digit number")
    @Column(length = 6)
    private String pincode;

    @Email(message = "Please enter valid email")
    private String email;
    private String website;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile1;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile2;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Contact person name must not contain special characters")
    @Size(max = 100, message = "Contact person name should not exceed 100 chars")
    private String contactPersonName;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String contactPersonMobile;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;
    private String logo1;
    private String logo2;

    @Column(nullable = false)
    private String status = "active";

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    @NotNull(message = "Customer should be available")
    private Customer customer;
}
