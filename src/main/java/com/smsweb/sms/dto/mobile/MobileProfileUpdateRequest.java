package com.smsweb.sms.dto.mobile;

import lombok.Data;

/**
 * Request body for PUT /api/v1/student/profile. All fields optional at the
 * JSON level — the mobile edit screen sends the whole editable form back on
 * every save (it's pre-filled from the GET), so in practice every field
 * arrives populated, but the service still tolerates nulls defensively.
 *
 * Bank fields are all-or-nothing: if any one of bankId/accountNo/
 * branchName/ifscCode is provided, all four must be provided.
 */
@Data
public class MobileProfileUpdateRequest {

    private String bloodGroup;
    private String fatherQualification;
    private String motherQualification;
    private String email;

    private Long bankId;
    private String accountNo;
    private String branchName;
    private String ifscCode;

    private Integer height;
    private Integer weight;
    private Boolean haveHealthIssues;
    private Boolean haveEyeIssue;
    private String healthIssueDescription;
}
