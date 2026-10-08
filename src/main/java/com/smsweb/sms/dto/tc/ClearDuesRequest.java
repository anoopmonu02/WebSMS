package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Input of the "Clear dues" dialog (ledger mode). */
@Getter
@Setter
public class ClearDuesRequest {

    private BigDecimal amountPaid;
    /** Admin only. */
    private BigDecimal discountAmount;
    private String discountReason;
    private String paymentMode;
    private String referenceNo;
    private String remark;
    /** Hidden field made when the dialog opens; a second post with the same value returns the first receipt. */
    private String submissionToken;
}
