package com.smsweb.sms.models.admin;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.annotations.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@Entity
public class AcademicYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotNull(message = "Start date should not blank")
    /*@DateTimeFormat(pattern = "yyyy-MM-dd")*/
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date startDate;

    @NotNull(message = "End date should not blank")
    /*@DateTimeFormat(pattern = "yyyy-MM-dd")*/
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date endDate;

    @NotBlank(message = "Academic year format should not blank")
    @Size(max = 50, message = "Academic year format should not exceed 50 chars")
    @Column(unique = true)
    private String sessionFormat;

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

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;
}
