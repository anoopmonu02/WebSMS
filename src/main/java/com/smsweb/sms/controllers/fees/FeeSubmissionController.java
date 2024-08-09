package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.fees.FeeSubmissionSub;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.reports.FeeReceiptService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/fees")
public class FeeSubmissionController {

    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final MonthmappingService mmService;
    private final FeeSubmissionService feeSubmissionService;
    private final FeeReceiptService receiptService;

    @Autowired
    public FeeSubmissionController(StudentService studentService, MonthmappingService mmService, FeeSubmissionService feeSubmissionService, AcademicStudentService academicStudentService, FeeReceiptService receiptService){
        this.studentService = studentService;
        this.mmService = mmService;
        this.feeSubmissionService = feeSubmissionService;
        this.academicStudentService = academicStudentService;
        this.receiptService = receiptService;
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
                if(responseMap.containsKey("fee_submission_not_allowed")){
                    redirectAttributes.addFlashAttribute("error", responseMap.get("fee_submission_not_allowed"));
                    return "redirect:/fees/fee-submit-form";
                }
                if(responseMap.containsKey("Feesubmission")){
                    FeeSubmission feeSubmission;
                    Object value = responseMap.get("Feesubmission");
                    if (value instanceof FeeSubmission) {
                        feeSubmission = (FeeSubmission)value;
                    }
                    AcademicStudent student = (AcademicStudent)responseMap.get("student");
                    redirectAttributes.addFlashAttribute("success","Fees Submitted for: "+student.getStudent().getStudentName());
                    return "redirect:/fees/receipt-print/"+responseMap.get("feeid");
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return "redirect:/fees/fee-submit-form";
    }

    @GetMapping("/receipt/{id}")
    public String getFeeReceiptPage(@PathVariable("id")Long id, Model model){
        try{
            AcademicStudent academicStudent = academicStudentService.getAcademicStudent(id).orElse(null);
            if(academicStudent!=null){
                model.addAttribute("student", academicStudent);
                model.addAttribute("hasStudent", academicStudent!=null);
                List<FeeSubmission> feeSubmissionList = feeSubmissionService.getAllFeeSubmissionByAcademicStudent(id);
                model.addAttribute("hasFeeSubmission", !feeSubmissionList.isEmpty());
                if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                    model.addAttribute("feeSubmissions", feeSubmissionList);
                } else{
                    model.addAttribute("feeSubmissionError", "Fees not found for: "+academicStudent.getStudent().getStudentName()+"!");
                }
            } else{
                model.addAttribute("studentError", "Student not found!");
            }
        }catch(Exception e){
            model.addAttribute("error", e.getLocalizedMessage());
        }
        return "/fees/fee-receipt";
    }

    @GetMapping("/receipt-print/{id}")
    public String getFeeReceipt(@PathVariable("id")Long id, Model model){
        try{
            SimpleDateFormat sf = new SimpleDateFormat("dd-MMM-yyyy");
            SimpleDateFormat sf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            FeeSubmission feeSubmission = feeSubmissionService.getFeeSubmissionById(id).orElse(null);
            AcademicStudent academicStudent = feeSubmission.getAcademicStudent();
            List<String> slipDateList = new ArrayList<>();
            if(academicStudent!=null && feeSubmission!=null){
                model.addAttribute("student", academicStudent);
                model.addAttribute("school", academicStudent.getSchool());
                model.addAttribute("academicYear", academicStudent.getAcademicYear().getSessionFormat());
                model.addAttribute("hasStudent", academicStudent!=null);
                //FeeSubmission feeSubmission = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(4L, 0L, id);
                model.addAttribute("hasFeeSubmission", feeSubmission!=null);
                if(feeSubmission!=null){
                    model.addAttribute("feeSubmission", feeSubmission);
                    HashMap<MonthMaster, Date> submittedMonthMap = new LinkedHashMap<>();
                    List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(14L, 4L);
                    List<FeeSubmission> feeSubmissionList = feeSubmissionService.getAllActiveFeeSubmissionByAcademicStudent(academicStudent.getId());
                    if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                        for(FeeSubmission submission: feeSubmissionList){
                            List<FeeSubmissionMonths> feeSubmissionMonthsList = submission.getFeeSubmissionMonths();
                            if(feeSubmissionMonthsList!=null && !feeSubmissionMonthsList.isEmpty()){
                                for(FeeSubmissionMonths feeMonths: feeSubmissionMonthsList){
                                    submittedMonthMap.put(feeMonths.getMonthMaster(), submission.getFeeSubmissionDate());
                                }
                            }
                        }
                    }
                    System.out.println("submittedMonthMap "+submittedMonthMap);
                    int i = 1;
                    for(MonthMapping mm: monthMappingList){
                        String dateString = "Month-"+ i +" ####("+mm.getMonthMaster().getMonthName().toUpperCase()+"): ####";
                        if(submittedMonthMap.containsKey(mm.getMonthMaster())){
                            dateString+="PAID " + sf.format(submittedMonthMap.get(mm.getMonthMaster()));
                        }
                        slipDateList.add(dateString);
                        i++;
                    }
                    model.addAttribute("feeSubmittedMonths", slipDateList);
                    System.out.println("feeSubmittedMonths: "+slipDateList);
                    //Calculate the fee
                    model.addAttribute("feesublist", feeSubmission.getFeeSubmissionSub());
                } else{
                    model.addAttribute("feeSubmissionError", "Fee not found for: "+academicStudent.getStudent().getStudentName()+"!");
                }

            } else{
                model.addAttribute("studentError", "Fees Object/Student not found!");
            }
        }catch(Exception e){
            e.printStackTrace();
            model.addAttribute("error", e.getLocalizedMessage());
        }
        return "/fees/receipt";
    }



}
