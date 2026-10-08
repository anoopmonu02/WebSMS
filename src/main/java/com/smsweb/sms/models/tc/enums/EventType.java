package com.smsweb.sms.models.tc.enums;

/** Timeline event types (plan appendix A). Stored by name in student_execution.event_type (VARCHAR 40). */
public enum EventType {
    STRUCK_OFF,
    LOGIN_DISABLED,
    DUES_MANUAL,
    TC_APPROVED,
    DUES_PAID,
    DUES_WAIVED,
    /** A ledger receipt was voided by an admin. */
    DUES_RECEIPT_VOIDED,
    TC_ISSUED,
    TC_REISSUED,
    STRIKE_OFF_CANCELLED,
    REACTIVATED,
    LOGIN_ENABLED,
    RE_REGISTERED,
    REGISTRATION_NO_ASSIGNED,
    SR_ASSIGNED,
    SR_CHANGED,
    ADMISSION_TC_SUBMITTED,
    ADMISSION_NOTARY,
    ADMISSION_EXEMPTED,
    /** Phase 7: old Remove Student screen. */
    DEACTIVATED,
    /** Phase 7: old Activate screen. */
    ACTIVATED
}
