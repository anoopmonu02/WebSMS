package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Returned after parsing the XLS file.
 * Contains all rows + a lookup summary for the left panel.
 */
@Getter
@Setter
public class ImportPreviewResult {

    private List<StudentImportRow> rows = new ArrayList<>();

    // ── Lookup summary (for left panel) ──────────────────────────────────────

    /** Grades found in DB */
    private Set<String> gradesFound    = new LinkedHashSet<>();
    /** Grades not in DB — will be auto-created during import */
    private Set<String> gradesToCreate = new LinkedHashSet<>();

    /** Sections found in DB */
    private Set<String> sectionsFound   = new LinkedHashSet<>();
    /** Sections not in DB — will be auto-created during import */
    private Set<String> sectionsToCreate = new LinkedHashSet<>();

    /** Castes that will be auto-created */
    private Set<String> castesToCreate  = new LinkedHashSet<>();
    /** Castes already in DB */
    private Set<String> castesFound     = new LinkedHashSet<>();

    /** Cities that will be auto-created */
    private Set<String> citiesToCreate  = new LinkedHashSet<>();
    /** Cities already in DB */
    private Set<String> citiesFound     = new LinkedHashSet<>();

    /** Banks found in DB (including "NO Bank" fallback) */
    private Set<String> banksFound      = new LinkedHashSet<>();
    /** Banks in Excel that will fall back to "NO Bank" */
    private Set<String> banksFallback   = new LinkedHashSet<>();

    /** Categories found */
    private Set<String> categoriesFound   = new LinkedHashSet<>();
    /** Categories missing from DB */
    private Set<String> categoriesMissing = new LinkedHashSet<>();

    // ── Aggregate counts ─────────────────────────────────────────────────────
    private int totalRows;
    private int readyCount;
    private int warningCount;
    private int errorCount;

    /** True when there are no ERROR rows. Grades/sections/castes/cities are all auto-created. */
    public boolean isImportAllowed() {
        return errorCount == 0 && categoriesMissing.isEmpty();
    }

    public void recalcCounts() {
        readyCount   = 0;
        warningCount = 0;
        errorCount   = 0;
        for (StudentImportRow r : rows) {
            switch (r.getStatus()) {
                case StudentImportRow.STATUS_READY   -> readyCount++;
                case StudentImportRow.STATUS_WARNING -> warningCount++;
                case StudentImportRow.STATUS_ERROR   -> errorCount++;
            }
        }
        totalRows = rows.size();
    }
}
