package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ExitReason;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input of the strike-off form. The school and the session never come from here. */
@Getter
@Setter
public class StrikeOffRequest {

    private Long academicStudentId;
    private ExitReason exitReason;
    /** Only for LONG_ABSENCE. */
    private Integer absentDays;
    /** The last date the student attended school. */
    private LocalDate exitDate;
    /** true = count the last date's month in the dues, false = only the months before it. */
    private boolean includeLastMonth = true;
    private String remark;
    /** Admin only: used when the automatic estimate fails. */
    private BigDecimal manualAmount;
    private String manualReason;
}
