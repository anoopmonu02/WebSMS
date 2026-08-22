package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionBalance;
import com.smsweb.sms.models.fees.FeeSubmissionPaymentBreakup;
import com.smsweb.sms.models.fees.ReceiptSequence;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.fees.ReceiptSequenceRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.*;

/**
 * Dedicated service for Pending Balance Payment flow.
 * Completely isolated from FeeSubmissionService — no cross-injection.
 *
 * Writes to:  fee_submission + fee_submission_balance only.
 * Never touches: fee_submission_sub, fee_submission_months.
 *
 * Distinguisher: feeRemark always prefixed with "BALANCE PAYMENT".
 */
@Service
public class PendingBalanceService {

    private static final Logger log = LoggerFactory.getLogger(PendingBalanceService.class);

    private final FeeSubmissionRepository    feeSubmissionRepository;
    private final AcademicStudentRepository  academicStudentRepository;
    private final ReceiptSequenceRepository  receiptSequenceRepository;
    private final UserService                userService;

    public PendingBalanceService(FeeSubmissionRepository feeSubmissionRepository,
                                 AcademicStudentRepository academicStudentRepository,
                                 ReceiptSequenceRepository receiptSequenceRepository,
                                 UserService userService) {
        this.feeSubmissionRepository   = feeSubmissionRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.receiptSequenceRepository = receiptSequenceRepository;
        this.userService               = userService;
    }

    // ── AJAX: student detail + pending balance + history ─────────────────────

