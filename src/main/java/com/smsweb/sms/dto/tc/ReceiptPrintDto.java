package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ReceiptMode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Everything the Due Clearance Receipt print page shows. Plain values only. */
@Getter
@Setter
public class ReceiptPrintDto {

    private String schoolName;
    private String schoolAddress;
    private String schoolPhone;

    private String receiptNo;
    private LocalDate receiptDate;
    private ReceiptMode mode;
    private boolean voided;
    private String voidReason;

    private String studentName;
    private String fatherName;
    private String className;
    private String sessionName;
    private String registrationNo;
    private String psrn;
    private String srNo;
    private LocalDate exitDate;
    private String reasonLabel;

    /** Stored breakdown of the fixed dues: months, heads, feesAndBalance, fine, total, manual, note. */
    private Map<String, Object> breakdown;
    private BigDecimal frozenAmount;
    private BigDecimal amountPaid;
    private BigDecimal discountAmount;
    private String discountReason;
    /** Paid plus waived on VALID receipts up to and including this one. */
    private BigDecimal settledUpToHere;
    private BigDecimal balanceAfter;
    private String amountInWords;
    private String paymentMode;
    private String referenceNo;
    private String remark;
    /** The exit this receipt belongs to (for the "back to exit" link). */
    private java.util.UUID exitUuid;
    /** Set for a fee-screen receipt: the normal fee receipt id, so both receipts can be reached. */
    private Long feeSubmissionId;
}
