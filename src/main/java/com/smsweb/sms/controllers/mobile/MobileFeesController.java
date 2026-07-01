package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.fees.FeeSubmissionSub;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Fee endpoints for the student mobile app.
 *
 * GET /api/v1/fees/submissions   — all active fee submissions (with months + breakdown)
 * GET /api/v1/fees/summary       — pie-chart totals
 * GET /api/v1/fees/receipt/{id}  — single receipt detail
 *
 * Uses FeeSubmissionService (existing service):
 *  - getActiveFeeSubmissionsForYear()  ← new method added to service
 *  - getFeeSubmissionById()            ← already existed in service
 */
@RestController
@RequestMapping("/api/v1/fees")
public class MobileFeesController {
    private static final Logger log = LoggerFactory.getLogger(MobileFeesController.class);


    private final FeeSubmissionService feeSubmissionService;

    public MobileFeesController(FeeSubmissionService feeSubmissionService) {
        this.feeSubmissionService = feeSubmissionService;
    }

    // ── GET /api/v1/fees/submissions ─────────────────────────────────────────

    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSubmissions(
            HttpServletRequest request) {
        log.info("Inside getSubmissions");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        Long academicYearId    = (Long) request.getAttribute("academicYearId");
        Long schoolId          = (Long) request.getAttribute("schoolId");

        // Uses FeeSubmissionService.getActiveFeeSubmissionsForYear() — added to service
        List<FeeSubmission> submissions =
                feeSubmissionService.getActiveFeeSubmissionsForYear(
                        schoolId, academicYearId, academicStudentId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (FeeSubmission fs : submissions) {
            result.add(toSubmissionMap(fs));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /api/v1/fees/summary ─────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            HttpServletRequest request) {
        log.info("Inside getSummary");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        Long academicYearId    = (Long) request.getAttribute("academicYearId");
        Long schoolId          = (Long) request.getAttribute("schoolId");

        List<FeeSubmission> submissions =
                feeSubmissionService.getActiveFeeSubmissionsForYear(
                        schoolId, academicYearId, academicStudentId);

        BigDecimal totalPaid     = BigDecimal.ZERO;
        BigDecimal totalBalance  = BigDecimal.ZERO;
        BigDecimal totalFine     = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (FeeSubmission fs : submissions) {
            totalPaid     = totalPaid    .add(orZero(fs.getPaidAmount()));
            totalBalance  = totalBalance .add(orZero(fs.getBalanceAmount()));
            totalFine     = totalFine    .add(orZero(fs.getFineAmount()));
            totalDiscount = totalDiscount.add(orZero(fs.getDiscountAmount()));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPaid",       totalPaid);
        summary.put("totalBalance",    totalBalance);
        summary.put("totalFine",       totalFine);
        summary.put("totalDiscount",   totalDiscount);
        summary.put("submissionCount", submissions.size());

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ── GET /api/v1/fees/receipt/{id} ─────────────────────────────────────────

    @GetMapping("/receipt/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReceipt(
            @PathVariable Long id,
            HttpServletRequest request) {
        log.info("Inside getReceipt");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        // Uses FeeSubmissionService.getFeeSubmissionById() — already existed in service
        Optional<FeeSubmission> optFs = feeSubmissionService.getFeeSubmissionById(id);
        if (optFs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Receipt not found"));
        }

        FeeSubmission fs = optFs.get();

        // Security: student can only view their own receipts
        if (!fs.getAcademicStudent().getId().equals(academicStudentId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }

        return ResponseEntity.ok(ApiResponse.success(toSubmissionMap(fs)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a full fee submission map including months covered and head breakdown. */
    private Map<String, Object> toSubmissionMap(FeeSubmission fs) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id",             fs.getId());
        entry.put("receiptNo",      fs.getReceiptNo());
        entry.put("paymentDate",    fs.getFeeSubmissionDate());
        entry.put("paymentType",    fs.getPaymentType());
        entry.put("totalAmount",    fs.getTotalAmount());
        entry.put("paidAmount",     fs.getPaidAmount());
        entry.put("balanceAmount",  fs.getBalanceAmount());
        entry.put("fineAmount",     fs.getFineAmount());
        entry.put("fineRemark",     fs.getFineRemark());
        entry.put("discountAmount", fs.getDiscountAmount());
        entry.put("fullPayAmt",     fs.getFullPaymentAmount());
        entry.put("fullPayRemark",  fs.getFullPaymentRemark());
        entry.put("feeRemark",      fs.getFeeRemark());
        entry.put("status",         fs.getStatus());
        entry.put("createdByName",  fs.getCreatedByName());

        // Months covered in this payment
        List<String> months = new ArrayList<>();
        if (fs.getFeeSubmissionMonths() != null) {
            for (FeeSubmissionMonths m : fs.getFeeSubmissionMonths()) {
                if (m.getMonthMaster() != null) {
                    months.add(m.getMonthMaster().getMonthName());
                }
            }
        }
        entry.put("monthsCovered", months);

        // Fee head breakdown
        List<Map<String, Object>> breakdown = new ArrayList<>();
        if (fs.getFeeSubmissionSub() != null) {
            for (FeeSubmissionSub sub : fs.getFeeSubmissionSub()) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("feeHead", sub.getFeehead() != null
                        ? sub.getFeehead().getFeeHeadName() : "N/A");
                h.put("amount",  sub.getAmount());
                breakdown.add(h);
            }
        }
        entry.put("breakdown", breakdown);

        return entry;
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
