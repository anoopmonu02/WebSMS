package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the exit list (a plain class with getters so Thymeleaf can read it).
 * {@code duesAmount} is the frozen amount once approved, otherwise the estimate;
 * {@code duesFrozen} tells which one it is.
 */
@Getter
@AllArgsConstructor
public class ExitRowDto {

    private final UUID uuid;
    private final String studentName;
    private final String fatherName;
    private final String className;
    private final String sectionName;
    private final String mediumName;
    private final String srNo;
    private final ExitReason exitReason;
    private final LocalDate exitDate;
    private final BigDecimal duesAmount;
    private final boolean duesFrozen;
    private final boolean duesManual;
    private final ExitStatus status;
}
