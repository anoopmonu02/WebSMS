package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.TcStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/** One TC line, on the exit detail page and in the TC register. */
@Getter
@AllArgsConstructor
public class TcRowDto {

    private final UUID uuid;
    private final UUID exitUuid;
    private final Long tcNumber;
    private final String tcFormat;
    private final String divisionName;
    private final String studentName;
    private final String fatherName;
    private final String className;
    private final LocalDate issueDate;
    private final Integer reissueNo;
    private final TcStatus status;
    private final String collectedByName;
    private final String collectedByRelation;
    private final String cause;
}
