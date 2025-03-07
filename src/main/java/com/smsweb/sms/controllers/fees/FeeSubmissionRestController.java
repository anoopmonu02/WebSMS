package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.*;
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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.thymeleaf.TemplateEngine;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
public class FeeSubmissionRestController extends BaseController {

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
    public ResponseEntity<?> searchStudentForFeePage(@PathVariable("query") String query, Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<AcademicStudent> students = academicStudentService.searchStudents(query, academicYear.getId(), school.getId());
        return ResponseEntity.ok(students);
    }

    @GetMapping("/searchStudentForOtherPage/{query}")
    public ResponseEntity<?> searchStudentForOtherPage(@PathVariable("query") String query, Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<AcademicStudent> students = academicStudentService.searchStudents(query, academicYear.getId(), school.getId());
        return ResponseEntity.ok(students);
    }

    @GetMapping("/getStudentDetailForFee/{id}")
    public ResponseEntity<?> getStudentDetailForFee(@PathVariable("id") Long id, Model model){
        Map result = new HashMap<>();
        //first fetch student details like fathername/mothername/class/section/contact-no/student type[old/new]/ etc.
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                //TODO - fetch previous pending if any

                //Calculating current month -
                //TODO - Can get date from server, so no dependent on client machine date, for now using local date
                int currentMonth = Calendar.getInstance().get(Calendar.MONTH)+1;
                List<FeeDate> fdList = feedateService.getByGivenMonth(academicYear.getId(), school.getId(), currentMonth);
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
                        FeeSubmission feeSubmission = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(school.getId(), academicYear.getId(), academicStudent.getId());
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
                Map paidMonths = feeSubmissionService.getPaidMonths(school.getId(), academicYear.getId(), academicStudent.getId());
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

    @GetMapping("/getStudentDetailsForSibling/{id}")
    public ResponseEntity<?> getStudentDetailsForSibling(@PathVariable("id") Long id, Model model){
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                List<AcademicStudent> allSiblingsList = academicStudentService.searchSiblings(academicYear.getId(), academicStudent);
                if(allSiblingsList!=null && !allSiblingsList.isEmpty()){
                    result.put("siblingList", allSiblingsList);
                    result.put("hasSiblings", !allSiblingsList.isEmpty());
                } else{
                    result.put("noSibling", "No Sibling(s) found");
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

    @GetMapping("/searchStudentIndividual/{id}")
    public ResponseEntity<?> getStudentDetail(@PathVariable("id")Long id, Model model){
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                result.put("academicStudent", academicStudent);
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
    public ResponseEntity<?> getStudentDetailForDiscount(@PathVariable("id") Long id, Model model){
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                //AcademicYear academicYear = academicyearService.getAcademicyearById(14L).get();
                result.put("student",academicStudent);
                //result.put("academicYear", academicYear);
                //collect discount details if any?
                StudentDiscount studentDiscount = studentDiscountService.getStudentDiscountForStudent(school.getId(), academicYear.getId(), id).orElse(null);
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
    public ResponseEntity<?> getFeeDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map result = new HashMap<>();
        try{
            //{checkBoxes=July, gradeId=2, academicStudentId=5}
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map serviceResultMap = feeSubmissionService.getFeeDetailsBasedOnMonth(school.getId(), academicYear.getId(), academicStuId, requestBody.get("checkBoxes"), gradeId);
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
    public ResponseEntity<?> getDiscountDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map result = new HashMap<>();
        try{
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map serviceResultMap = feeSubmissionService.getDiscountDetailsBasedOnMonth(school.getId(), academicYear.getId(), academicStuId, requestBody.get("checkBoxes"), gradeId);
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
    public ResponseEntity<?> getFineDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map fineMap = new HashMap();
        Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
        Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent student = academicStudentService.searchStudentById(academicStuId, academicYear.getId(), school.getId());
        Fine fine = fineService.getAllFines(school.getId(), academicYear.getId()).get(0);
        /*String subDate = requestBody.get("submissionDate")!=null?requestBody.get("submissionDate"):"";
        String feeDate = requestBody.get("feeDate")!=null?requestBody.get("feeDate"):"";*/

        //Calculating New Fine - As per customer need
        int maxFineAmount = fine.getFineAmount() * fine.getMaxCalculated();
        List<String> selectedMonthList = Arrays.stream(requestBody.getOrDefault("checkBoxes","").split("-")).toList();
        if(selectedMonthList!=null && !selectedMonthList.isEmpty()){
            try{
                int finalFineAmount = feeSubmissionService.calculateFine(selectedMonthList, school, academicYear, maxFineAmount, fine);
                fineMap.put("fineamount", finalFineAmount);
            }catch(Exception e){
                fineMap.put("fineamount", 0);
                e.printStackTrace();
                throw new RuntimeException("Error calculating fine", e);
            }
        }
        return ResponseEntity.ok(fineMap);
    }

    @PostMapping("/getFineDetailsBasedOnMonth_Old_Request")
    public ResponseEntity<?> getFineDetailsBasedOnMonth_Old(@RequestBody Map<String, String> requestBody, Model model){
        System.out.println("-=-=-=-=--=-== "+requestBody);
        Map fineMap = new HashMap();
        double fineAmount = 0.0;
        Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
        Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent student = academicStudentService.searchStudentById(academicStuId, academicYear.getId(), school.getId());
        //AcademicYear academicYear = academicyearService.getCurrentAcademicYear(); // Replace with actual implementation
        Fine fine = fineService.getAllFines(school.getId(), academicYear.getId()).get(0);
        String subDate = requestBody.get("submissionDate")!=null?requestBody.get("submissionDate"):"";
        String feeDate = requestBody.get("feeDate")!=null?requestBody.get("feeDate"):"";

        //Calculating New Fine - As per customer need
        int maxFineAmount = fine.getFineAmount() * fine.getMaxCalculated();
        List<String> selectedMonthList = Arrays.stream(requestBody.getOrDefault("checkBoxes","").split("-")).toList();
        if(selectedMonthList!=null && !selectedMonthList.isEmpty()){
            try{
                int finalFineAmount = feeSubmissionService.calculateFine(selectedMonthList, school, academicYear, maxFineAmount, fine);
                fineMap.put("fineamount", finalFineAmount);
            }catch(Exception e){
                fineMap.put("fineamount", 0);
                e.printStackTrace();
                throw new RuntimeException("Error calculating fine", e);
            }
        }


        FeeSubmission lastSubmittedFees = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(school.getId(), academicYear.getId(), academicStuId);
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

            int monthDiff = monthmappingService.monthDifference(academicYear.getId(), school.getId(), lastMonthName, subDate);
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
        int removeFineResult = monthmappingService.findMonthDifferenceToNullify(academicYear.getId(), school.getId(), lastMonthName);
        if (removeFineResult > 0) {
            fineAmount = 0.0;
        }
        fineMap.put("fineamount", fineAmount);
        //return Map.of("fineAmount", fineAmount);
        return ResponseEntity.ok(fineMap);
    }


    @PostMapping("/getFeeReminderDetails")
    public ResponseEntity<?> getFeeReminderDetails(@RequestBody Map<String, String> requestBody, Model model){
        Map result = new HashMap<>();
        try{
            System.out.println("requestBody--------> "+requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                result = feeSubmissionService.calculateFeeReminder(requestBody, school, academicYear);
                System.out.println("responseMap "+result);
                //System.out.println("result "+result.keySet());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getStudentFeeDetails/{id}")
    public ResponseEntity<?> getStudentFeeDetails(@PathVariable("id") Long id, Model model){
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                Student student = academicStudent.getStudent();
                result.put("student",academicStudent);
                //collect student fee details if any?

                List<FeeSubmission> feeSubmissionList = feeSubmissionService.getAllFeeSubmissionByAcademicStudent(id);
                if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                    result.put("feeSubmissions", feeSubmissionList);
                }
                else{
                    result.put("feeSubmissionError", "Fees not found for: "+academicStudent.getStudent().getStudentName()+"!");
                }
            } else{
                result.put("studentError", "Student:"+ academicStudent.getStudent().getStudentName() +" not found.");
            }

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/student-receipt-print/{id}")
    public ResponseEntity<?> getFeeReceipt(@PathVariable("id")Long id, Model model){
        //Map result = new HashMap<>();
        /*try{
            SimpleDateFormat sf = new SimpleDateFormat("dd-MMM-yyyy");
            SimpleDateFormat sf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            FeeSubmission feeSubmission = feeSubmissionService.getFeeSubmissionById(id).orElse(null);
            AcademicStudent academicStudent = feeSubmission.getAcademicStudent();
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
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
                    List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
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
        }*/
        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        Map<String, Object> receiptData = feeSubmissionService.getFeeReceiptData(id, school, academicYear);
        if(receiptData==null || receiptData.isEmpty()){
            receiptData.put("error","Unable to print");
        }
        //System.out.println("receiptData "+ receiptData);
        System.out.println("--->>>>><<<<<<<<<==========");
        return ResponseEntity.ok(receiptData);
    }

    @GetMapping("/searchReceiptForFeePage/{query}")
    public ResponseEntity<?> searchReceiptForFeePage(@PathVariable("query") String query, Model model){
        Map<String, Object> receiptData = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        FeeSubmission feeSubmission = feeSubmissionService.getFeeDetailsForReceipt(query, school, academicYear);
        if(feeSubmission!=null){
            receiptData.put("feeSubmission",feeSubmission);
        } else{
            receiptData.put("error","Fee detail not found.");
        }
        return ResponseEntity.ok(receiptData);
    }

    @PostMapping("/getFeeCollectionDetailsUserwise")
    public ResponseEntity<?> getFeeCollectionDetailsUserwise(@RequestBody Map<String, String> requestBody, Model model){
        Map<String, Object> receiptData = new HashMap<>();
        try{
            System.out.println("requestBody--------> "+requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateFeeSubmissionUserWise(requestBody, school, academicYear);
                //System.out.println("responseMap "+result);
                //System.out.println("result "+result.keySet());
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @PostMapping("/getFeeCancelledDetails")
    public ResponseEntity<?> getFeeCancelledDetails(@RequestBody Map<String, String> requestBody, Model model){
        Map<String, Object> receiptData = new HashMap<>();
        try{
            System.out.println("requestBody--------> "+requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateCancelledFees(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @PostMapping("/getTotalFeeSubmittedDetails")
    public ResponseEntity<?> getTotalFeeSubmittedDetails(@RequestBody Map<String, String> requestBody, Model model){
        Map<String, Object> receiptData = new HashMap<>();
        try{
            System.out.println("requestBody--------> "+requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateTotalSubmittedFees(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }
}
