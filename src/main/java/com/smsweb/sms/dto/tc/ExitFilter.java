package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filters of the exit list. Every field is optional. */
@Getter
@Setter
public class ExitFilter {

    private ExitStatus status;
    private ExitReason reason;
    private Long gradeId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    /** Free text: student, father or mother name. */
    private String q;
    private int page = 0;
    private int size = 20;
}
