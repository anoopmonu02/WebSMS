package com.smsweb.sms.dto;

import lombok.Getter;

/**
 * Read-only row: one Active child who already shares a mobile number / family account.
 * Built by a JPQL constructor expression in FamilyMappingQueryRepository.findFamilyChildren().
 */
@Getter
public class ContactFamilyChildRow {

    private final Long studentId;
    private final String studentName;
    private final String gradeName;
    private final String sectionName;
    private final Long schoolId;

    public ContactFamilyChildRow(Long studentId, String studentName, String gradeName,
                                 String sectionName, Long schoolId) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.gradeName = gradeName;
        this.sectionName = sectionName;
        this.schoolId = schoolId;
    }
}
