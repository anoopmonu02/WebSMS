package com.smsweb.sms.config;

import com.smsweb.sms.models.admin.School;
import org.springframework.stereotype.Component;

@Component
public class SchoolHolder {
    private School school;

    public School getCurrentSchool(){
        return school;
    }

    public void setCurrentSchool(School school){
        this.school = school;
    }

}
