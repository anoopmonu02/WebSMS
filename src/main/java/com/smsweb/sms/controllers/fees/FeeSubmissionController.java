package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.MonthmappingService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.reports.FeeReceiptService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.universal.GradeService;
import com.smsweb.sms.services.universal.MediumService;
import com.smsweb.sms.services.universal.SectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
@Controller
@RequestMapping("/fees")
public class FeeSubmissionController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(FeeSubmissionController.class);


    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final MonthmappingService mmService;
    private final FeeSubmissionService feeSubmissionService;
    private final FeeReceiptService receiptService;
    private final GradeService gradeService;
    private final SectionService sectionService;
    private final MediumService mediumService;
    private final AcademicyearService academicyearService;

    @Autowired
    public FeeSubmissionController(StudentService studentService, MonthmappingService mmService, FeeSubmissionService feeSubmissionService, AcademicStudentService academicStudentService, FeeReceiptService receiptService,
                                   GradeService gradeService, SectionService sectionService, MediumService mediumService, AcademicyearService academicyearService){
        this.studentService = studentService;
        this.mmService = mmService;
        this.feeSubmissionService = feeSubmissionService;
        this.academicStudentService = academicStudentService;
        this.receiptService = receiptService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.mediumService = mediumService;
        this.academicyearService = academicyearService;
    }

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/fee-submit-form")
    public String getFeeSubmissionForm(Model model){
        log.info("Inside getFeeSubmissionForm");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
        log.debug("getFeeSubmissionForm - monthMappingList size={}", monthMappingList.size());
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("feesubmissionobj", new FeeSubmission());
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        // Mid Year Migration Discount field - one check at page load (system_config toggle +
        // ROLE_ADMIN/ROLE_SUPERADMIN), not tied to any specific student. See
        // FeeSubmissionService.isMigrationDiscountFieldEnabledForCurrentUser for the full rule.
        model.addAttribute("migrationDiscountEnabled", feeSubmissionService.isMigrationDiscountFieldEnabledForCurrentUser());
        return "fees/feesubmitform";
    }

    @CheckAccess(screen = "FEE_SUBMIT", type = AccessType.CREATE)
    @PostMapping("/feesubmit")
    public String saveFeeSubmission(HttpServletRequest request, RedirectAttributes redirectAttributes, Model model){
        log.info("Inside saveFeeSubmission");
        try{
            Map paramMap = request.getParameterMap();
            log.debug("saveFeeSubmission request params: {}", paramMap.keySet());
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            Map responseMap = feeSubmissionService.save(paramMap, school, academicYear);
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

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/receipt")
    public String getFeeReceiptPage(Model model){
        log.info("Inside getFeeReceiptPage");
        /*try{
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
        }*/
        return "fees/fee-receipt";
    }

    @CheckAccess(screen = "FEE_RECEIPT_PRINT", type = AccessType.VIEW)
    @GetMapping("/receipt-print/{id}")
    public String getFeeReceipt(@PathVariable("id")Long id, Model model){
        log.info("Inside getFeeReceipt");
        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        Map<String, Object> receiptData = feeSubmissionService.getFeeReceiptData(id, school, academicYear);
        model.addAllAttributes(receiptData);

        return "fees/receipt";
    }

    @CheckAccess(screen = "FEE_REMINDER", type = AccessType.VIEW)
    @GetMapping("fee-reminder")
    public String reminderpage(Model model){
        log.info("Inside reminderpage");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        model.addAttribute("grades",gradeService.getAllGrades());
        model.addAttribute("sections",sectionService.getAllSections());
        model.addAttribute("mediums", mediumService.getAllMediums());
        return "fees/feereminder";
    }
    @CheckAccess(screen = "FEE_CANCEL", type = AccessType.VIEW)
    @GetMapping("/fee-cancel")
    public String cancelFeePage(Model model){
        log.info("Inside cancelFeePage");
        return "fees/feecancel";

    }

    @CheckAccess(screen = "FEE_REPORT_USER_WISE", type = AccessType.VIEW)
    @GetMapping("/fees-user-wise-collection")
    public String userwiseCollection(Model model){
        log.info("Inside userwiseCollection");
        model.addAttribute("monthOrder", new LinkedHashMap<String, Integer>());
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            model.addAttribute("isAdmin", isAdmin);

            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
            Map<String, Integer> monthOrder = new LinkedHashMap<>();
            for (MonthMapping mm : monthMappingList) {
                monthOrder.put(mm.getMonthMaster().getMonthName(), mm.getPriority());
            }
            model.addAttribute("monthOrder", monthOrder);
        }catch(Exception e){
            e.printStackTrace();        }

        return "fees/fees_user_collection";
    }

    @CheckAccess(screen = "FEE_REPORT_OWN_COLLECTION", type = AccessType.VIEW)
    @GetMapping("/fees-users_own-collection")
    public String ownCollection(Model model){
        log.info("Inside ownCollection");
        model.addAttribute("monthOrder", new LinkedHashMap<String, Integer>());
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            model.addAttribute("isAdmin", isAdmin);

            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
            Map<String, Integer> monthOrder = new LinkedHashMap<>();
            for (MonthMapping mm : monthMappingList) {
                monthOrder.put(mm.getMonthMaster().getMonthName(), mm.getPriority());
            }
            model.addAttribute("monthOrder", monthOrder);
        }catch(Exception e){
            e.printStackTrace();        }

        return "fees/fees_own_collection";
    }

    @CheckAccess(screen = "FEE_REPORT_HEAD_WISE", type = AccessType.VIEW)
    @GetMapping("/fees-head-wise-collection-summary")
    public String headwiseCollectionSummary(Model model){
        log.info("Inside headwiseCollectionSummary");
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            model.addAttribute("isAdmin", isAdmin);
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
            model.addAttribute("monthmapping", monthMappingList);
            model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
            model.addAttribute("grades",gradeService.getAllGrades());
            model.addAttribute("sections",sectionService.getAllSections());
            model.addAttribute("mediums", mediumService.getAllMediums());
        }catch(Exception e){
            e.printStackTrace();        }

        return "fees/fees_head_wise_collection";
    }


    @CheckAccess(screen = "FEE_REPORT_CANCELLED", type = AccessType.VIEW)
    @GetMapping("/fees-drop-off-collection")
    public String feesCancellation(Model model){
        log.info("Inside feesCancellation");
        model.addAttribute("page", "datatable");
        return "fees/fees_cancelled";
    }


    @CheckAccess(screen = "FEE_REPORT_TOTAL_SUBMITTED", type = AccessType.VIEW)
    @GetMapping("/fees-submitted-total-detail")
    public String totalFeeSubmissionDetail(Model model){
        log.info("Inside totalFeeSubmissionDetail");
        model.addAttribute("mediums", mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/total_fee_submitted_details";
    }

    @CheckAccess(screen = "FEE_REPORT_GRADE_WISE", type = AccessType.VIEW)
    @GetMapping("fees-submitted-total-detail-grade-wise")
    public String totalFeeSubmittedGradeWise(Model model){
        log.info("Inside totalFeeSubmittedGradeWise");
        model.addAttribute("grades",gradeService.getAllGrades());
        model.addAttribute("sections",sectionService.getAllSections());
        model.addAttribute("mediums", mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/total_fee_submitted_details_grade";
    }

    @CheckAccess(screen = "FEE_PENDING_SUMMARY_REPORT", type = AccessType.VIEW)
    @GetMapping("fees-pending-summary-report")
    public String feePendingSummaryReport(Model model){
        log.info("Inside feePendingSummaryReport");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        // Only show grades/sections that have actual enrolled students in the current academic year
        model.addAttribute("grades",   academicStudentService.findEnrolledGrades(school.getId(), academicYear.getId()));
        model.addAttribute("sections", academicStudentService.findEnrolledSections(school.getId(), academicYear.getId()));
        model.addAttribute("mediums",  mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/pending-fee-summary-report";
    }

    @CheckAccess(screen = "FEE_REPORT_PENDING", type = AccessType.VIEW)
    @GetMapping("fees-pending-total-report")
    public String totalFeePending(Model model){
        log.info("Inside totalFeePending");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        model.addAttribute("grades",gradeService.getAllGrades());
        model.addAttribute("sections",sectionService.getAllSections());
        model.addAttribute("mediums", mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/pending-fee-report";
    }

    @CheckAccess(screen = "FEE_REPORT_DEPOSITED", type = AccessType.VIEW)
    @GetMapping("fees-total-deposited-report")
    public String totalDepositedFee(Model model){
        log.info("Inside totalDepositedFee");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<AcademicYear> academicYears = academicyearService.getAllAcademiyears(school.getId());
        model.addAttribute("academicYears", academicYears);
        model.addAttribute("grades",gradeService.getAllGrades());
        model.addAttribute("sections",sectionService.getAllSections());
        model.addAttribute("mediums", mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/total_deposited_fees";
    }

    @CheckAccess(screen = "FEE_REPORT_PENDING", type = AccessType.VIEW)
    @GetMapping("fees-pending-report")
    public String feePendingReport(Model model){
        log.info("Inside feePendingReport");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List<MonthMapping> monthMappingList = mmService.getAllMonthMapping(academicYear.getId(), school.getId());
        model.addAttribute("monthmapping", monthMappingList);
        model.addAttribute("hasMonthMapping", !monthMappingList.isEmpty());
        model.addAttribute("grades",gradeService.getAllGrades());
        model.addAttribute("sections",sectionService.getAllSections());
        model.addAttribute("mediums", mediumService.getAllMediums());
        model.addAttribute("page", "datatable");
        return "fees/pending-fee-report";
    }

    @CheckAccess(screen = "FEE_REPORT_GRADEWISE_INCOME", type = AccessType.VIEW)
    @GetMapping("/fees-total-gradewise-income-report")
    public String gradeWiseFeeIncomeDetail(Model model){
        log.info("Inside gradeWiseFeeIncomeDetail");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        List dataMap = feeSubmissionService.calculateTotalGradewiseFees(school.getId(), academicYear.getId());

        long totalStudentsCount = 0L;
        long totalStudentsDiscountCount = 0L;
        BigDecimal totalDiscountFees = BigDecimal.ZERO;
        BigDecimal totalFeesCollected = BigDecimal.ZERO;

        if(dataMap!=null && !dataMap.isEmpty()){
            for (Object o : dataMap) {
                List data = (List) o;
                totalStudentsCount += (long) data.get(2);
                totalStudentsDiscountCount += (long) data.get(4);
                totalDiscountFees = totalDiscountFees.add((BigDecimal) data.get(5));
                totalFeesCollected = totalFeesCollected.add((BigDecimal) data.get(6));
            }
        }
        model.addAttribute("totalStudentsCount", totalStudentsCount);
        model.addAttribute("totalStudentsDiscountCount", totalStudentsDiscountCount);
        model.addAttribute("totalDiscountFees", totalDiscountFees);
        model.addAttribute("totalFeesCollected", totalFeesCollected);

        model.addAttribute("hasData", !dataMap.isEmpty());
        model.addAttribute("page", "datatable");
        model.addAttribute("datalist", dataMap);
        return "fees/fees-total-gradewise-income";
    }
}
