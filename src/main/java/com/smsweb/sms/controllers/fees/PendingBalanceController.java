package com.smsweb.sms.controllers.fees;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.fees.PendingBalanceService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pending Balance Collection — completely separate from FeeSubmissionController.
 *
 * GET  /fees/collect-balance                      → form page
 * GET  /fees/searchStudentForPendingBalance/{q}   → AJAX student search (infinite scroll)
 * GET  /fees/getPendingBalanceData/{id}           → AJAX: student detail + balance + history
 * POST /fees/collect-balance-save                 → save balance payment, redirect to receipt
 * GET  /fees/collect-balance-receipt/{id}         → receipt page
 */
@Controller
@RequestMapping("/fees")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class PendingBalanceController extends BaseController {

    private final PendingBalanceService    pendingBalanceService;
    private final AcademicStudentService   academicStudentService;
    private final StudentService           studentService;

    public PendingBalanceController(PendingBalanceService pendingBalanceService,
                                    AcademicStudentService academicStudentService,
                                    StudentService studentService) {
        this.pendingBalanceService  = pendingBalanceService;
        this.academicStudentService = academicStudentService;
        this.studentService         = studentService;
    }

    // ── Page ─────────────────────────────────────────────────────────────────

    @CheckAccess(screen = "PENDING_BALANCE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/collect-balance")
    public String collectBalancePage(Model model) {
        return "fees/pendingbalanceform";
    }

    // ── AJAX: student search (infinite scroll — mirrors FeeSubmissionRestController) ──

    @CheckAccess(screen = "PENDING_BALANCE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/searchStudentForPendingBalance/{query}")
    @ResponseBody
    public ResponseEntity<?> searchStudentForPendingBalance(
            @PathVariable("query") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        School      school      = (School)      model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        List<AcademicStudent> raw = academicStudentService.searchStudents(
                query, academicYear.getId(), school.getId(), page);

        List<Map<String, Object>> leanList = new ArrayList<>();
        if (raw != null) {
            for (AcademicStudent as : raw) {
                leanList.add(studentService.toLeanAcademicStudentMap(as));
            }
        }
        return ResponseEntity.ok(leanList);
    }

    // ── AJAX: student detail + pending balance + history ──────────────────────

    @CheckAccess(screen = "PENDING_BALANCE_SUBMIT", type = AccessType.VIEW)
    @Transactional
    @GetMapping("/getPendingBalanceData/{id}")
    @ResponseBody
    public ResponseEntity<?> getPendingBalanceData(
            @PathVariable("id") Long id,
            Model model) {

        School       school       = (School)       model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        // Flat student map (same as fee-submit search result for the 3 cards)
        AcademicStudent as = academicStudentService.searchStudentById(
                id, academicYear.getId(), school.getId());

        if (as == null) {
            return ResponseEntity.ok(Map.of("error", "Student not found."));
        }

        // Flat student map for the 3 info-cards
        Map<String, Object> studentMap = studentService.toLeanAcademicStudentMap(as);

        // Balance + history
        Map<String, Object> balanceData = pendingBalanceService.getStudentPendingBalanceData(
                id, school, academicYear);

        balanceData.put("student", studentMap);
        return ResponseEntity.ok(balanceData);
    }

    // ── POST: save balance payment ─────────────────────────────────────────────

    @CheckAccess(screen = "PENDING_BALANCE_SUBMIT", type = AccessType.CREATE)
    @PostMapping("/collect-balance-save")
    public String saveCollectBalance(HttpServletRequest request,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        School       school       = (School)       model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        Map<String, Object> result = pendingBalanceService.savePendingBalance(
                request.getParameterMap(), school, academicYear);

        if (result.containsKey("error")) {
            redirectAttributes.addFlashAttribute("error", result.get("error").toString());
            return "redirect:/fees/collect-balance";
        }

        Long feeSubmissionId = (Long) result.get("feeSubmissionId");
        redirectAttributes.addFlashAttribute("success",
                "Balance payment saved for " + result.get("studentName") + ".");
        return "redirect:/fees/collect-balance-receipt/" + feeSubmissionId;
    }

    // ── Receipt page ─────────────────────────────────────────────────────────

    @CheckAccess(screen = "PENDING_BALANCE_SUBMIT", type = AccessType.VIEW)
    @GetMapping("/collect-balance-receipt/{id}")
    public String collectBalanceReceipt(@PathVariable("id") Long id, Model model) {
        Map<String, Object> data = pendingBalanceService.getPendingBalanceReceiptData(id);
        model.addAllAttributes(data);
        return "fees/pending-balance-receipt";
    }
}
