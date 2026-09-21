package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of the PEN-No-matched Apaar ID bulk-update sheet.
 *
 * Match key is PEN No alone. Student Name / Gender / DOB / Mobile / Class / Section are
 * carried through purely as read-only context for the preview screen (sheet value next to
 * the DB's current value) — they are never compared and never influence the row's status,
 * because that other data can be misspelled/stale and would otherwise produce false blocks
 * on an otherwise-correct PEN No match.
 *
 * Status lifecycle:
 *   Preview:  READY | CONFLICT | SKIP_BLANK | SKIP_ALREADY_SET | ERROR
 *   Execute:  READY rows that save successfully become UPDATED; everything else keeps its
 *             preview-time status (or becomes ERROR if the save itself fails).
 *
 * Note: unlike the PSRN-based flow (PsrnUpdateRow), there is no WARNING status here — since
 * nothing but PEN No is used for matching, there is nothing left to "warn" about; a CONFLICT
 * covers both "DB already has a different Apaar ID" and "PEN No matches more than one student
 * (ambiguous, never guessed)".
 */
@Getter
@Setter
public class PenApaarUpdateRow {

    public static final String STATUS_READY            = "READY";
    public static final String STATUS_CONFLICT         = "CONFLICT";
    public static final String STATUS_SKIP_BLANK       = "SKIP_BLANK";
    public static final String STATUS_SKIP_ALREADY_SET = "SKIP_ALREADY_SET";
    public static final String STATUS_ERROR             = "ERROR";
    public static final String STATUS_UPDATED           = "UPDATED";

    private int rowNum;                 // Excel row number (1-based, for error reporting)
    private String penNoRaw;            // as read from the sheet, kept even if unparseable

    private String sheetApaarId;        // Apaar ID value from the sheet (trimmed)
    private String dbApaarId;           // current DB value at preview time (may be blank)

    // Read-only context only — never used for matching or status.
    private String sheetStudentName;
    private String sheetGender;
    private String sheetDob;
    private String sheetMobile;
    private String sheetClass;
    private String sheetSection;

    private String dbStudentName;
    private String dbGender;
    private String dbDob;
    private String dbMobile;
    private String dbClass;
    private String dbSection;

    private Long studentId;             // resolved student's id, once found (single match only)

    private String status = STATUS_READY;
    private String message;             // human-readable reason shown in the UI
}
