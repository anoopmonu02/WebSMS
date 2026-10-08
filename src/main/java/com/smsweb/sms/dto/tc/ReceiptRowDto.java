package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ReceiptMode;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One receipt line, on the exit detail page and in the receipt register. */
@Getter
@AllArgsConstructor
public class ReceiptRowDto {

    private final UUID uuid;
    private final UUID exitUuid;
    private final String receiptNo;
    private final LocalDate receiptDate;
    private final ReceiptMode mode;
    private final BigDecimal amountPaid;
    private final BigDecimal discountAmount;
    private final String paymentMode;
    private final String referenceNo;
    private final RecordStatus recordStatus;
    private final String voidReason;
    private final String studentName;
    private final String className;
}
