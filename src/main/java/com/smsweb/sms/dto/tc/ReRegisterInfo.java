package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/** What the re-registration form shows above the registration form: whose record is being continued. */
@Getter
@AllArgsConstructor
public class ReRegisterInfo {

    private final UUID exitUuid;
    private final String studentName;
    private final String registrationNo;
    private final String className;
    private final String sessionName;
    private final Long tcNumber;
    private final LocalDate tcIssueDate;
}
