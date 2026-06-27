package com.smsweb.sms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one family group (one mobile number) for the migration preview UI.
 */
public class FamilyGroupPreview {

    private String mobile;
    private boolean accountExists;   // FamilyAccount row already in DB
    private List<StudentRow> students = new ArrayList<>();

    public FamilyGroupPreview() {}
    public FamilyGroupPreview(String mobile, boolean accountExists) {
        this.mobile = mobile;
        this.accountExists = accountExists;
    }

    public int getTotalStudents()  { return students.size(); }
    public int getAlreadyLinked()  { return (int) students.stream().filter(StudentRow::isAlreadyLinked).count(); }
    public int getNeedsLink()      { return getTotalStudents() - getAlreadyLinked(); }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String getMobile()                        { return mobile; }
    public void   setMobile(String mobile)           { this.mobile = mobile; }
    public boolean isAccountExists()                 { return accountExists; }
    public void   setAccountExists(boolean v)        { this.accountExists = v; }
    public List<StudentRow> getStudents()            { return students; }
    public void   setStudents(List<StudentRow> s)    { this.students = s; }

    // ── Inner row ─────────────────────────────────────────────────────────────
    public static class StudentRow {
        private Long   id;
        private String studentName;
        private String fatherName;
        private String schoolName;
        private String gradeName;
        private String sectionName;
        private String matchedVia;    // "mobile1" or "mobile2"
        private boolean alreadyLinked;

        public StudentRow() {}
        public StudentRow(Long id, String studentName, String fatherName,
                          String schoolName, String gradeName, String sectionName,
                          String matchedVia, boolean alreadyLinked) {
            this.id           = id;
            this.studentName  = studentName;
            this.fatherName   = fatherName;
            this.schoolName   = schoolName;
            this.gradeName    = gradeName;
            this.sectionName  = sectionName;
            this.matchedVia   = matchedVia;
            this.alreadyLinked = alreadyLinked;
        }

        public Long    getId()            { return id; }
        public String  getStudentName()   { return studentName; }
        public String  getFatherName()    { return fatherName; }
        public String  getSchoolName()    { return schoolName; }
        public String  getGradeName()     { return gradeName; }
        public String  getSectionName()   { return sectionName; }
        public String  getMatchedVia()    { return matchedVia; }
        public boolean isAlreadyLinked()  { return alreadyLinked; }
    }
}
