package com.smsweb.sms.dto.mobile;

import lombok.Builder;
import lombok.Data;

/**
 * Response body for POST /api/v1/auth/login
 *
 * Contains the JWT token and basic student info the Flutter app needs immediately
 * to render the home screen (name, class, SR number, school name, etc.)
 */
@Data
@Builder
public class MobileLoginResponse {

    private String loginType;        // "SINGLE"
    private boolean mustChangePassword;
    private String token;
    private String tokenType;           // Always "Bearer"
    private String refreshToken;        // feature #10 — long-lived, single-use, rotated on each refresh

    // Student identity
    private Long   academicStudentId;
    private String studentName;
    private String fatherName;
    private String classSrNo;
    private String rollNo;

    // Class info
    private String gradeName;
    private String sectionName;
    private String mediumName;

    // School info
    private String schoolName;
    private Long   schoolId;
    private Long   academicYearId;
    private String academicYearName;

    // Profile picture path (relative URL)
    private String profilePicUrl;
}
