package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.validator.constraints.Length;

import java.util.Date;

@Data
@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(updatable = false)
    private Date registrationDate;

    private String registrationNo;

    @NotBlank(message = "Customer name should not blank")
    @Size(max = 100, message = "Customer name should not exceed 100 chars")
    @Column(unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 chars")
    private String address;

    @ManyToOne
    private Province province;

    @ManyToOne
    private City city;

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a 6-digit number")
    @Column(length = 6)
    private String pincode;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile1;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile2;

    @Email(message = "Please enter valid email")
    private String email;
    private String website;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

    private String pic;

    @Column(nullable = false)
    private String status = "active";

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
}
