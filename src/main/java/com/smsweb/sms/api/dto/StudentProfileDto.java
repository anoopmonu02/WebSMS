package com.smsweb.sms.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentProfileDto {

    @JsonProperty("student_id")
    private Long studentId;

    @JsonProperty("registration_no")
    private String registrationNo;

    @JsonProperty("class_sr_no")
    private String classSrNo;

    @JsonProperty("board_sr_no")
    private String boardSrNo;

    @JsonProperty("roll_no")
    private String rollNo;

    @JsonProperty("student_name")
    private String studentName;

    @JsonProperty("father_name")
    private String fatherName;

    @JsonProperty("mother_name")
    private String motherName;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("dob")
    private String dob;

    @JsonProperty("blood_group")
    private String bloodGroup;

    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("religion")
    private String religion;

    @JsonProperty("mobile1")
    private String mobile1;

    @JsonProperty("mobile2")
    private String mobile2;

    @JsonProperty("address")
    private String address;

    @JsonProperty("pincode")
    private String pincode;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("section")
    private String section;

    @JsonProperty("medium")
    private String medium;

    @JsonProperty("school_id")
    private Long schoolId;

    @JsonProperty("branch_id")
    private Long branchId;

    @JsonProperty("session_id")
    private Long sessionId;

    @JsonProperty("session_name")
    private String sessionName;

    @JsonProperty("school_name")
    private String schoolName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("pic")
    private String pic;

    @JsonProperty("aadhar_no")
    private String aadharNo;

    @JsonProperty("apaar_id")
    private String apaarId;

    @JsonProperty("pen_no")
    private String penNo;

    @JsonProperty("result")
    private int result;

    @JsonProperty("error_message")
    private String errorMessage;

    // success constructor
    public StudentProfileDto(
            Long studentId, String registrationNo,
            String classSrNo, String boardSrNo, String rollNo,
            String studentName, String fatherName, String motherName,
            String gender, String dob, String bloodGroup,
            String nationality, String religion,
            String mobile1, String mobile2,
            String address, String pincode,
            String grade, String section, String medium,
            Long schoolId, Long branchId,
            Long sessionId, String sessionName, String schoolName,
            String status, String pic,
            String aadharNo, String apaarId, String penNo) {
        this.studentId      = studentId;
        this.registrationNo = registrationNo;
        this.classSrNo      = classSrNo;
        this.boardSrNo      = boardSrNo;
        this.rollNo         = rollNo;
        this.studentName    = studentName;
        this.fatherName     = fatherName;
        this.motherName     = motherName;
        this.gender         = gender;
        this.dob            = dob;
        this.bloodGroup     = bloodGroup;
        this.nationality    = nationality;
        this.religion       = religion;
        this.mobile1        = mobile1;
        this.mobile2        = mobile2;
        this.address        = address;
        this.pincode        = pincode;
        this.grade          = grade;
        this.section        = section;
        this.medium         = medium;
        this.schoolId       = schoolId;
        this.branchId       = branchId;
        this.sessionId      = sessionId;
        this.sessionName    = sessionName;
        this.schoolName     = schoolName;
        this.status         = status;
        this.pic            = pic;
        this.aadharNo       = aadharNo;
        this.apaarId        = apaarId;
        this.penNo          = penNo;
        this.result         = 1;
        this.errorMessage   = null;
    }

    // error constructor
    public StudentProfileDto(int result, String errorMessage) {
        this.result       = result;
        this.errorMessage = errorMessage;
    }

    // getters
    public Long   getStudentId()      { return studentId; }
    public String getRegistrationNo() { return registrationNo; }
    public String getClassSrNo()      { return classSrNo; }
    public String getBoardSrNo()      { return boardSrNo; }
    public String getRollNo()         { return rollNo; }
    public String getStudentName()    { return studentName; }
    public String getFatherName()     { return fatherName; }
    public String getMotherName()     { return motherName; }
    public String getGender()         { return gender; }
    public String getDob()            { return dob; }
    public String getBloodGroup()     { return bloodGroup; }
    public String getNationality()    { return nationality; }
    public String getReligion()       { return religion; }
    public String getMobile1()        { return mobile1; }
    public String getMobile2()        { return mobile2; }
    public String getAddress()        { return address; }
    public String getPincode()        { return pincode; }
    public String getGrade()          { return grade; }
    public String getSection()        { return section; }
    public String getMedium()         { return medium; }
    public Long   getSchoolId()       { return schoolId; }
    public Long   getBranchId()       { return branchId; }
    public Long   getSessionId()      { return sessionId; }
    public String getSessionName()    { return sessionName; }
    public String getSchoolName()     { return schoolName; }
    public String getStatus()         { return status; }
    public String getPic()            { return pic; }
    public String getAadharNo()       { return aadharNo; }
    public String getApaarId()        { return apaarId; }
    public String getPenNo()          { return penNo; }
    public int    getResult()         { return result; }
    public String getErrorMessage()   { return errorMessage; }
}