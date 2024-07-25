package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.services.student.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fees")
public class FeeSubmissionController {

    private final StudentService studentService;

    @Autowired
    public FeeSubmissionController(StudentService studentService){
        this.studentService = studentService;
    }

    @GetMapping("/fee-submit-form")
    public String getFeeSubmissionForm(){
        return "/fees/feesubmitform";
    }
}
