package com.smsweb.sms.models.student;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.helper.DisplayLabel;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "students")  // Specifies the table name for Student entities
@ToString(exclude = {"createdBy", "updatedBy"})
public class Student { // Extend UserEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_entity_id", referencedColumnName = "id")
    private UserEntity userEntity; // Create a one-to-one relationship with UserEntity

    @DisplayLabel("Registration No")
    private String registrationNo; // Auto-generated

    @CreationTimestamp
    @Column(updatable = false)
    private Date registrationDate;

    // Personal Info
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Student name must not contain special characters")
    @NotBlank(message = "Student name should not be blank")
    @Size(max = 200, message = "Student name should not exceed 200 characters")
    @DisplayLabel("Student Name")
    private String studentName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Father's name must not contain special characters")
    @NotBlank(message = "Father's name should not be blank")
    @Size(max = 200, message = "Father's name should not exceed 200 characters")
    @DisplayLabel("Father Name")
    private String fatherName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Mother's name must not contain special characters")
    @NotBlank(message = "Mother's name should not be blank")
    @Size(max = 200, message = "Mother's name should not exceed 200 characters")
    @DisplayLabel("Mother Name")
    private String motherName;

    @DisplayLabel("Birth Date")
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date dob;

    @Column(nullable = false)
    private String nationality = "INDIAN";

    private String fatherOccupation;
    private String motherOccupation;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @NotNull(message = "Category should be available")
    @DisplayLabel("Category")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "cast_id")
    @NotNull(message = "Cast should be available")
    @DisplayLabel("Cast")
    private Cast cast;

    @DisplayLabel("Gender")
    private String gender = "No_Preference";

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 characters")
    private String description;

    private String pic;
    @DisplayLabel("Religion")
    private String religion = "No Preference";

    // Physical Info
    private Integer height = 0;
    private Integer weight = 0;
    private String bloodGroup = "No Preference";
    private String bodyType = "Normal";

    // Contact Info
    @NotBlank(message = "Address should not be blank")
    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 characters")
    @DisplayLabel("Address")
    private String address;

    @DisplayLabel("Landmark")
    private String landmark;

    @ManyToOne
    @JoinColumn(name = "province_id")
    @NotNull(message = "Province should be available")
    @DisplayLabel("Province")
    private Province province;

    @ManyToOne
    @JoinColumn(name = "city_id")
    @NotNull(message = "City should be available")
    @DisplayLabel("City")
    private City city;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be a 6-digit number")
    @Column(length = 6)
    @DisplayLabel("Pincode")
    private String pincode;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    @DisplayLabel("Mobile-1")
    private String mobile1;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    @DisplayLabel("Mobile-2")
    private String mobile2;



    // Previous Academic Details
    private String previousSchool;
    private String previousClass;
    private String tcNo;
    private String removalCause;
    private Integer passingYear;

    // Emergency
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Emergency contact person name must not contain special characters")
    private String personName;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String personContact;

    private String relationship;

    // Extra
    @ManyToOne
    @JoinColumn(name = "grade_id")
    @NotNull(message = "Grade should be available")
    private Grade grade;

    @ManyToOne
    @JoinColumn(name = "section_id")
    @NotNull(message = "Section should be available")
    private Section section;

    @ManyToOne
    @JoinColumn(name = "medium_id")
    @NotNull(message = "Medium should be available")
    private Medium medium;

    @Column(nullable = false)
    private String studentType = "New";

    @Column(nullable = false)
    private String schoolStatus = "Own";

    @Column(nullable = false)
    private String status = "Active";

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Remark should not exceed 500 characters")
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    // Bank & Aadhar details
    @ManyToOne
    @JoinColumn(name = "bank_id")
    @NotNull(message = "Bank should be available")
    private Bank bank;

    private String branchName;
    private String ifscCode;
    private String accountNo;

    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;

    @Pattern(regexp = "^$|^[0-9]{12}$", message = "Aadhar number must be a 12-digit number")
    @Column(length = 12)
    private String aadharNo;

    // createdBy, updatedBy fields, use @JsonIgnore if necessary to avoid circular reference

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;
}
