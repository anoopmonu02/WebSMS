package com.smsweb.sms.models.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;


@Data
@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String registrationNo;//auto-generated

    @CreationTimestamp
    @Column(updatable = false)
    private Date registrationDate;

    //Personal Info
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Student name must not contain special characters")
    @NotBlank(message = "Student name should not blank")
    @Size(max = 200, message = "Student name should not exceed 200 chars")
    private String studentName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Father name must not contain special characters")
    @NotBlank(message = "Father name should not blank")
    @Size(max = 200, message = "Father name should not exceed 200 chars")
    private String fatherName;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Mother name must not contain special characters")
    @NotBlank(message = "Mother name should not blank")
    @Size(max = 200, message = "Mother name should not exceed 200 chars")
    private String motherName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dob;
    @Column(nullable = false)
    private String nationality = "INDIAN";

    private String fatherOccupation;
    private String motherOccupation;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @NotNull(message = "Category should be available")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "cast_id")
    @NotNull(message = "Cast should be available")
    private Cast cast;
    private String gender="No_Preference";

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;
    private String pic;
    private String religion="No Preference";

    //Physical Info
    private Integer height = 0;
    private Integer weight = 0;
    private String bloodGroup = "No Preference";
    private String bodyType = "Normal";

    //Contact Info
    @NotBlank(message = "Address should not blank")
    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Address should not exceed 500 chars")
    private String address;
    private String landmark;

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
    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile1;
    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String mobile2;
    @Email(message = "Please enter valid email")
    private String email;

    //Previous Academic Details
    private String previousSchool;
    private String previousClass;
    private String tcNo;
    private String removalCause;
    private Integer passingYear;

    //Emergency
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Emergency contact person name must not contain special characters")
    private String personName;
    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be a 10-digit number")
    @Column(length = 10)
    private String personContact;
    private String relationship;

    //Extra
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
    @Size(max = 500, message = "Remark should not exceed 500 chars")
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

    //Bank & Aadhar details
    @ManyToOne
    @JoinColumn(name = "bank_id")
    @NotNull(message = "Bank should be available")
    private Bank bank;
    private String branchName;
    private String ifscCode;
    private String accountNo;

    @Pattern(regexp = "^$|^[0-9]{12}$", message = "Aadhar number must be a 12-digit number")
    @Column(length = 12)
    private String aadharNo;

    //TODO-will add 2 more attributes - createdBy, updatedBy


}