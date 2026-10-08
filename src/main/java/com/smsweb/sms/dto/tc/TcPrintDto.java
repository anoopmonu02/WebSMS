package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

/** Everything a TC print page shows. {@code data} holds the values saved at issue time. */
@Getter
@Setter
public class TcPrintDto {

    private String schoolName;
    private String schoolAddress;
    private String schoolPhone;
    private String schoolBoard;

    private Long tcNumber;
    private String tcFormat;
    private String divisionName;
    private LocalDate issueDate;
    private Integer reissueNo;
    /** true when a later re-issue replaced this TC (the print shows a watermark). */
    private boolean superseded;
    private String cause;
    private String collectedByName;
    private String collectedByRelation;

    /** Printed values: studentName, fatherName, motherName, dob, gender, nationality, religion, registrationNo,
     *  psrn, srNo, className, sectionName, mediumName, session, lastDate, reason. */
    private Map<String, Object> data;
}
