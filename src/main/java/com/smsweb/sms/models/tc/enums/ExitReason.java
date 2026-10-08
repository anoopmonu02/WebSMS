package com.smsweb.sms.models.tc.enums;

/** Why a student left. Stored by name in {@code student_exit.exit_reason}; the label is for screens only. */
public enum ExitReason {
    GUARDIAN_WISH("Guardian's wish"),
    PASSED_ALL_CLASSES("Passed all classes of the school"),
    MOVED_TO_OTHER_SCHOOL("Moved to another school"),
    LONG_ABSENCE("Long absence");

    private final String label;

    ExitReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
