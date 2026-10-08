package com.smsweb.sms.dto.tc;

/** One line of the student picker on the strike-off form. */
public record StudentPickRow(Long academicStudentId,
                             String studentName,
                             String fatherName,
                             String motherName,
                             String className,
                             String sectionName,
                             String mediumName,
                             String srNo,
                             String registrationNo) {
}
