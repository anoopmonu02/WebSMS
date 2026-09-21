package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Returned after parsing (preview) and again after saving (result) the PEN-No-matched
 * Apaar ID bulk-update sheet. Same object reused for both screens, same pattern as
 * PsrnUpdatePreviewResult — recalcCounts() is called again after execute() mutates row
 * statuses from READY to UPDATED (or ERROR, if an individual save failed).
 */
@Getter
@Setter
public class PenApaarUpdatePreviewResult {

    private List<PenApaarUpdateRow> rows = new ArrayList<>();

    /** Which of Student Name/Gender/DOB/Mobile/Class/Section columns were absent from the
     *  sheet — shown to the user for information only; these columns never block the update. */
    private List<String> missingOptionalColumns = new ArrayList<>();

    private int totalRows;
    private int readyCount;
    private int conflictCount;
    private int skipBlankCount;
    private int skipAlreadySetCount;
    private int errorCount;
    private int updatedCount; // populated only after execute()

    /** True once at least one row is eligible to be written (READY). */
    public boolean isHasUpdatableRows() {
        return readyCount > 0;
    }

    public void recalcCounts() {
        readyCount = 0; conflictCount = 0;
        skipBlankCount = 0; skipAlreadySetCount = 0; errorCount = 0; updatedCount = 0;
        for (PenApaarUpdateRow r : rows) {
            switch (r.getStatus()) {
                case PenApaarUpdateRow.STATUS_READY            -> readyCount++;
                case PenApaarUpdateRow.STATUS_CONFLICT         -> conflictCount++;
                case PenApaarUpdateRow.STATUS_SKIP_BLANK       -> skipBlankCount++;
                case PenApaarUpdateRow.STATUS_SKIP_ALREADY_SET -> skipAlreadySetCount++;
                case PenApaarUpdateRow.STATUS_ERROR            -> errorCount++;
                case PenApaarUpdateRow.STATUS_UPDATED          -> updatedCount++;
            }
        }
        totalRows = rows.size();
    }
}
