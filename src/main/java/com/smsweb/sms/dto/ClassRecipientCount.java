package com.smsweb.sms.dto;

import lombok.Getter;

/** One grade-section with the number of Active students a class notification would reach. */
@Getter
public class ClassRecipientCount {

    private final Long gradeId;
    private final Long sectionId;
    private final String gradeName;
    private final String sectionName;
    private final long count;

    public ClassRecipientCount(Long gradeId, Long sectionId, String gradeName, String sectionName, long count) {
        this.gradeId = gradeId;
        this.sectionId = sectionId;
        this.gradeName = gradeName;
        this.sectionName = sectionName;
        this.count = count;
    }

    public String getLabel() {
        return gradeName + " - " + sectionName;
    }
}
