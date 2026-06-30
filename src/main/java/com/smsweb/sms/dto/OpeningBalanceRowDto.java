package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One row from the Previous Pending Balance Excel, enriched with match result.
 */
@Getter
@Setter
public class OpeningBalanceRowDto {

    // ── From Excel ───────────────────────────────────────────────────────────
    private int rowNum;
    private String studentName;
    private String fatherName;
    private String srNo;
    private String excelClass;      // raw class string e.g. "11 SCI V"
    private String excelGrade;      // parsed: "11 SCI"
    private String excelSection;    // parsed: "V"
    private BigDecimal pendingAmount;

    // ── Match result ─────────────────────────────────────────────────────────
    /** MATCHED | SR_NOT_FOUND | GRADE_SECTION_MISMATCH */
    private String matchStatus;

    // ── From DB (only when matched / mismatch) ───────────────────────────────
    private Long academicStudentId;
    private String systemStudentName;
    private String systemFatherName;
    private String systemGrade;
    private String systemSection;

    // ── Convenience ──────────────────────────────────────────────────────────
    public boolean isMatched() {
        return "MATCHED".equals(matchStatus);
    }

    public boolean isMismatch() {
        return "GRADE_SECTION_MISMATCH".equals(matchStatus) || "NAME_MISMATCH".equals(matchStatus);
    }

    public boolean isNotFound() {
        return "SR_NOT_FOUND".equals(matchStatus);
    }
}
