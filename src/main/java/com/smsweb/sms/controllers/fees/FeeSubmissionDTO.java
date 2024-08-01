package com.smsweb.sms.controllers.fees;

import java.math.BigDecimal;
import java.util.Date;

public class FeeSubmissionDTO {
    private Long id;
    private Date feeSubmissionDate;
    private String receiptNo;
    private Long schoolId;
    private Long academicYearId;
    private Long academicStudentId;
    private BigDecimal fineAmount;
    private String fineRemark;
    private BigDecimal discountAmount;
    private Long discountClassMapId;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private BigDecimal fullPaymentAmount;
    private String fullPaymentRemark;
    private String feeRemark;
    private String status = "Active";
}
