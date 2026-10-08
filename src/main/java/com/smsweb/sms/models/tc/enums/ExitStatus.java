package com.smsweb.sms.models.tc.enums;

/** State of a student_exit row. See plan section 5.3 for the state machine. The label is for screens only. */
public enum ExitStatus {
    EXITED("Waiting for approval"),
    APPROVED("Approved"),
    TC_COLLECTED("TC collected"),
    REACTIVATED("Reactivated"),
    RE_REGISTERED("Re-registered"),
    CANCELLED("Cancelled");

    private final String label;

    ExitStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
