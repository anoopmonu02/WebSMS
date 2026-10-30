package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of the Family Mapping screen (Admin > Family Mapping).
 * Read-only view built from AcademicStudent + Student + the student's current
 * FamilyAccount (if any). The constructor is used by a JPQL constructor expression
 * in FamilyMappingQueryRepository, so its argument order/types must match that query.
 *
 * state:
 *   MAPPED    - linked to an ACTIVE FamilyAccount whose mobile equals the student's mobile1
 *   UNMAPPED  - no family account linked
 *   MISMATCH  - linked, but to an account with a different mobile, or to an INACTIVE account
 *   NO_MOBILE - mobile1 is blank (cannot be mapped until a number is entered)
 */
@Getter
@Setter
public class FamilyMappingRowDto {

    public static final String MAPPED = "MAPPED";
    public static final String UNMAPPED = "UNMAPPED";
    public static final String MISMATCH = "MISMATCH";
    public static final String NO_MOBILE = "NO_MOBILE";

    private Long studentId;
    private String studentName;
    private String gradeName;
    private String sectionName;
    private String fatherName;
    private String classSrNo;
    private Long psrn;
    private String mobile1;
    private String mobile2;

    // Current link (nullable) - kept so the state can be derived in memory
    private Long familyAccountId;
    private String familyMobile;
    private String familyStatus;

    private String state;
    /** Only set for MISMATCH: OTHER_MOBILE or ACCOUNT_INACTIVE. */
    private String mismatchReason;

    public FamilyMappingRowDto(Long studentId, String studentName, String gradeName, String sectionName,
                               String fatherName, String classSrNo, Long psrn, String mobile1, String mobile2,
                               Long familyAccountId, String familyMobile, String familyStatus) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.gradeName = gradeName;
        this.sectionName = sectionName;
        this.fatherName = fatherName;
        this.classSrNo = classSrNo;
        this.psrn = psrn;
        this.mobile1 = mobile1;
        this.mobile2 = mobile2;
        this.familyAccountId = familyAccountId;
        this.familyMobile = familyMobile;
        this.familyStatus = familyStatus;
    }

    public String getGradeSection() {
        String g = gradeName == null ? "" : gradeName;
        String s = sectionName == null ? "" : sectionName;
        return s.isEmpty() ? g : g + "-" + s;
    }
}
