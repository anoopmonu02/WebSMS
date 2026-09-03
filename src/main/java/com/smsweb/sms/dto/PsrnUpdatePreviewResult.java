package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Returned after parsing (preview) and again after saving (result) the PSRN-based
 * PEN No / Apaar ID bulk-update sheet. Same object reused for both screens, same pattern
 * as ImportPreviewResult — recalcCounts() is called again after execute() mutates row
 * statuses from READY/WARNING to UPDATED (or ERROR, if an individual save failed).
 */
@Getter
@Setter
public class PsrnUpdatePreviewResult {

    private List<PsrnUpdateRow> rows = new ArrayList<>();

    /** "PEN_NO" or "APAAR_ID" — echoed back so the execute step knows which field to write. */
    private String targetField;

    /** Which of Student Name/Father Name/Mother Name/Grade/Sec columns were absent from the
     *  sheet — the mismatch check is silently skipped for these, never blocks the upload. */
    private List<String> missingOptionalColumns = new ArrayList<>();

    private int totalRows;
    private int readyCount;
    private int warningCount;
    private int conflictCount;
    private int skipBlankCount;
    private int skipAlreadySetCount;
    private int errorCount;
    private int updatedCount; // populated only after execute()

    /** True once at least one row is eligible to be written (READY, or WARNING pending confirm). */
    public boolean isHasUpdatableRows() {
        return readyCount > 0 || warningCount > 0;
    }

    public void recalcCounts() {
        readyCount = 0; warningCount = 0; conflictCount = 0;
        skipBlankCount = 0; skipAlreadySetCount = 0; errorCount = 0; updatedCount = 0;
        for (PsrnUpdateRow r : rows) {
            switch (r.getStatus()) {
                case PsrnUpdateRow.STATUS_READY            -> readyCount++;
                case PsrnUpdateRow.STATUS_WARNING          -> warningCount++;
                case PsrnUpdateRow.STATUS_CONFLICT         -> conflictCount++;
                case PsrnUpdateRow.STATUS_SKIP_BLANK       -> skipBlankCount++;
                case PsrnUpdateRow.STATUS_SKIP_ALREADY_SET -> skipAlreadySetCount++;
                case PsrnUpdateRow.STATUS_ERROR            -> errorCount++;
                case PsrnUpdateRow.STATUS_UPDATED          -> updatedCount++;
            }
        }
        totalRows = rows.size();
    }
}
