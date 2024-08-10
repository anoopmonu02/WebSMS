package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FeeDate;
import com.smsweb.sms.models.admin.Fine;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.FeedateService;
import com.smsweb.sms.services.admin.FineService;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.reports.FeeReceiptService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentDiscountService;
import com.smsweb.sms.services.student.StudentService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.thymeleaf.TemplateEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
public class FeeSubmissionRestController {

    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final AcademicyearService academicyearService;

    private final FeeSubmissionService feeSubmissionService;
    private final FeedateService feedateService;
    private final StudentDiscountService studentDiscountService;
    private final FineService fineService;
    private final MonthmappingService monthmappingService;
    private final FeeReceiptService receiptService;
    private final MonthmappingService mmService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    public FeeSubmissionRestController(StudentService studentService, AcademicyearService academicyearService, AcademicStudentService academicStudentService,
                                       FeeSubmissionService feeSubmissionService, FeedateService feedateService, StudentDiscountService studentDiscountService,
                                       FineService fineService, MonthmappingService monthmappingService, FeeReceiptService receiptService, MonthmappingService mmService) {
        this.studentService = studentService;
        this.academicyearService = academicyearService;
        this.academicStudentService = academicStudentService;
        this.feeSubmissionService = feeSubmissionService;
        this.feedateService = feedateService;
        this.studentDiscountService = studentDiscountService;
        this.fineService = fineService;
        this.monthmappingService = monthmappingService;
        this.receiptService = receiptService;
        this.mmService = mmService;
    }

