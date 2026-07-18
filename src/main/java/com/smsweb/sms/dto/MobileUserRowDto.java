package com.smsweb.sms.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One row on the Mobile Users admin screen (Admin Config > Mobile Users).
 * Built from FamilyAccount + its linked students + a MobileRefreshTokenService
 * session summary — no new persistence, purely a read view over existing data.
 */
@Getter
@Setter
public class MobileUserRowDto {

    private Long familyAccountId;
    private String mobile;
    private String status; // ACTIVE / INACTIVE, from FamilyAccount.status
    private boolean mustChangePassword;

    // One line per linked child, e.g. "Sabeeha (8-A, United Avadh Montessori School)"
    private List<String> students = new ArrayList<>();

    // Session info, from MobileRefreshTokenService.SessionSummary
    private boolean everLoggedIn;
    private boolean hasValidSession;

    // Kept as LocalDateTime for server-side sort/30-day-cutoff comparisons only —
    // never sent as-is to the browser. A LocalDateTime has no timezone attached;
    // serializing it as a raw ISO string and letting the browser's `new Date()`
    // parse it would make JS treat it as UTC (per spec, no offset = UTC) and shift
    // it by +5:30 on display — the exact class of bug this app just had in the fee
    // receipt flow. lastActiveDisplay below is the safe, pre-formatted string the
    // template should actually render.
    @JsonIgnore
    private LocalDateTime lastActive;
    private String lastActiveDisplay; // "17-Jul-2026 19:07" or "Never" — plain text, no client-side date parsing needed

    // IDs of every AcademicStudent linked to this family — needed by the
    // controller to call revokeAllForFamily() without re-deriving them.
    private List<Long> academicStudentIds = new ArrayList<>();
}
