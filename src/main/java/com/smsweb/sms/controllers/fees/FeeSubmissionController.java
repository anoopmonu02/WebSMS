package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/fees")
public class FeeSubmissionController {

    private final StudentService studentService;
    private final MonthmappingService mmService;
    private final FeeSubmissionService feeSubmissionService;

    @Autowired
    public FeeSubmissionController(StudentService studentService, MonthmappingService mmService, FeeSubmissionService feeSubmissionService){
        this.studentService = studentService;
        this.mmService = mmService;
        this.feeSubmissionService = feeSubmissionService;
    }

    @GetMapping("/fee-submit-form")
    public String getFeeSubmissionForm(Model model){
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(14L, 4L);
        System.out.println(monthMappingList);
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("feesubmissionobj", new FeeSubmission());
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        return "/fees/feesubmitform";
    }

    @PostMapping("/feesubmit")
    public String saveFeeSubmission(HttpServletRequest request, RedirectAttributes redirectAttributes){
        try{
            Map paramMap = request.getParameterMap();
            System.out.println("==== "+paramMap.keySet());
            /*for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                System.out.println("Key: " + key);
                System.out.println("Values:");
                for (String value : values) {
                    System.out.println(" - " + value);
                }
                System.out.println(); // Newline for better readability
            }*/
            Map responseMap = feeSubmissionService.save(paramMap);
            if(responseMap!=null){
                if(responseMap.containsKey("Feesubmission")){
                    FeeSubmission feeSubmission;
                    Object value = responseMap.get("Feesubmission");
                    if (value instanceof FeeSubmission) {
                        feeSubmission = (FeeSubmission)value;
                    }
                    AcademicStudent student = (AcademicStudent)responseMap.get("student");
                    redirectAttributes.addFlashAttribute("Fees Submitted for: "+student.getStudent().getStudentName());
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return "redirect:/fees/fee-submit-form";
    }
}
