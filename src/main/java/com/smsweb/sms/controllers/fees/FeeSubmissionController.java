package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/fees")
public class FeeSubmissionController {

    private final StudentService studentService;
    private final MonthmappingService mmService;

    @Autowired
    public FeeSubmissionController(StudentService studentService, MonthmappingService mmService){
        this.studentService = studentService;
        this.mmService = mmService;
    }

    @GetMapping("/fee-submit-form")
    public String getFeeSubmissionForm(Model model){
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(14L, 4L);
        System.out.println(monthMappingList);
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        return "/fees/feesubmitform";
    }
}
