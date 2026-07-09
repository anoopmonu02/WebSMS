package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One search result card for the "Single Student Update" mode of the Regional
 * Language Details screen — lets an admin fix/add one student's regional-language
 * fields without touching the bulk Excel workflow (e.g. for a handful of
 * mid-session new admissions).
 *
 * Carries the student's UUID only, never the raw numeric id — same rule as
 * the bulk-upload RegionalImportRowDto.
 */
@Getter
@Setter
public class RegionalStudentSearchResultDto {

    private String studentUuid;
    private String studentName;
    private String fatherName;
    private String motherName;
    private String address;
    private String classSrNo;
    private String gradeName;
    private String sectionName;

    // Existing saved values, if any — used to pre-fill the edit form.
    private String studentNameRegional;
    private String fatherNameRegional;
    private String motherNameRegional;
    private String addressRegional;
}
