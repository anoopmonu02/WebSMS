package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.TcStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filters of the TC register. */
@Getter
@Setter
public class TcFilter {

    private Long divisionId;
    private TcStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    private Long numberFrom;
    private Long numberTo;
    /** Student, father or mother name. */
    private String q;
    private int page = 0;
    private int size = 25;
}
