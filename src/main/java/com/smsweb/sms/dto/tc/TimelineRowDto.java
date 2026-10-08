package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One line of the student timeline. Plain values only, so the page never touches lazy data. */
@Getter
@AllArgsConstructor
public class TimelineRowDto {

    private final LocalDate eventDate;
    private final LocalDateTime recordedAt;
    /** Short heading, for example "Struck off". */
    private final String title;
    /** Bootstrap colour name for the badge: danger, success, primary, warning, info or secondary. */
    private final String tone;
    /** One readable sentence built from the saved values. */
    private final String description;
    private final String className;
    private final String studentName;
    private final String registrationNo;
    private final String userName;
    private final boolean voided;
    private final String voidReason;
    /** EXIT, TC or RECEIPT when the row points at a record the page can link to, otherwise null. */
    private final String linkKind;
    private final UUID linkUuid;
}
