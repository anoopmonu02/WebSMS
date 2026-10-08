package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * One student record in the chain of records linked by re-registration (oldest first).
 * {@code current} marks the record whose timeline was opened.
 */
@Getter
@AllArgsConstructor
public class TimelineChainRow {

    private final UUID studentUuid;
    private final String studentName;
    private final String registrationNo;
    private final String status;
    /** PSRN of this record; null when not set. */
    private final Long psrn;
    /** Last known SR number: the SR at the time of exit for older records, the current one for the newest. */
    private final String srNo;
    private final boolean current;
}