    /**
     * Called by AJAX when operator selects a student on the collect-balance form.
     * Returns:
     *   pendingBalance   — BigDecimal from latest Active FeeSubmissionBalance
     *   fineAmount       — BigDecimal from latest Active FeeSubmission (display only)
     *   hasPendingBalance — boolean
     *   todayDate        — formatted string
     *   history          — list of maps (one per Active FeeSubmission, desc order)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStudentPendingBalanceData(Long academicStudentId,
                                                            School school,
                                                            AcademicYear academicYear) {
        log.info("Inside getStudentPendingBalanceData");
        Map<String, Object> result = new HashMap<>();
        try {
            // Latest Active submission (already filtered by status='Active' in repo query)
            List<FeeSubmission> latestList = feeSubmissionRepository
                    .findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(
                            school.getId(), academicStudentId);

            BigDecimal pendingBalance = BigDecimal.ZERO;
            BigDecimal fineAmount     = BigDecimal.ZERO;

            if (!latestList.isEmpty()) {
                FeeSubmission latest = latestList.get(0);
                pendingBalance = latest.getFeeSubmissionBalance() != null
                        ? latest.getFeeSubmissionBalance().getBalanceAmount()
                        : BigDecimal.ZERO;
                fineAmount = latest.getFineAmount() != null
                        ? latest.getFineAmount() : BigDecimal.ZERO;
            }

            result.put("pendingBalance",    pendingBalance);
            result.put("fineAmount",        fineAmount);
            result.put("hasPendingBalance", pendingBalance.compareTo(BigDecimal.ZERO) > 0);

            // Today's date formatted same as feesubmitform
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy HH:mm:ss");
            result.put("todayDate", sdf.format(new Date()));

            // History — all Active submissions for this student, newest first
            List<FeeSubmission> allActive = feeSubmissionRepository
                    .findAllByAcademicStudent_IdAndStatus(academicStudentId, "Active");
            allActive.sort(Comparator.comparing(FeeSubmission::getFeeSubmissionDate).reversed());

            List<Map<String, Object>> historyList = new ArrayList<>();
            for (FeeSubmission fs : allActive) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("submitDate",     sdf.format(fs.getFeeSubmissionDate()));
                row.put("receiptNo",      fs.getReceiptNo()      != null ? fs.getReceiptNo()      : "");
                row.put("totalAmount",    fs.getTotalAmount());
                row.put("fineAmount",     fs.getFineAmount());
                row.put("discountAmount", fs.getDiscountAmount());
                row.put("paidAmount",     fs.getPaidAmount());
                row.put("balanceAmount",  fs.getBalanceAmount());
                row.put("feeRemark",      fs.getFeeRemark()      != null ? fs.getFeeRemark()      : "");
                historyList.add(row);
            }
            result.put("history", historyList);

        } catch (Exception e) {
            log.error("Error fetching pending balance data for academicStudentId={}", academicStudentId, e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ── SAVE ─────────────────────────────────────────────────────────────────

    /**
     * Saves the balance payment.
     * Creates ONE FeeSubmission (no sub/months) + ONE FeeSubmissionBalance.
     *
     * @return map with "feeSubmissionId" on success, or "error" on failure.
     */
    @Transactional
    public Map<String, Object> savePendingBalance(Map<String, String[]> params,
                                                  School school,
                                                  AcademicYear academicYear) {
        log.info("Inside savePendingBalance");
        Map<String, Object> result = new HashMap<>();
        try {
            Long       academicStudentId = Long.parseLong(params.get("academicStudentId")[0].trim());
            BigDecimal submitAmount      = new BigDecimal(params.get("submitAmount")[0].trim());
            BigDecimal totalAmount       = new BigDecimal(params.get("totalAmount")[0].trim());
            String     paymentType       = params.get("paymentType")[0].trim();
            String     remarkRaw         = params.containsKey("feeRemark")
                    ? params.get("feeRemark")[0].trim() : "";
            String     feeRemark         = remarkRaw.isEmpty()
                    ? "BALANCE PAYMENT"
                    : "BALANCE PAYMENT - " + remarkRaw;

            // ── Server-side validation ────────────────────────────────────────
            if (submitAmount.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("error", "Submit amount must be greater than zero.");
                return result;
            }
            if (submitAmount.compareTo(totalAmount) > 0) {
                result.put("error", "Submit amount cannot exceed pending balance ₹" + totalAmount + ".");
                return result;
            }
            if (paymentType.isEmpty()) {
                result.put("error", "Payment type is required.");
                return result;
            }
            // "Both" (part Cash, part Online) - server-side check on top of the breakup
            // popup's own client-side validation, same reasoning/shape as
            // FeeSubmissionService#save's equivalent check: a tampered or stale request
            // must not be able to save a submission whose recorded split doesn't match
            // what was actually collected. Deliberately a local copy rather than calling
            // into FeeSubmissionService - this class is intentionally isolated from it
            // (see class javadoc).
            BigDecimal cashAmountForValidation = null;
            BigDecimal onlineAmountForValidation = null;
            if ("Both".equalsIgnoreCase(paymentType)) {
                cashAmountForValidation = parseAmountParam(params, "cashAmount");
                onlineAmountForValidation = parseAmountParam(params, "onlineAmount");
                if (cashAmountForValidation == null || onlineAmountForValidation == null
                        || cashAmountForValidation.compareTo(BigDecimal.ZERO) <= 0
                        || onlineAmountForValidation.compareTo(BigDecimal.ZERO) <= 0) {
                    result.put("error", "For payment type \"Both\", both Cash amount and Online amount are required and must be greater than zero.");
                    return result;
                }
                if (cashAmountForValidation.add(onlineAmountForValidation).compareTo(submitAmount) != 0) {
                    result.put("error", "Cash amount + Online amount must equal the Submit Amount.");
                    return result;
                }
            }

            // ── Fetch AcademicStudent via latest Active submission ─────────────
            List<FeeSubmission> latest = feeSubmissionRepository
                    .findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(
                            school.getId(), academicStudentId);
            if (latest.isEmpty()) {
                result.put("error", "No active fee record found for this student.");
                return result;
            }
            AcademicStudent academicStudent = latest.get(0).getAcademicStudent();

            BigDecimal balanceAmount = totalAmount.subtract(submitAmount);
            String     schoolCode    = resolveSchoolCode(school.getSchoolName());

            // ── Build FeeSubmission ───────────────────────────────────────────
            FeeSubmission fs = new FeeSubmission();
            fs.setAcademicStudent(academicStudent);
            fs.setAcademicYear(academicYear);
            fs.setSchool(school);
            fs.setFeeSubmissionDate(new Date());  // server time — no user input
            fs.setTotalAmount(totalAmount);
            fs.setPaidAmount(submitAmount);
            fs.setBalanceAmount(balanceAmount);
            fs.setFineAmount(BigDecimal.ZERO);    // no fine collected on balance payment
            fs.setDiscountAmount(BigDecimal.ZERO);
            fs.setFullPaymentAmount(BigDecimal.ZERO);
            fs.setFeeRemark(feeRemark);
            fs.setFineRemark(null);
            fs.setPaymentType(paymentType);
            fs.setReceiptNo(generateReceiptNumber(schoolCode));
            fs.setStatus("Active");
            fs.setPreviousFeeBalanceRemark(totalAmount.toPlainString());
            fs.setFeeSubmissionSub(new ArrayList<>());      // intentionally empty
            fs.setFeeSubmissionMonths(new ArrayList<>());   // intentionally empty
            fs.setPaymentBreakup(buildPaymentBreakupList(fs, paymentType, submitAmount, params, cashAmountForValidation, onlineAmountForValidation));
            fs.setCreatedBy(userService.getLoggedInUser());

            // ── Build FeeSubmissionBalance ────────────────────────────────────
            FeeSubmissionBalance fsb = new FeeSubmissionBalance();
            fsb.setBalanceAmount(balanceAmount);
            fsb.setFeeDate(fs.getFeeSubmissionDate());
            fsb.setStudent(academicStudent.getStudent());
            fsb.setStatus("Active");
            fsb.setFeeSubmission(fs);
            fs.setFeeSubmissionBalance(fsb);

            feeSubmissionRepository.save(fs);
            log.info("Pending balance saved — student={}, paid={}, balance={}, receipt={}",
                    academicStudent.getStudent().getStudentName(), submitAmount, balanceAmount, fs.getReceiptNo());

            result.put("feeSubmissionId", fs.getId());
            result.put("studentName",     academicStudent.getStudent().getStudentName());

        } catch (Exception e) {
            log.error("Error saving pending balance payment", e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ── RECEIPT DATA ─────────────────────────────────────────────────────────

    /**
     * Loads all data needed to render pending-balance-receipt.html.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPendingBalanceReceiptData(Long id) {
        log.info("Inside getPendingBalanceReceiptData");
        Map<String, Object> model = new HashMap<>();
        try {
            Optional<FeeSubmission> opt = feeSubmissionRepository.findById(id);
            if (opt.isEmpty()) {
                model.put("error", "Receipt not found for ID: " + id);
                return model;
            }
            FeeSubmission   fs     = opt.get();
            AcademicStudent as     = fs.getAcademicStudent();
            School          school = as.getSchool();

            // School (flat map — no lazy proxy)
            Map<String, Object> schoolMap = new LinkedHashMap<>();
            schoolMap.put("schoolName", school.getSchoolName() != null ? school.getSchoolName() : "");
            schoolMap.put("address",    school.getAddress()    != null ? school.getAddress()    : "");
            schoolMap.put("mobile1",    school.getMobile1()    != null ? school.getMobile1()    : "");
            schoolMap.put("mobile2",    school.getMobile2()    != null ? school.getMobile2()    : "");
            schoolMap.put("email",      school.getEmail()      != null ? school.getEmail()      : "");
            model.put("school", schoolMap);

            // Student (flat map)
            Map<String, Object> studentMap = new LinkedHashMap<>();
            studentMap.put("studentName", as.getStudent().getStudentName());
            studentMap.put("fatherName",  as.getStudent().getFatherName());
            studentMap.put("motherName",  as.getStudent().getMotherName());
            studentMap.put("mobile1",     as.getStudent().getMobile1() != null ? as.getStudent().getMobile1() : "");
            studentMap.put("classSrNo",   as.getClassSrNo()            != null ? as.getClassSrNo()           : "");
            studentMap.put("grade",       as.getGrade().getGradeName());
            studentMap.put("section",     as.getSection().getSectionName());
            model.put("student", studentMap);

            // FeeSubmission (flat map — mirrors getFeeReceiptData pattern)
            Map<String, Object> fsMap = new LinkedHashMap<>();
            fsMap.put("id",              fs.getId());
            fsMap.put("receiptNo",       fs.getReceiptNo()    != null ? fs.getReceiptNo()    : "");
            fsMap.put("feeSubmissionDate", fs.getFeeSubmissionDate());
            fsMap.put("paymentType",     fs.getPaymentType()  != null ? fs.getPaymentType()  : "");
            fsMap.put("totalAmount",     fs.getTotalAmount());
            fsMap.put("paidAmount",      fs.getPaidAmount());
            fsMap.put("balanceAmount",   fs.getBalanceAmount());
            fsMap.put("feeRemark",       fs.getFeeRemark()    != null ? fs.getFeeRemark()    : "");
            // Cash+Online breakup - same "paymentDisplay" convention as
            // FeeSubmissionService#getFeeReceiptData. Empty/falls back to plain
            // paymentType for historical rows saved before this table existed, and for
            // any Cash-only/Online-only payment.
            List<Map<String, Object>> breakupList = new ArrayList<>();
            if (fs.getPaymentBreakup() != null) {
                for (FeeSubmissionPaymentBreakup breakup : fs.getPaymentBreakup()) {
                    Map<String, Object> breakupRow = new HashMap<>();
                    breakupRow.put("paymentMode", breakup.getPaymentMode());
                    breakupRow.put("amount", breakup.getAmount());
                    breakupList.add(breakupRow);
                }
            }
            fsMap.put("paymentBreakup", breakupList);
            fsMap.put("paymentDisplay", buildPaymentDisplayText(fs, breakupList));
            model.put("feeSubmission", fsMap);

            model.put("academicYear", as.getAcademicYear().getSessionFormat());

        } catch (Exception e) {
            log.error("Error fetching pending balance receipt data for id={}", id, e);
            model.put("error", e.getMessage());
        }
        return model;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Transactional
    protected String generateReceiptNumber(String branchCode) {
        int currentYear = Year.now().getValue();
        ReceiptSequence sequence = receiptSequenceRepository
                .findByBranchCodeAndYear(branchCode, currentYear)
                .orElse(new ReceiptSequence(branchCode, 0, currentYear));
        int next = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(next);
        receiptSequenceRepository.save(sequence);
        return String.format("%s/%d/%d", branchCode, currentYear, next);
    }

    private String resolveSchoolCode(String schoolName) {
        if (schoolName == null || schoolName.isEmpty()) return "";
        String lower = schoolName.toLowerCase();
        if (lower.contains("college")) return "UC";
        if (lower.contains("school"))  return "US";
        if (lower.contains("sansthan")) return "DM";
        return "";
    }

    // ── Payment breakup (Cash/Online/Both) ──────────────────────────────────────
    // Deliberately local copies of the equivalent logic in FeeSubmissionService,
    // not shared/injected - this class stays completely isolated, per the class
    // javadoc at the top of this file.

    private BigDecimal parseAmountParam(Map<String, String[]> params, String paramName) {
        if (params == null || !params.containsKey(paramName)) return null;
        String[] values = params.get(paramName);
        if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) return null;
        try {
            return new BigDecimal(values[0].trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Builds the 1 or 2 FeeSubmissionPaymentBreakup rows for a balance payment - same
     * cardinality rules as the main Fee Submission flow (see
     * FeeSubmissionPaymentBreakup's javadoc). cashAmount/onlineAmount are passed in
     * already-validated (present, >0, summing to submitAmount) rather than re-parsed here.
     */
    private List<FeeSubmissionPaymentBreakup> buildPaymentBreakupList(FeeSubmission fs, String paymentType, BigDecimal submitAmount,
                                                                        Map<String, String[]> params,
                                                                        BigDecimal validatedCashAmount, BigDecimal validatedOnlineAmount) {
        List<FeeSubmissionPaymentBreakup> breakupList = new ArrayList<>();
        if ("Cash".equalsIgnoreCase(paymentType) || "Online".equalsIgnoreCase(paymentType)) {
            FeeSubmissionPaymentBreakup row = new FeeSubmissionPaymentBreakup();
            row.setFeeSubmission(fs);
            row.setPaymentMode(paymentType);
            row.setAmount(submitAmount);
            row.setDescription(fs.getFeeRemark());
            breakupList.add(row);
        } else if ("Both".equalsIgnoreCase(paymentType)) {
            String cashRemark = params.containsKey("cashRemark") ? params.get("cashRemark")[0] : null;
            String onlineRemark = params.containsKey("onlineRemark") ? params.get("onlineRemark")[0] : null;

            FeeSubmissionPaymentBreakup cashRow = new FeeSubmissionPaymentBreakup();
            cashRow.setFeeSubmission(fs);
            cashRow.setPaymentMode("Cash");
            cashRow.setAmount(validatedCashAmount != null ? validatedCashAmount : BigDecimal.ZERO);
            cashRow.setDescription(cashRemark);
            breakupList.add(cashRow);

            FeeSubmissionPaymentBreakup onlineRow = new FeeSubmissionPaymentBreakup();
            onlineRow.setFeeSubmission(fs);
            onlineRow.setPaymentMode("Online");
            onlineRow.setAmount(validatedOnlineAmount != null ? validatedOnlineAmount : BigDecimal.ZERO);
            onlineRow.setDescription(onlineRemark);
            breakupList.add(onlineRow);
        }
        return breakupList;
    }

    /**
     * Ready-to-print text for the receipt's "Payment:" line - same behavior as
     * FeeSubmissionService#buildPaymentDisplayText (local copy, same isolation reasoning
     * as the other helpers above).
     */
    private String buildPaymentDisplayText(FeeSubmission fs, List<Map<String, Object>> breakupList) {
        if (breakupList != null && breakupList.size() == 2) {
            BigDecimal cashAmt = null;
            BigDecimal onlineAmt = null;
            for (Map<String, Object> row : breakupList) {
                Object mode = row.get("paymentMode");
                Object amount = row.get("amount");
                BigDecimal amt = amount instanceof BigDecimal ? (BigDecimal) amount : BigDecimal.ZERO;
                if ("Cash".equalsIgnoreCase(String.valueOf(mode))) {
                    cashAmt = amt;
                } else if ("Online".equalsIgnoreCase(String.valueOf(mode))) {
                    onlineAmt = amt;
                }
            }
            if (cashAmt != null && onlineAmt != null) {
                return "Cash ₹" + cashAmt.setScale(2, java.math.RoundingMode.HALF_UP)
                        + " + Online ₹" + onlineAmt.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        return fs.getPaymentType();
    }
}
