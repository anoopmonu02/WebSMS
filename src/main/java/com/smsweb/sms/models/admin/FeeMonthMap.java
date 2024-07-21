package com.smsweb.sms.models.admin;

import com.smsweb.sms.models.universal.Feehead;

import java.util.Date;

public class FeeMonthMap {

    private Long id;
    private School school;
    private AcademicYear academicYear;
    private Date creationDate;
    private Date lastUpdated;

    private Feehead feehead;
    private String description;

    //TODO-will add 2 more attributes - createdBy, updatedBy
}