    @GetMapping("/searchStudentForFeePage/{query}")
    public ResponseEntity<?> searchStudentForFeePage(@PathVariable("query") String query){
        List<AcademicStudent> students = academicStudentService.searchStudents(query, 14L, 4L);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/getStudentDetailForFee/{id}")
    public ResponseEntity<?> getStudentDetailForFee(@PathVariable("id") Long id){
        Map result = new HashMap<>();
        //first fetch student details like fathername/mothername/class/section/contact-no/student type[old/new]/ etc.
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, 14L, 4L);
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
                //TODO - fetch previous pending if any

                //Calculating current month -
                //TODO - Can get date from server, so no dependent on client machine date, for now using local date
                int currentMonth = Calendar.getInstance().get(Calendar.MONTH)+1;
                List<FeeDate> fdList = feedateService.getByGivenMonth(14L, 4L, currentMonth);
                FeeDate fd = null;
                if(fdList!=null && !fdList.isEmpty()){
                    fd = fdList.get(0);
                }
                // = feedateService.getByGivenMonth(14L, 4L, currentMonth).get(0);
                if (fd != null) {
                    //if any pending fees left then redirect to pending fee page
                    //NEED TO DISCUSS HOW TO HANDLE THIS:- 1). REDIRECT TO PENDING PAGE 2). OPEN A POPUP MODEL AND SUBMIT
                    //Calculate lastsubmittedfees for previous balance pending if any
                    if(1==2){
                        //to calculate the previous pending fee
                    } else{
                        result.put("student",academicStudent);
                        result.put("feeDate",fd);
                        result.put("todayDate",new Date());
                        result.put("countStu", academicStudentService.countNoOfYearsOfStudent(academicStudent)>1?"OLD":"NEW");
                        result.put("academicYear", academicYear);
                        FeeSubmission feeSubmission = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(4L, 14L, academicStudent.getId());
                        if(feeSubmission!=null){
                            FeeSubmission feeSub = feeSubmission;
                            result.put("previousBalance",feeSub.getFeeSubmissionBalance().getBalanceAmount());
                        } else{
                            result.put("previousBalance",0.0);
                        }
                    }
                } else{
                    //Fee date is missing.
                    result.put("noFeeDate", "No Fee-Date found, Please add fee-date first.");
                }
                //Calculate Paid months
                Map paidMonths = feeSubmissionService.getPaidMonths(4L, 14L, academicStudent.getId());
                if(paidMonths!=null && !paidMonths.isEmpty()){
                    if(paidMonths.containsKey("MonthError")){
                        result.put("Paid_Month_Error", paidMonths.get("MonthError"));
                    } else{
                        result.put("PaidMonths", paidMonths.get("paidMonths"));
                    }
                } else{
                    result.put("Paid_Month_Error", "No fee submission data found.");
                }
            } else{
                result.put("noAcademicStudent", "Student:"+ academicStudent.getStudent().getStudentName() +" not found.");
            }

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getStudentDetailForDiscount/{id}")
    public ResponseEntity<?> getStudentDetailForDiscount(@PathVariable("id") Long id){
        Map result = new HashMap<>();
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, 14L, 4L);
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                //AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
                result.put("student",academicStudent);
                //result.put("academicYear", academicYear);
                //collect discount details if any?
                StudentDiscount studentDiscount = studentDiscountService.getStudentDiscountForStudent(4L, 14L, id).orElse(null);
                if(studentDiscount!=null){
                    result.put("assignedDiscount", studentDiscount);
                }
            } else{
                result.put("noAcademicStudent", "Student:"+ academicStudent.getStudent().getStudentName() +" not found.");
            }

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/updateContact")
    public ResponseEntity<?> updateContact(@RequestBody Map<String, String> requestBody){
        Map result = new HashMap<>();
        try{
            if(requestBody!=null){
                String contactNo = requestBody.get("contactNumber");
                Long stuId = Long.parseLong(requestBody.get("stuId"));
                Long returnId = studentService.updateContact(contactNo, stuId);
                if(returnId>0){
                    result.put("success","Contact number: "+ contactNo +" updated successfully.");
                }
            } else{
                result.put("empty_response", "Request params are empty");
            }
        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }


    @PostMapping("/getFeeDetailsBasedOnMonth")
    public ResponseEntity<?> getFeeDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map result = new HashMap<>();
        try{
            //{checkBoxes=July, gradeId=2, academicStudentId=5}
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                Map serviceResultMap = feeSubmissionService.getFeeDetailsBasedOnMonth(4L, 14L, academicStuId, requestBody.get("checkBoxes"), gradeId);
                System.out.println("========================================================================= "+serviceResultMap);
                if(serviceResultMap!=null && !serviceResultMap.isEmpty()){
                    result.put("feelist", serviceResultMap.get("feelist"));
                    result.put("paymentlist", serviceResultMap.get("paymentlist"));
                } else{
                    result.put("data_error","Unable to fetch data. Contact to Admin.");
                }
            } else{
                result.put("empty_response", "Request params are empty");
            }
        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    //getDiscountDetailsBasedOnMonth
    @PostMapping("/getDiscountDetailsBasedOnMonth")
    public ResponseEntity<?> getDiscountDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map result = new HashMap<>();
        try{
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                Map serviceResultMap = feeSubmissionService.getDiscountDetailsBasedOnMonth(4L, 14L, academicStuId, requestBody.get("checkBoxes"), gradeId);
                System.out.println("========================================================================= "+serviceResultMap);
                if(serviceResultMap!=null && !serviceResultMap.isEmpty()){
                    result.put("discountlist", serviceResultMap.get("discountdata"));
                    /*result.put("paymentlist", serviceResultMap.get("paymentlist"));*/
                } else{
                    result.put("data_error","Unable to fetch data. Contact to Admin.");
                }
            } else{
                result.put("empty_response", "Request params are empty");
            }
        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }


    @PostMapping("/getFineDetailsBasedOnMonth")
    public ResponseEntity<?> getFineDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map fineMap = new HashMap();
        double fineAmount = 0.0;
        Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
        Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
        AcademicStudent student = academicStudentService.searchStudentById(academicStuId, 14L, 4L);
        AcademicYear academicYear = academicyearService.getCurrentAcademicYear(); // Replace with actual implementation
        Fine fine = fineService.getAllFines(4L, 14L).get(0);
        String subDate = requestBody.get("submissionDate")!=null?requestBody.get("submissionDate"):"";
        String feeDate = requestBody.get("feeDate")!=null?requestBody.get("feeDate"):"";


        FeeSubmission lastSubmittedFees = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(4L, 14L, academicStuId);
        if (lastSubmittedFees != null) {
            Date lastFeeSubmissionDate = lastSubmittedFees.getFeeSubmissionDate();
            LocalDate lastFeeSubmissionLocalDate = Instant.ofEpochMilli(lastFeeSubmissionDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            String lastMonthName = lastFeeSubmissionLocalDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
            List<FeeSubmissionMonths> feeSubmissionMonths = lastSubmittedFees.getFeeSubmissionMonths();
            if(feeSubmissionMonths!=null && !feeSubmissionMonths.isEmpty()){
                for(FeeSubmissionMonths fsm:feeSubmissionMonths){
                    lastMonthName = fsm.getMonthMaster().getMonthName();
                }
            }

            int monthDiff = monthmappingService.monthDifference(14L, 4L, lastMonthName, subDate);
            int cdiff = monthmappingService.currentDateDifference(feeDate, subDate);
            try {
                if (monthDiff >= 3) {
                    fineAmount = fine.getFineAmount() * fine.getMaxCalculated();
                } else if (monthDiff == 2) {
                    if (cdiff<0) {
                        fineAmount = 2 * fine.getFineAmount();
                    } else {
                        fineAmount = fine.getFineAmount();
                    }
                } else if (monthDiff == 1) {
                    if (cdiff<0) {
                        fineAmount = fine.getFineAmount();
                    } else {
                        fineAmount = 0.0;
                    }
                } else {
                    fineAmount = 0.0;
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error calculating fine", e);
            }
        } else {
            int monthdiff = monthmappingService.firstMonthDifference(subDate, academicYear.getStartDate());
            int cdiff = monthmappingService.currentDateDifference(feeDate, subDate);
            try {
                //int dt = request.getSubmissionDate().getMonthValue() - academicYear.getStartDate().getMonthValue();
                if (monthdiff > 2) {
                    fineAmount = fine.getFineAmount() * fine.getMaxCalculated();
                } else if (monthdiff<0 && monthdiff >= -2) {
                    if (cdiff<0) {
                        fineAmount = (monthdiff + (monthdiff * monthdiff) + 1) * fine.getFineAmount();
                    } else {
                        fineAmount = (monthdiff + (monthdiff * monthdiff)) * fine.getFineAmount();
                    }
                } else if (monthdiff < -2) {
                    fineAmount = fine.getFineAmount() * fine.getMaxCalculated();
                } else {
                    if (cdiff<0) {
                        fineAmount = (monthdiff + 1) * fine.getFineAmount();
                    } else {
                        fineAmount = monthdiff * fine.getFineAmount();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error calculating fine", e);
            }
        }
        String monthNames = requestBody.get("checkBoxes");
        List monNames = Arrays.stream(monthNames.split("-")).toList();
        String lastMonthName = "";
        for(Object monthNm: monNames){
            lastMonthName = monthNm.toString();
        }
        //String currentMonthName = LocalDate.now().getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        int removeFineResult = monthmappingService.findMonthDifferenceToNullify(14L,4L, lastMonthName);
        if (removeFineResult > 0) {
            fineAmount = 0.0;
        }
        fineMap.put("fineamount", fineAmount);
        //return Map.of("fineAmount", fineAmount);
        return ResponseEntity.ok(fineMap);
    }



}
