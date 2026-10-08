package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filters of the Due Clearance Register. */
@Getter
@Setter
public class ReceiptFilter {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    /** Receipt number or student name. */
    private String q;
    /** true = also show voided receipts. */
    private boolean includeVoided;
    private int page = 0;
    private int size = 25;
}
