package com.smsweb.sms.dto.mobile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight child record returned when a parent's mobile number
 * is linked to multiple students. Flutter uses this list to show
 * the "Select Child" picker before issuing a full JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildSummaryDto {
    private Long   academicStudentId;
    private String studentName;
    private String gradeName;
    private String sectionName;
    private String mediumName;
    private String classSrNo;
    private String rollNo;
    private String profilePicUrl;
}
