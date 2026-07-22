package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.fees.FeeSubmissionSub;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.mobile.MobileAcademicYearService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fee endpoints for the student mobile app.
 *
 * GET /api/v1/fees/submissions[?academicStudentId=..]
 * GET /api/v1/fees/summary[?academicStudentId=..]
 * GET /api/v1/fees/receipt/{id}
 * GET /api/v1/fees/monthly-table[?academicStudentId=..]
 */
@RestController
@RequestMapping("/api/v1/fees")
public class MobileFeesController {
    private static final Logger log = LoggerFactory.getLogger(MobileFeesController.class);

    private final FeeSubmissionService feeSubmissionService;       // existing, unchanged
    private final MobileAcademicYearService academicYearService;  // new, mobile-only

    public MobileFeesController(FeeSubmissionService feeSubmissionService,
                                 MobileAcademicYearService academicYearService) {
        this.feeSubmissionService = feeSubmissionService;
        this.academicYearService = academicYearService;
    }

    // ── GET /api/v1/fees/submissions ─────────────────────────────────────────

    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSubmissions(
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getSubmissions");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        AcademicStudent resolved = target.get();

        List<FeeSubmission> submissions =
                feeSubmissionService.getActiveFeeSubmissionsForYear(
                        resolved.getSchool().getId(),
                        resolved.getAcademicYear().getId(),
                        resolved.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (FeeSubmission fs : submissions) {
            result.add(toSubmissionMap(fs));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /api/v1/fees/summary ─────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getSummary");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        AcademicStudent resolved = target.get();

        List<FeeSubmission> submissions =
                feeSubmissionService.getActiveFeeSubmissionsForYear(
                        resolved.getSchool().getId(),
                        resolved.getAcademicYear().getId(),
                        resolved.getId());

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

    // ── GET /api/v1/fees/monthly-table ───────────────────────────────────────
    // Month name, receipt#, submission date, expected amount and PAID/"-"
    // status for every month of the academic year — for the Fees > Summary
    // tab's month-wise table. Read-only.

    @GetMapping("/monthly-table")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMonthlyTable(
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getMonthlyTable");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        AcademicStudent resolved = target.get();

        if (resolved.getSchool() == null || resolved.getAcademicYear() == null || resolved.getGrade() == null) {
            log.warn("getMonthlyTable: academicStudentId={} missing school/academicYear/grade — returning empty table",
                    resolved.getId());
            return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
        }

        try {
            List<Map<String, Object>> table = feeSubmissionService.getMonthlyFeeTable(
                    resolved.getSchool().getId(),
                    resolved.getAcademicYear().getId(),
                    resolved.getId(),
                    resolved.getGrade().getId());

            return ResponseEntity.ok(ApiResponse.success(table));
        } catch (Exception e) {
            // Fee-structure config (fee_class_map / fee_month_map) may be incomplete
            // for this grade/year — don't take down the whole Summary tab over it.
            log.error("getMonthlyTable failed for academicStudentId={}", resolved.getId(), e);
            return ResponseEntity.ok(ApiResponse.success(new ArrayList<>()));
        }
    }

    // ── GET /api/v1/fees/receipt/{id} ─────────────────────────────────────────
    // Unchanged from the previous file — receipt ids are globally unique and
    // already carry their own ownership check.

    @GetMapping("/receipt/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReceipt(
            @PathVariable Long id,
            HttpServletRequest request) {
        log.info("Inside getReceipt");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<FeeSubmission> optFs = feeSubmissionService.getFeeSubmissionById(id);
        if (optFs.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Receipt not found"));
        }

        FeeSubmission fs = optFs.get();

        if (!fs.getAcademicStudent().getId().equals(academicStudentId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }

        return ResponseEntity.ok(ApiResponse.success(toSubmissionMap(fs)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

        List<String> months = new ArrayList<>();
        if (fs.getFeeSubmissionMonths() != null) {
            for (FeeSubmissionMonths m : fs.getFeeSubmissionMonths()) {
                if (m.getMonthMaster() != null) {
                    months.add(m.getMonthMaster().getMonthName());
                }
            }
        }
        if (fs.getSchool() != null && fs.getAcademicYear() != null) {
            months = feeSubmissionService.sortMonthsByPriority(
                    fs.getSchool().getId(), fs.getAcademicYear().getId(), months);
        }
        entry.put("monthsCovered", months);

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
