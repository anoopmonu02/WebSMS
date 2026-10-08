package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of the re-registration search: an exit of a student who left, with whether the student can be
 * registered again now and, when not, why. Plain values only.
 */
@Getter
@AllArgsConstructor
public class ReRegisterRowDto {

    private final UUID exitUuid;
    private final UUID studentUuid;
    private final String studentName;
    private final String fatherName;
    private final String motherName;
    private final String registrationNo;
    private final Long psrn;
    private final String srNo;
    private final String className;
    private final String sessionName;
    private final LocalDate exitDate;
    private final String statusLabel;
    /** Number of the valid TC, or null when none was issued. */
    private final Long tcNumber;
    private final LocalDate tcIssueDate;
    private final boolean eligible;
    /** Why the student cannot be registered again now (null when eligible). */
    private final String note;
}
