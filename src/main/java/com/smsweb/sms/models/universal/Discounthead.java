package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Data
@Entity
public class Discounthead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Discount name should not blank")
    @Size(max = 100, message = "Discount name should not exceed 100 chars")
    @Column(unique = true)
    private String discountName;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;

    @Column(nullable = false)
    private String status = "active";

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
}
