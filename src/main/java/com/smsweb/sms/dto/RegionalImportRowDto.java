package com.smsweb.sms.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * One row of the Regional-Language (Hindi) bulk update screen — used for both
 * the preview (after Excel upload) and the save (confirmed rows posted back).
 *
 * Deliberately carries the student's UUID as the identifier, never the raw
 * numeric `students.id` — the UUID is safe to round-trip through the browser
 * and the downloadable Excel file, the internal DB id is not.
 *
 * ignoreUnknown = true: the browser posts this same object straight back on
 * /save, so any extra (non-setter) JSON field must not blow up deserialization.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegionalImportRowDto {

    private int sno;

    /** Match key — never the raw numeric id. */
    private String studentUuid;

    // ── Locked / read-only columns (English, from `students`) ───────────────
    private String studentName;
    private String fatherName;
    private String motherName;
    private String address;

    // ── Editable regional-language columns ──────────────────────────────────
    private String studentNameRegional;
    private String fatherNameRegional;
    private String motherNameRegional;
    private String addressRegional;

    /** MATCHED | NOT_FOUND | INACTIVE */
    private String matchStatus;

    private String message;

    /** True when one or more regional values were cleared because the uploaded file
     * contained a broken/unconverted spreadsheet formula instead of real text
     * (e.g. an unresolved Google Sheets =GOOGLETRANSLATE(...) export artifact). */
    private boolean hasWarning;

    @JsonIgnore
    public boolean isMatched() {
        return "MATCHED".equals(matchStatus);
    }
}
