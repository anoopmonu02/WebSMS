package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
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
import com.smsweb.sms.services.student.SiblingGroupService;
import com.smsweb.sms.services.student.StudentDiscountService;
import com.smsweb.sms.services.student.StudentService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestController
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class FeeSubmissionRestController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(FeeSubmissionRestController.class);


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
    private final SiblingGroupService siblingGroupService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    public FeeSubmissionRestController(StudentService studentService, AcademicyearService academicyearService, AcademicStudentService academicStudentService,
                                       FeeSubmissionService feeSubmissionService, FeedateService feedateService, StudentDiscountService studentDiscountService,
                                       FineService fineService, MonthmappingService monthmappingService, FeeReceiptService receiptService,
                                       MonthmappingService mmService, SiblingGroupService siblingGroupService) {
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
        this.siblingGroupService = siblingGroupService;
    }

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/searchStudentForFeePage/{query}")
    public ResponseEntity<?> searchStudentForFeePage(
            @PathVariable("query") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        log.info("Inside searchStudentForFeePage");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<AcademicStudent> raw = academicStudentService.searchStudents(query, academicYear.getId(), school.getId(), page);
        List<Map<String, Object>> leanList = new ArrayList<>();
        if (raw != null) {
            for (AcademicStudent as : raw) leanList.add(studentService.toLeanAcademicStudentMap(as));
        }
        // Returns plain array — all existing callers (fee-receipt, messageSender, etc.) continue to work.
        // feesubmitform.js detects hasMore by checking data.length === 10.
        return ResponseEntity.ok(leanList);
    }

    /**
     * Global student search for sibling group "Add Manually" — searches ALL active students
     * regardless of school or academic year. Returns each student's latest active enrollment
     * so the display shows their current branch, grade and section.
     */
    @GetMapping("/searchStudentForSiblingPage/{query}")
    public ResponseEntity<?> searchStudentForSiblingPage(
            @PathVariable("query") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        log.info("Inside searchStudentForSiblingPage (global)");
        List<AcademicStudent> raw = academicStudentService.searchGlobalStudentsByName(query, page);
        List<Map<String, Object>> leanList = new ArrayList<>();
        if (raw != null) {
            for (AcademicStudent as : raw) leanList.add(studentService.toLeanAcademicStudentMap(as));
        }
        return ResponseEntity.ok(leanList);
    }

    /**
     * Cross-school individual fetch — kept for backward compatibility.
     * New flow uses /checkSiblingEligibility + cached data instead.
     */
    @GetMapping("/searchStudentIndividualGlobal/{id}")
    public ResponseEntity<?> searchStudentIndividualGlobal(@PathVariable("id") Long id, Model model) {
        log.info("Inside searchStudentIndividualGlobal");
        Map<String, Object> result = new HashMap<>();
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentByIdCrossBranch(id, academicYear.getSessionFormat());
        try {
            if (academicStudent != null) {
                result.put("academicStudent", studentService.toLeanAcademicStudentMap(academicStudent));
            } else {
                result.put("noAcademicStudent", "Student not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Error: " + e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * UX-level sibling eligibility check — called when staff clicks "Add Student".
     * Checks if the student (by student.id) is already in ANY active sibling group
     * across ALL branches. Prevents duplicate discount assignment.
     *
     * Returns: {eligible: true} OR {blocked: true, groupName, schoolName}
     */
    @GetMapping("/checkSiblingEligibility/{studentId}")
    public ResponseEntity<?> checkSiblingEligibility(@PathVariable("studentId") Long studentId) {
        log.info("Inside checkSiblingEligibility - studentId={}", studentId);
        return ResponseEntity.ok(siblingGroupService.checkSiblingEligibility(studentId));
    }

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/searchStudentForOtherPage/{query}")
    public ResponseEntity<?> searchStudentForOtherPage(
            @PathVariable("query") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        log.info("Inside searchStudentForOtherPage");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<AcademicStudent> raw = academicStudentService.searchStudents(query, academicYear.getId(), school.getId(), page);
        List<Map<String, Object>> leanList = new ArrayList<>();
        if (raw != null) {
            for (AcademicStudent as : raw) leanList.add(studentService.toLeanAcademicStudentMap(as));
        }
        return ResponseEntity.ok(leanList);
    }

    /*@Transactional
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
                        //result.put("countStu", academicStudentService.countNoOfYearsOfStudent(academicStudent)>1?"OLD":"NEW");
                        if(academicStudentService.countNoOfYearsOfStudent(academicStudent)>1){
                            result.put("countStu", "OLD");
                        } else{
                            result.put("countStu", academicStudent.getStudent().getStudentType().equalsIgnoreCase("old")?"OLD":"NEW");
                        }
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
    }*/

    @Transactional
    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/getStudentDetailForFee/{id}")
    public ResponseEntity<?> getStudentDetailForFee(@PathVariable("id") Long id, Model model) {
        log.info("Inside getStudentDetailForFee");
        Map<String, Object> result = new HashMap<>();

        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(
                id, academicYear.getId(), school.getId());

        try {
            if (academicStudent != null) {
                Student student = academicStudent.getStudent();

                int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
                List<FeeDate> fdList = feedateService.getByGivenMonth(
                        academicYear.getId(), school.getId(), currentMonth);

                FeeDate fd = null;
                if (fdList != null && !fdList.isEmpty()) {
                    fd = fdList.get(0);
                }

                if (fd != null) {
                    // ✅ Build flat student map — only primitives, no JPA entities
                    Map<String, Object> studentMap = new HashMap<>();
                    studentMap.put("id",              student.getId());
                    studentMap.put("studentName",     student.getStudentName());
                    studentMap.put("fatherName",      student.getFatherName());
                    studentMap.put("motherName",      student.getMotherName());
                    studentMap.put("mobile1",         student.getMobile1());
                    studentMap.put("mobile2",         student.getMobile2());
                    studentMap.put("gender",          student.getGender());
                    studentMap.put("registrationNo",  student.getRegistrationNo());
                    studentMap.put("studentType",     student.getStudentType());
                    studentMap.put("status",          student.getStatus());
                    studentMap.put("address",         student.getAddress());
                    studentMap.put("aadharNo",        student.getAadharNo());

                    // from AcademicStudent
                    studentMap.put("academicId", academicStudent.getId());
                    studentMap.put("classSrNo",       academicStudent.getClassSrNo());
                    studentMap.put("boardSrNo",       academicStudent.getBoardSrNo());
                    studentMap.put("rollNo",          academicStudent.getRollNo());
                    studentMap.put("gradeId", academicStudent.getGrade().getId());
                    studentMap.put("grade",           academicStudent.getGrade() != null
                            ? academicStudent.getGrade().getGradeName() : null);
                    studentMap.put("section",         academicStudent.getSection() != null
                            ? academicStudent.getSection().getSectionName() : null);
                    studentMap.put("medium",          academicStudent.getMedium() != null
                            ? academicStudent.getMedium().getMediumName() : null);

                    // ✅ Build flat feeDate map
                    Map<String, Object> feeDateMap = new HashMap<>();
                    feeDateMap.put("id",          fd.getId());
                    feeDateMap.put("feeDate",     fd.getFeeSubmissiondate());

                    // ✅ Build flat academicYear map
                    Map<String, Object> academicYearMap = new HashMap<>();
                    academicYearMap.put("id",            academicYear.getId());
                    academicYearMap.put("sessionFormat", academicYear.getSessionFormat());
                    academicYearMap.put("startDate",     academicYear.getStartDate());
                    academicYearMap.put("endDate",       academicYear.getEndDate());

                    // student type — old or new
                    String countStu;
                    if (academicStudentService.countNoOfYearsOfStudent(academicStudent) > 1) {
                        countStu = "OLD";
                    } else {
                        countStu = student.getStudentType().equalsIgnoreCase("old") ? "OLD" : "NEW";
                    }

                    // previous balance
                    BigDecimal previousBalance = BigDecimal.ZERO;
                    FeeSubmission feeSubmission = feeSubmissionService
                            .getLastFeeSubmissionOfStudentForBalance(
                                    school.getId(), academicYear.getId(), academicStudent.getId());
                    if (feeSubmission != null) {
                        previousBalance = feeSubmission.getFeeSubmissionBalance().getBalanceAmount();
                    } else {
                        // No fee submitted yet this year — fall back to opening balance
                        // (set via Excel upload for students carrying dues from a previous year/system)
                        previousBalance = academicStudent.getOpeningBalance() != null
                                ? academicStudent.getOpeningBalance() : BigDecimal.ZERO;
                    }

                    result.put("student",         studentMap);      // ✅ flat map, not entity
                    result.put("feeDate",         feeDateMap);      // ✅ flat map, not entity
                    result.put("academicYear",    academicYearMap); // ✅ flat map, not entity
                    result.put("todayDate",       new Date());
                    result.put("countStu",        countStu);
                    result.put("previousBalance", previousBalance);

                } else {
                    result.put("noFeeDate", "No Fee-Date found, Please add fee-date first.");
                }

                // paid months
                Map paidMonths = feeSubmissionService.getPaidMonths(
                        school.getId(), academicYear.getId(), academicStudent.getId());
                if (paidMonths != null && !paidMonths.isEmpty()) {
                    if (paidMonths.containsKey("MonthError")) {
                        result.put("Paid_Month_Error", paidMonths.get("MonthError"));
                    } else {
                        result.put("PaidMonths", paidMonths.get("paidMonths"));
                    }
                } else {
                    result.put("Paid_Month_Error", "No fee submission data found.");
                }

            } else {
                result.put("noAcademicStudent", "Student not found.");  // ✅ fixed NPE — was calling .getStudent() on null
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Error: " + e.getLocalizedMessage());
        }

        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/getStudentDetailsForSibling/{id}")
    public ResponseEntity<?> getStudentDetailsForSibling(@PathVariable("id") Long id, Model model){
        log.info("Inside getStudentDetailsForSibling");
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                List<AcademicStudent> allSiblingsList = academicStudentService.searchSiblings(academicYear.getId(), academicStudent);
                if(allSiblingsList!=null && !allSiblingsList.isEmpty()){
                    List<Map<String, Object>> leanSiblings = new java.util.ArrayList<>();
                    for (AcademicStudent sib : allSiblingsList) {
                        leanSiblings.add(studentService.toLeanAcademicStudentMap(sib));
                    }
                    result.put("siblingList", leanSiblings);
                    result.put("hasSiblings", !leanSiblings.isEmpty());
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

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/searchStudentIndividual/{id}")
    public ResponseEntity<?> getStudentDetail(@PathVariable("id")Long id, Model model){
        log.info("Inside getStudentDetail");
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                result.put("academicStudent", studentService.toLeanAcademicStudentMap(academicStudent));
            } else{
                result.put("noAcademicStudent", "Student not found.");
            }
        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "STUDENT_DISCOUNT_ASSIGN", type = AccessType.VIEW)
    @GetMapping("/getStudentDetailForDiscount/{id}")
    public ResponseEntity<?> getStudentDetailForDiscount(@PathVariable("id") Long id, Model model){
        log.info("Inside getStudentDetailForDiscount");
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                result.put("student", studentService.toLeanAcademicStudentMap(academicStudent));
                //collect discount details if any?
                StudentDiscount studentDiscount = studentDiscountService.getStudentDiscountForStudent(school.getId(), academicYear.getId(), id).orElse(null);
                if(studentDiscount!=null){
                    Map<String, Object> discountMap = new HashMap<>();
                    discountMap.put("id", studentDiscount.getId());
                    discountMap.put("status", studentDiscount.getStatus() != null ? studentDiscount.getStatus() : "");
                    if (studentDiscount.getDiscounthead() != null) {
                        discountMap.put("discounthead", Map.of(
                            "id", studentDiscount.getDiscounthead().getId(),
                            "discountName", studentDiscount.getDiscounthead().getDiscountName() != null ? studentDiscount.getDiscounthead().getDiscountName() : ""
                        ));
                    }
                    result.put("assignedDiscount", discountMap);
                }
            } else{
                result.put("noAcademicStudent", "Student not found.");
            }

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.EDIT)
    @PostMapping("/updateContact")
    public ResponseEntity<?> updateContact(@RequestBody Map<String, String> requestBody){
        log.info("Inside updateContact");
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


    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @PostMapping("/getFeeDetailsBasedOnMonth")
    public ResponseEntity<?> getFeeDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFeeDetailsBasedOnMonth");
        log.debug("requestBody={}", requestBody);
        Map result = new HashMap<>();
        try{
            //{checkBoxes=July, gradeId=2, academicStudentId=5}
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map serviceResultMap = feeSubmissionService.getFeeDetailsBasedOnMonth(school.getId(), academicYear.getId(), academicStuId, requestBody.get("checkBoxes"), gradeId);
                log.debug("serviceResultMap keys={}", serviceResultMap != null ? serviceResultMap.keySet() : null);
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
    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @PostMapping("/getDiscountDetailsBasedOnMonth")
    public ResponseEntity<?> getDiscountDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getDiscountDetailsBasedOnMonth");
        log.debug("requestBody={}", requestBody);
        Map result = new HashMap<>();
        try{
            if(requestBody!=null){
                Long academicStuId = requestBody.get("academicStudentId")!=null?Long.parseLong(requestBody.get("academicStudentId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map serviceResultMap = feeSubmissionService.getDiscountDetailsBasedOnMonth(school.getId(), academicYear.getId(), academicStuId, requestBody.get("checkBoxes"), gradeId);
                log.debug("serviceResultMap keys={}", serviceResultMap != null ? serviceResultMap.keySet() : null);
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

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @PostMapping("/getFineDetailsBasedOnMonth")
    public ResponseEntity<?> getFineDetailsBasedOnMonth(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFineDetailsBasedOnMonth");
        log.debug("requestBody={}", requestBody);
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

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @PostMapping("/getFineDetailsBasedOnMonth_Old_Request")
    public ResponseEntity<?> getFineDetailsBasedOnMonth_Old(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFineDetailsBasedOnMonth_Old");
        log.debug("requestBody={}", requestBody);
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


    @CheckAccess(screen = "FEE_PENDING_SUMMARY_REPORT", type = AccessType.VIEW)
    @PostMapping("/getPendingFeeSummaryData")
    public ResponseEntity<?> getPendingFeeSummaryData(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getPendingFeeSummaryData");
        Map<String, Object> result = new HashMap<>();
        try {
            if (requestBody != null) {
                School school = (School) model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                result = feeSubmissionService.calculatePendingFeeSummary(requestBody, school, academicYear);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Failed to generate report: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "FEE_REMINDER", type = AccessType.VIEW)
    @PostMapping("/getFeeReminderDetails")
    public ResponseEntity<?> getFeeReminderDetails(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFeeReminderDetails");
        Map result = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                result = feeSubmissionService.calculateFeeReminder(requestBody, school, academicYear);
                log.debug("getFeeReminderDetails result keys={}", result.keySet());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/getStudentFeeDetails/{id}")
    public ResponseEntity<?> getStudentFeeDetails(@PathVariable("id") Long id, Model model){
        log.info("Inside getStudentFeeDetails");
        Map result = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        AcademicStudent academicStudent = academicStudentService.searchStudentById(id, academicYear.getId(), school.getId());
        try{
            if(academicStudent!=null){
                result.put("student", studentService.toLeanAcademicStudentMap(academicStudent));
                //collect student fee details if any?
                List<FeeSubmission> feeSubmissionList = feeSubmissionService.getAllFeeSubmissionByAcademicStudent(id);
                if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                    List<Map<String, Object>> leanFees = new java.util.ArrayList<>();
                    for (FeeSubmission fs : feeSubmissionList) {
                        Map<String, Object> fsMap = new HashMap<>();
                        fsMap.put("id", fs.getId());
                        fsMap.put("feeSubmissionDate", fs.getFeeSubmissionDate());
                        fsMap.put("receiptNo", fs.getReceiptNo() != null ? fs.getReceiptNo() : "");
                        fsMap.put("totalAmount", fs.getTotalAmount());
                        fsMap.put("paidAmount", fs.getPaidAmount());
                        fsMap.put("balanceAmount", fs.getBalanceAmount());
                        fsMap.put("paymentType", fs.getPaymentType() != null ? fs.getPaymentType() : "");
                        fsMap.put("status", fs.getStatus() != null ? fs.getStatus() : "");
                        leanFees.add(fsMap);
                    }
                    result.put("feeSubmissions", leanFees);
                }
                else{
                    result.put("feeSubmissionError", "Fees not found for: "+academicStudent.getStudent().getStudentName()+"!");
                }
            } else{
                result.put("studentError", "Student not found.");
            }

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", "Error: "+e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/student-receipt-print/{id}")
    public ResponseEntity<?> getFeeReceipt(@PathVariable("id")Long id, Model model){
        log.info("Inside getFeeReceipt");
        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        Map<String, Object> receiptData = feeSubmissionService.getFeeReceiptData(id, school, academicYear);
        if(receiptData==null || receiptData.isEmpty()){
            receiptData.put("error","Unable to print");
        }
        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/searchReceiptForFeePage/{query}")
    public ResponseEntity<?> searchReceiptForFeePage(@PathVariable("query") String query, Model model){
        log.info("Inside searchReceiptForFeePage");
        Map<String, Object> receiptData = new HashMap<>();
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        FeeSubmission feeSubmission = feeSubmissionService.getFeeDetailsForReceipt(query, school, academicYear);
        if(feeSubmission!=null){
            Map<String, Object> fsMap = new HashMap<>();
            fsMap.put("id", feeSubmission.getId());
            fsMap.put("receiptNo", feeSubmission.getReceiptNo() != null ? feeSubmission.getReceiptNo() : "");
            fsMap.put("totalAmount", feeSubmission.getTotalAmount());
            fsMap.put("paidAmount", feeSubmission.getPaidAmount());
            fsMap.put("balanceAmount", feeSubmission.getBalanceAmount());
            fsMap.put("fineAmount", feeSubmission.getFineAmount());
            fsMap.put("discountAmount", feeSubmission.getDiscountAmount());
            fsMap.put("feeSubmissionDate", feeSubmission.getFeeSubmissionDate());
            fsMap.put("paymentType", feeSubmission.getPaymentType() != null ? feeSubmission.getPaymentType() : "");
            fsMap.put("status", feeSubmission.getStatus() != null ? feeSubmission.getStatus() : "");
            if (feeSubmission.getAcademicStudent() != null) {
                fsMap.put("academicStudent", studentService.toLeanAcademicStudentMap(feeSubmission.getAcademicStudent()));
            }
            receiptData.put("feeSubmission", fsMap);
        } else{
            receiptData.put("error","Fee detail not found.");
        }
        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "FEE_REPORT_USER_WISE", type = AccessType.VIEW)
    @PostMapping("/getFeeCollectionDetailsUserwise")
    public ResponseEntity<?> getFeeCollectionDetailsUserwise(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFeeCollectionDetailsUserwise");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateFeeSubmissionUserWise(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "FEE_REPORT_CANCELLED", type = AccessType.VIEW)
    @PostMapping("/getFeeCancelledDetails")
    public ResponseEntity<?> getFeeCancelledDetails(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFeeCancelledDetails");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
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

    @CheckAccess(screen = "FEE_REPORT_TOTAL_SUBMITTED", type = AccessType.VIEW)
    @PostMapping("/getTotalFeeSubmittedDetails")
    public ResponseEntity<?> getTotalFeeSubmittedDetails(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getTotalFeeSubmittedDetails");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
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

    @CheckAccess(screen = "FEE_REPORT_GRADE_WISE", type = AccessType.VIEW)
    @PostMapping("/getTotalFeeSubmittedDetailsForGrades")
    public ResponseEntity<?> getTotalFeeSubmittedDetailsForGrades(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getTotalFeeSubmittedDetailsForGrades");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateTotalSubmittedFeesGradeWise(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "FEE_REPORT_DEPOSITED", type = AccessType.VIEW)
    @PostMapping("/getTotalDepositedFeeSubmittedDetailsForGrades")
    public ResponseEntity<?> getTotalDepositedFeeSubmittedDetailsForGrades(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getTotalDepositedFeeSubmittedDetailsForGrades");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.getSubmittedFeeDetailForGrade(school, academicYear, requestBody);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }


    @CheckAccess(screen = "FEE_CANCEL", type = AccessType.DELETE)
    @PostMapping("/cancelFeeForStudent")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    public ResponseEntity<?> cancelFeeForStudent(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside cancelFeeForStudent");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.cancelSubmittedFeeForStudent(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "FEE_REPORT_HEAD_WISE", type = AccessType.VIEW)
    @PostMapping("/getFeeCollectionDetailsHeadwise")
    public ResponseEntity<?> getFeeCollectionDetailsHeadwise(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getFeeCollectionDetailsHeadwise");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = feeSubmissionService.calculateFeeSubmissionHeadWise(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

}
