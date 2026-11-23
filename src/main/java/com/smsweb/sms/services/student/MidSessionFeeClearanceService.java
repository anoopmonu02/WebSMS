package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.Fine;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.admin.FineService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Read-only fee clearance check for the Mid Session Migration feature.
 *
 * Rule (customer decision, 2026-11-22): before a student is moved to another branch, every
 * month BEFORE the current month must be paid at the current branch, including the late fine
 * on those months, and the student's previous balance must be 0. The current month and later
 * months are not required (advance payments are fine).
 *
 * Fail-closed: if anything needed for the check is missing or throws (month calendar, fee
 * amounts, fine setup, balance), the result says the check could not be completed and the
 * migration is NOT allowed. This is the opposite of the old pending-due calculation, which
 * returned zero on any error.
 *
 * Works for ONE student only (no class-wide loading) and never writes anything. It reuses
 * existing, already-live logic without changing it:
 *  - FeeSubmissionService.getMonthlyFeeTable : month-wise fee (net of discount) and PAID status
 *  - FeeSubmissionService.calculateFine      : the same fine the Fee Submission screen charges
 *  - FeeSubmissionService.getLastFeeSubmissionOfStudentForBalance : same previous-balance source
 *    as the Fee Submission screen and the mobile app (falls back to the opening balance when
 *    the student has no receipt yet).
 *
 * There is only this one strict path: no configuration switch, no override.
 */
@Service
public class MidSessionFeeClearanceService {

    private static final Logger log = LoggerFactory.getLogger(MidSessionFeeClearanceService.class);

    private final FeeSubmissionService feeSubmissionService;
    private final FineService fineService;

    @Autowired
    public MidSessionFeeClearanceService(FeeSubmissionService feeSubmissionService,
                                          FineService fineService) {
        this.feeSubmissionService = feeSubmissionService;
        this.fineService = fineService;
    }

    /** Outcome of one clearance check. */
    public static class Result {
        /** The balance (or the whole check) could not be determined: migration must not proceed. */
        private boolean checkFailed = false;
        /** The month/fee/fine part could not be determined (calendar, amounts, fine setup). */
        private String monthsError = null;
        private String message = "";
        private String confirmText = "";
        private String currentMonth = "";
        private String requiredUpTo = "";
        private String paidUpTo = "";
        private BigDecimal feeDue = BigDecimal.ZERO;
        private BigDecimal fineDue = BigDecimal.ZERO;
        private BigDecimal balance = BigDecimal.ZERO;
        private boolean fineCapped = false;
        private List<Map<String, Object>> months = new ArrayList<>();

        public boolean isCheckFailed() { return checkFailed; }
        public String getMessage() { return message; }
        public String getRequiredUpTo() { return requiredUpTo; }

        public BigDecimal getTotalDue() {
            BigDecimal b = balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO;
            return feeDue.add(fineDue).add(b);
        }

        /** True only when the check completed and nothing is due. */
        public boolean isCleared() {
            return !checkFailed && monthsError == null && getTotalDue().compareTo(BigDecimal.ZERO) <= 0;
        }

        /** The one decision the migration depends on. */
        public boolean canMigrate() {
            return isCleared();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cleared", isCleared());
            m.put("canMigrate", canMigrate());
            m.put("checkFailed", checkFailed || monthsError != null);
            m.put("message", message);
            m.put("confirmText", confirmText);
            m.put("currentMonth", currentMonth);
            m.put("requiredUpTo", requiredUpTo);
            m.put("paidUpTo", paidUpTo);
            m.put("feeDue", feeDue);
            m.put("fineDue", fineDue);
            m.put("balance", balance);
            m.put("totalDue", getTotalDue());
            m.put("fineCapped", fineCapped);
            m.put("months", months);
            return m;
        }
    }

