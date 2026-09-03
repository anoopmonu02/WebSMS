package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the PSRN-based PEN No / Apaar ID bulk-update sheet, carrying both the sheet's
 * values and the DB's current values so the preview screen can show a side-by-side diff.
 *
 * Status lifecycle:
 *   Preview:  READY | WARNING | CONFLICT | SKIP_BLANK | SKIP_ALREADY_SET | ERROR
 *   Execute:  READY/WARNING(confirmed) rows that save successfully become UPDATED;
 *             everything else keeps its preview-time status (or becomes ERROR if the
 *             save itself fails).
 */
@Getter
@Setter
public class PsrnUpdateRow {

    public static final String STATUS_READY            = "READY";
    public static final String STATUS_WARNING          = "WARNING";
    public static final String STATUS_CONFLICT         = "CONFLICT";
    public static final String STATUS_SKIP_BLANK       = "SKIP_BLANK";
    public static final String STATUS_SKIP_ALREADY_SET = "SKIP_ALREADY_SET";
    public static final String STATUS_ERROR             = "ERROR";
    public static final String STATUS_UPDATED           = "UPDATED";

    private int rowNum;                 // Excel row number (1-based, for error reporting)
    private String psrnRaw;             // as read from the sheet, kept even if unparseable
    private Long psrn;                  // parsed PSRN, null if invalid/missing

    private String sheetValue;          // PEN No / Apaar ID value from the sheet (trimmed)
    private String dbValue;             // current DB value at preview time (may be blank)

    private String sheetStudentName;
    private String sheetFatherName;
    private String sheetMotherName;
    private String sheetGrade;
    private String sheetSection;

    private String dbStudentName;
    private String dbFatherName;
    private String dbMotherName;
    private String dbGrade;
    private String dbSection;

    private Long studentId;             // resolved student's id, once found

    private String status = STATUS_READY;
    private String message;             // human-readable reason shown in the UI

    /** Which of studentName/fatherName/motherName/grade/section differ from the DB. */
    private List<String> mismatchFields = new ArrayList<>();

    public boolean hasMismatch() {
        return mismatchFields != null && !mismatchFields.isEmpty();
    }
}
