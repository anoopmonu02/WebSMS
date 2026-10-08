package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** What the controller needs after a successful re-registration. */
@Getter
@AllArgsConstructor
public class ReRegistrationResult {

    private final UUID newStudentUuid;
    private final String newRegistrationNo;
}