    public Result check(AcademicStudent stu) {
        log.info("Inside MidSessionFeeClearanceService.check - academicStudentId={}", stu != null ? stu.getId() : null);
        Result r = new Result();
        try {
            if (stu == null || stu.getSchool() == null || stu.getAcademicYear() == null || stu.getGrade() == null) {
                return fail(r, "Student's school, session or class is missing, so the fee check could not run.");
            }
            School school = stu.getSchool();
            AcademicYear year = stu.getAcademicYear();

            // 1. Previous balance
            try {
                FeeSubmission last = feeSubmissionService.getLastFeeSubmissionOfStudentForBalance(school.getId(), year.getId(), stu.getId());
                if (last != null) {
                    BigDecimal b = last.getFeeSubmissionBalance() != null ? last.getFeeSubmissionBalance().getBalanceAmount() : null;
                    r.balance = b != null ? b : BigDecimal.ZERO;
                } else {
                    r.balance = stu.getOpeningBalance() != null ? stu.getOpeningBalance() : BigDecimal.ZERO;
                }
            } catch (Exception e) {
                log.error("Balance lookup failed for academicStudentId={}", stu.getId(), e);
                return fail(r, "Previous balance could not be read, so the fee check could not run.");
            }

            // 2. Month-wise fee, paid status and fine
            try {
                buildMonthPart(r, stu, school, year);
            } catch (Exception e) {
                log.error("Month/fee check failed for academicStudentId={}", stu.getId(), e);
                r.monthsError = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage() : "Unexpected error while reading the fee details.";
                r.months = new ArrayList<>();
            }

            composeMessages(r);
            return r;
        } catch (Exception e) {
            log.error("Fee clearance check failed for academicStudentId=" + (stu != null ? stu.getId() : null), e);
            return fail(r, "The fee check could not be completed. Please try again or contact support.");
        }
    }

    private Result fail(Result r, String message) {
        r.checkFailed = true;
        r.message = message;
        return r;
    }

    private void buildMonthPart(Result r, AcademicStudent stu, School school, AcademicYear year) {
        List<Map<String, Object>> table = feeSubmissionService.getMonthlyFeeTable(
                school.getId(), year.getId(), stu.getId(), stu.getGrade().getId());
        if (table == null || table.isEmpty()) {
            throw new IllegalStateException("The fee month calendar is not set up for this school and session.");
        }

        String currentName = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int currentIdx = -1;
        boolean anyFee = false;
        for (int i = 0; i < table.size(); i++) {
            Object nm = table.get(i).get("monthName");
            if (nm != null && currentName.equalsIgnoreCase(nm.toString().trim())) currentIdx = i;
            if (amountOf(table.get(i)).compareTo(BigDecimal.ZERO) > 0) anyFee = true;
        }
        r.currentMonth = currentName;
        if (currentIdx < 0) {
            throw new IllegalStateException("The current month (" + currentName
                    + ") is not in this school's fee month calendar. Please set it up first.");
        }
        if (!anyFee) {
            throw new IllegalStateException("No fee amounts are set up for this student's class, so dues cannot be checked.");
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy");
        dateFmt.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        List<String> unpaidRequired = new ArrayList<>();
        int firstUnpaidIdx = -1;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < table.size(); i++) {
            Map<String, Object> src = table.get(i);
            String monthName = String.valueOf(src.get("monthName"));
            BigDecimal amount = amountOf(src);
            boolean paid = "PAID".equals(String.valueOf(src.get("status")));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("monthName", monthName);
            row.put("fee", amount);
            row.put("fine", BigDecimal.ZERO);
            row.put("receiptNo", src.get("receiptNo"));
            Object sd = src.get("submissionDate");
            row.put("submissionDate", sd instanceof Date ? dateFmt.format((Date) sd) : null);

            String status;
            if (paid) {
                status = "PAID";
            } else if (i < currentIdx) {
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    status = "UNPAID";
                    unpaidRequired.add(monthName);
                    if (firstUnpaidIdx < 0) firstUnpaidIdx = i;
                } else {
                    status = "NO_FEE";
                }
            } else if (i == currentIdx) {
                status = "CURRENT";
            } else {
                status = "UPCOMING";
            }
            row.put("status", status);
            row.put("advance", paid && i > currentIdx);
            rows.add(row);
        }

