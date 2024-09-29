package com.smsweb.sms.config;

import com.smsweb.sms.models.admin.AcademicYear;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AcademicYearHolder {
    private AcademicYear currentAcademicYear;

    public AcademicYear getCurrentAcademicYear() {
        return currentAcademicYear;
    }

    public void setCurrentAcademicYear(AcademicYear academicYear) {
        this.currentAcademicYear = academicYear;
    }
}

