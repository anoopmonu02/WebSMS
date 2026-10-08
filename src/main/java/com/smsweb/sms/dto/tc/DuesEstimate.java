package com.smsweb.sms.dto.tc;

import java.math.BigDecimal;

/**
 * Result of the dues estimate for one student.
 *
 * @param amount          total dues: month fees, previous or opening balance and late fine, less any discount
 * @param fineAmount      the late-fine part of the total
 * @param monthsLabel     months counted, for example "April, May, June" (empty when none)
 * @param headsLabel      fee heads counted (empty when none)
 * @param cutoffMonth     name of the last month counted
 * @param includeLastMonth the choice made on the form
 * @param breakdownJson   all of the above as JSON, stored in {@code student_exit.calc_breakdown}
 */
public record DuesEstimate(BigDecimal amount,
                           BigDecimal fineAmount,
                           String monthsLabel,
                           String headsLabel,
                           String cutoffMonth,
                           boolean includeLastMonth,
                           String breakdownJson) {
}