        if (currentIdx > 0) {
            r.requiredUpTo = String.valueOf(table.get(currentIdx - 1).get("monthName"));
            if (firstUnpaidIdx < 0) {
                r.paidUpTo = r.requiredUpTo;
            } else if (firstUnpaidIdx > 0) {
                r.paidUpTo = String.valueOf(table.get(firstUnpaidIdx - 1).get("monthName"));
            } else {
                r.paidUpTo = "";
            }
        }

        // Fee due and fine for the unpaid required months
        BigDecimal feeDue = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            if ("UNPAID".equals(row.get("status"))) feeDue = feeDue.add((BigDecimal) row.get("fee"));
        }
        r.feeDue = feeDue;

        if (!unpaidRequired.isEmpty()) {
            List<Fine> fines = fineService.getAllFines(school.getId(), year.getId());
            if (fines == null || fines.isEmpty()) {
                throw new IllegalStateException("Fine setup is missing for this school and session, so the fine cannot be calculated.");
            }
            Fine fine = fines.get(0);
            int maxFine = fine.getFineAmount() * fine.getMaxCalculated();
            int totalFine = feeSubmissionService.calculateFine(unpaidRequired, school, year, maxFine, fine);
            int sumOfMonths = 0;
            for (Map<String, Object> row : rows) {
                if ("UNPAID".equals(row.get("status"))) {
                    int one = feeSubmissionService.calculateFine(
                            Collections.singletonList(String.valueOf(row.get("monthName"))), school, year, maxFine, fine);
                    row.put("fine", BigDecimal.valueOf(one));
                    sumOfMonths += one;
                }
            }
            r.fineCapped = sumOfMonths > totalFine;
            r.fineDue = BigDecimal.valueOf(totalFine);
        }
        r.months = rows;
    }

    private BigDecimal amountOf(Map<String, Object> row) {
        Object a = row.get("amount");
        if (a instanceof BigDecimal) return (BigDecimal) a;
        if (a == null) return BigDecimal.ZERO;
        return new BigDecimal(a.toString());
    }

    private void composeMessages(Result r) {
        if (r.monthsError != null) {
            r.message = "Fee clearance could not be verified: " + r.monthsError;
            r.confirmText = "Fee clearance must be verified before migration.";
            return;
        }
        BigDecimal total = r.getTotalDue();
        List<String> unpaidNames = new ArrayList<>();
        for (Map<String, Object> row : r.months) {
            if ("UNPAID".equals(row.get("status"))) unpaidNames.add(String.valueOf(row.get("monthName")));
        }
        boolean hasBalance = r.balance.compareTo(BigDecimal.ZERO) > 0;

        if (r.isCleared()) {
            r.message = r.requiredUpTo.isEmpty()
                    ? "No fee is required before this month. No dues at this branch."
                    : "All fees are cleared up to " + r.requiredUpTo + ". No dues at this branch.";
            r.confirmText = r.requiredUpTo.isEmpty()
                    ? "I confirm there are no fee dues at this branch."
                    : "I confirm all fee dues up to " + r.requiredUpTo + " are cleared at this branch.";
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (!unpaidNames.isEmpty()) {
            sb.append(String.join(", ", unpaidNames)).append(unpaidNames.size() == 1 ? " fee is unpaid" : " fees are unpaid");
            if (r.fineDue.compareTo(BigDecimal.ZERO) > 0) sb.append(" (late fine included)");
        }
        if (hasBalance) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("previous balance of Rs ").append(r.balance.toPlainString()).append(" is pending");
        }
        sb.append(". Total due Rs ").append(total.toPlainString()).append(".");

        r.message = "Migration blocked: " + sb + " Collect it at this branch first, then refresh.";
        r.confirmText = "Fee clearance must be completed before migration.";
    }
}
