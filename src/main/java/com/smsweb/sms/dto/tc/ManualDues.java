package com.smsweb.sms.dto.tc;

import java.math.BigDecimal;

/** Dues typed by an admin instead of the automatic estimate. */
public record ManualDues(BigDecimal amount, String reason) {
}
