package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** The TC block of the exit detail page. */
@Getter
@Setter
public class TcPanelDto {

    /** The TC can be issued now. */
    private boolean canIssue;
    /** Why it cannot (shown to the user); null when it can or when a TC already exists. */
    private String blockReason;
    private String divisionName;
    private String tcFormat;
    private Long nextNumber;
    /** All TCs of this exit, oldest first (the last VALID one is the current TC). */
    private List<TcRowDto> tcs;
    /** A VALID TC exists, so a re-issue is possible. */
    private boolean canReissue;
    /** No regional-language name or address is saved for the student; the TC prints those lines blank. */
    private boolean regionalMissing;
}
