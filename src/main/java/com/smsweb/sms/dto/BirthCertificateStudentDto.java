package com.smsweb.sms.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One search-result / print-source record for the Birth Certificate report
 * (Reports > Student Report > Birth Certificate).
 *
 * Read-only — this screen never writes to the database, it only looks up a
 * single student (by UUID, never the raw numeric id) and hands back every
 * field the printable certificate needs, in both English and the regional
 * language (e.g. Hindi) captured via the Student Regional Language Details
 * screen.
 */
@Getter
@Setter
public class BirthCertificateStudentDto {

    private String studentUuid;

    // ── English (source of truth — from `students`) ─────────────────────────
    private String studentName;
    private String fatherName;
    private String motherName;
    private String address;

    // ── Regional language, if captured — from `student_regional_detail` ─────
    private String studentNameRegional;
    private String fatherNameRegional;
    private String motherNameRegional;
    private String addressRegional;

    // ── Other certificate fields (English only — no regional equivalent needed) ─
    private String dob;          // pre-formatted, e.g. "12 Aug 2015"
    private String gender;
    private String nationality;
    private String religion;
    private String registrationNo;

    // ── Class context, shown in search results ───────────────────────────────
    private String classSrNo;
    private String gradeName;
    private String sectionName;
}
