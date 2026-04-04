package com.smsweb.sms.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StudentProfileRequest {

    @JsonProperty("student_id")
    private Long studentId;

    @JsonProperty("school_id")
    private Long schoolId;

    @JsonProperty("branch_id")
    private Long branchId;

    @JsonProperty("session_id")
    private Long sessionId;

    // default constructor — required by Jackson
    public StudentProfileRequest() {}

    // getters
    public Long getStudentId() { return studentId; }
    public Long getSchoolId()  { return schoolId; }
    public Long getBranchId()  { return branchId; }
    public Long getSessionId() { return sessionId; }

    // setters — required by Jackson
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public void setSchoolId(Long schoolId)   { this.schoolId = schoolId; }
    public void setBranchId(Long branchId)   { this.branchId = branchId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
}
