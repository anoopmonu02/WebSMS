package com.smsweb.sms.services.automation;

import java.time.LocalDate;

/** One student on the long-absence list. */
public final class AbsenceStreakRow {

    private final String studentName;
    private final String grade;
    private final String section;
    private final String classSrNo;
    private final String fatherName;
    private final String mobile;
    private final int streakDays;
    private final LocalDate absentSince;
    private final LocalDate lastAbsent;

    public AbsenceStreakRow(String studentName, String grade, String section, String classSrNo, String fatherName,
                            String mobile, int streakDays, LocalDate absentSince, LocalDate lastAbsent) {
        this.studentName = studentName;
        this.grade = grade;
        this.section = section;
        this.classSrNo = classSrNo;
        this.fatherName = fatherName;
        this.mobile = mobile;
        this.streakDays = streakDays;
        this.absentSince = absentSince;
        this.lastAbsent = lastAbsent;
    }

    public String getStudentName() { return studentName; }
    public String getGrade() { return grade; }
    public String getSection() { return section; }
    public String getClassSrNo() { return classSrNo; }
    public String getFatherName() { return fatherName; }
    public String getMobile() { return mobile; }
    public int getStreakDays() { return streakDays; }
    public LocalDate getAbsentSince() { return absentSince; }
    public LocalDate getLastAbsent() { return lastAbsent; }
}
