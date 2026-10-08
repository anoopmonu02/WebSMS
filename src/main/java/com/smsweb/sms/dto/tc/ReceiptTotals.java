package com.smsweb.sms.dto.tc;

import java.math.BigDecimal;

/** Sum of the VALID receipts that match the register filter. */
public record ReceiptTotals(BigDecimal paid, BigDecimal waived) {
}
