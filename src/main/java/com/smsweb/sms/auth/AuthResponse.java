package com.smsweb.sms.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        @JsonProperty("access_token")       String  accessToken,
        @JsonProperty("token_type")         String  tokenType,
        @JsonProperty("expires_in_minutes") long    expiresInMinutes,
        @JsonProperty("refresh_token")      String  refreshToken,
        @JsonProperty("user_id")            Long    userId,
        @JsonProperty("user_name")          String  userName,
        @JsonProperty("role")               String  role,
        @JsonProperty("school_id")          Long    schoolId,      // Customer.id
        @JsonProperty("branch_id")          Long    branchId,      // School.id
        @JsonProperty("session_id")         Long    sessionId,     // AcademicYear.id
        @JsonProperty("result")             int     result,
        @JsonProperty("error_message")      String  errorMessage
) {}
