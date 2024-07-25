package com.smsweb.sms.models.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;

import java.math.BigDecimal;
import java.util.Date;

public class FeeSubmission {

    private Long id;
    private Date feeSubmissionDate;
    private String receiptNo;
    private AcademicYear academicYear;
    private AcademicStudent academicStudent;
    private School school;

    private BigDecimal fineAmount;
    private String fineRemark;
    private BigDecimal discountAmount;
    private DiscountClassMap discountClassMap;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private BigDecimal fullPaymentAmount;
    private String fullPaymentRemark;

    private String feeRemark;
    private String status = "Active";

    private Date creationDate;
    private Date lastUpdated;

}
