package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of checking or applying a contact (mobile1) change from the Fee Submission screen.
 * Plain text only; the browser must render it with text(), never as HTML.
 */
@Getter
@Setter
public class ContactChangeResult {

    public enum Outcome { BLOCKED, NO_CHANGE, NEW_FAMILY, EXISTING_FAMILY, APPLIED }

    private Outcome outcome;
    private String message;
    /** Children already on the target number, in the logged-in school ("Name (Grade-Sec)"). */
    private List<String> childrenInSchool = new ArrayList<>();
    /** Total other children already on the target number (all branches). */
    private int familyChildCount;
    /** Of those, how many are in other branches (names are not shown). */
    private int otherBranchCount;

    public static ContactChangeResult of(Outcome outcome, String message) {
        ContactChangeResult r = new ContactChangeResult();
        r.outcome = outcome;
        r.message = message;
        return r;
    }
}
