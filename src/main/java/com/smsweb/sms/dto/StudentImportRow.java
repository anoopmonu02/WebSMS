package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one row parsed from the student import XLS file.
 * Holds both raw values (for display) and resolved/cleaned values (for saving).
 */
@Getter
@Setter
public class StudentImportRow {

    // ── Row metadata ──────────────────────────────────────────────────────────
    private int rowNum;          // 1-based row number in Excel
    private String status;       // READY | WARNING | ERROR
    private List<String> messages = new ArrayList<>();

    // ── Raw Excel values (always preserved for display) ───────────────────────
    private String rawSrNo;
    private String rawName;
    private String rawFatherName;
    private String rawMotherName;
    private String rawClass;
    private String rawSection;
    private String rawDob;
    private String rawReligion;
    private String rawCaste;
    private String rawCategory;
    private String rawGender;
    private String rawContact;
    private String rawAddress;
    private String rawAadhar;
    private String rawBankName;
    private String rawBranchName;
    private String rawAccountNo;
    private String rawIfscCode;

    // ── Cleaned / resolved values (used during actual insert) ─────────────────
    private String classSrNo;       // = rawSrNo
    private String studentName;
    private String fatherName;
    private String motherName;
    private String dobStr;          // cleaned date string or null
    private String religion;        // mapped to DB value (e.g. HINDU→HINDUISM)
    private String casteCleaned;    // after dedup corrections
    private String categoryName;
    private String gender;          // uppercase normalised
    private String mobile1;
    private String mobile2;
    private String address;         // address without city suffix
    private String cityName;        // extracted last segment after ','
    private String aadharNo;
    private String bankName;        // cleaned bank name
    private String branchName;
    private String accountNo;
    private String ifscCode;

    // ── Lookup resolution flags ───────────────────────────────────────────────
    private boolean gradeFound;
    private boolean sectionFound;
    private boolean categoryFound;
    private boolean castExists;     // false = will be auto-created
    private boolean cityExists;     // false = will be auto-created
    private boolean bankFound;      // false = will use "NO Bank"
    private boolean dobIsNull;
    private boolean contactDummy;   // true = no contact in Excel, assigned dummy

    // ── Status helpers ────────────────────────────────────────────────────────
    public static final String STATUS_READY   = "READY";
    public static final String STATUS_WARNING = "WARNING";
    public static final String STATUS_ERROR   = "ERROR";

    public void addError(String msg) {
        messages.add("❌ " + msg);
        this.status = STATUS_ERROR;
    }

    public void addWarning(String msg) {
        messages.add("⚠️ " + msg);
        if (!STATUS_ERROR.equals(this.status)) {
            this.status = STATUS_WARNING;
        }
    }

    public void markReady() {
        // Set READY for any row that has no ERROR — warnings are auto-handled during import
        if (!STATUS_ERROR.equals(this.status)) {
            this.status = STATUS_READY;
        }
    }
}
