package com.smsweb.sms.models.fees;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Discounthead;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity

public class FeeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date feeSubmissionDate;

    private String receiptNo;//auto-generated

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_student_id")
    @NotNull(message = "Student should be available")
    private AcademicStudent academicStudent;


    @Digits(integer = 10, fraction = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO;

    private String fineRemark;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @ManyToOne(optional = true) // Allowing null values
    @JoinColumn(name = "discounthead_id")
    private Discounthead discounthead;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Digits(integer = 10, fraction = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    @Digits(integer = 10, fraction = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO;
    @Digits(integer = 10, fraction = 2)
    private BigDecimal fullPaymentAmount = BigDecimal.ZERO;
    private String fullPaymentRemark;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Remark should not exceed 500 chars")
    private String feeRemark;

    private String status = "Active";

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @OneToMany(mappedBy = "feeSubmission", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<FeeSubmissionSub> feeSubmissionSub = new ArrayList<>();

    @OneToMany(mappedBy = "feeSubmission", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<FeeSubmissionMonths> feeSubmissionMonths = new ArrayList<>();

    @OneToOne(mappedBy = "feeSubmission", cascade = CascadeType.ALL)
    @JsonManagedReference
    @ToString.Exclude
    private FeeSubmissionBalance feeSubmissionBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;

    private String previousFeeBalanceRemark;

    private String paymentType;

    @Override
    public String toString() {
        return "FeeSubmission{" +
                "id=" + id +
                ", feeSubmissionDate=" + feeSubmissionDate +
                ", receiptNo='" + receiptNo + '\'' +
                ", fineAmount=" + fineAmount +
                ", fineRemark='" + fineRemark + '\'' +
                // Include other relevant fields
                '}';
    }

    @JsonProperty("createdByName")
    public String getCreatedByName() {
        if (createdBy == null) return null;
        return createdBy.getDisplayName();
    }

}
