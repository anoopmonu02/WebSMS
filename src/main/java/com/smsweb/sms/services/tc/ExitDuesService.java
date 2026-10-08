package com.smsweb.sms.services.tc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsweb.sms.dto.tc.DuesEstimate;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Estimates what a leaving student owes, using the fee calculator that already drives the Fee
 * Reminder page ({@link FeeSubmissionService#calculateFeeReminder}). Nothing is re-implemented:
 * the total is month fees, previous (or opening) balance and late fine, less any discount (D20).
 *
 * <p>Cut-off (D14): calendar months only. The month of the last date is counted when
 * {@code includeLastMonth} is true; otherwise only the months before it. The fine is worked out
 * as of today, because that is how the fee calculator works.</p>
 *
 * <p>Unlike the migration screen's version, this one never hides a failure as zero: if the fee
 * setup is incomplete it throws {@link DuesCalculationException}, so a student cannot leave
 * owing money just because a fee date or fine setting is missing.</p>
 *
 * <p>The calculator only sees Active students, so this must run while the student is still Active
 * (before strike-off deactivates them). It loads the whole class once; call it once per student,
 * never in a loop.</p>
 */
@Service
public class ExitDuesService {

    private static final Logger log = LoggerFactory.getLogger(ExitDuesService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ASK_ADMIN = " An admin can enter the dues by hand.";

    private final FeeSubmissionService feeSubmissionService;
    private final MonthmappingRepository monthmappingRepository;
    private final FeeSubmissionRepository feeSubmissionRepository;

    public ExitDuesService(FeeSubmissionService feeSubmissionService,
                           MonthmappingRepository monthmappingRepository,
                           FeeSubmissionRepository feeSubmissionRepository) {
        this.feeSubmissionService = feeSubmissionService;
        this.monthmappingRepository = monthmappingRepository;
        this.feeSubmissionRepository = feeSubmissionRepository;
    }

    /**
     * Not transactional on purpose: a {@link DuesCalculationException} must be catchable by the
     * caller (strike-off lets an admin type the amount) without marking the caller's transaction
     * rollback-only. The caller supplies the transaction.
     *
     * @throws DuesCalculationException when the figure cannot be worked out reliably
     */
    public DuesEstimate estimate(AcademicStudent enrollment, LocalDate exitDate, boolean includeLastMonth,
                                 School school, AcademicYear academicYear) {
        log.info("Inside estimate: academicStudentId={}, exitDate={}, includeLastMonth={}",
                enrollment.getId(), exitDate, includeLastMonth);

        List<MonthMapping> sessionMonths = monthmappingRepository
                .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
        if (sessionMonths == null || sessionMonths.isEmpty()) {
            throw new DuesCalculationException("The fee months are not set up for this session." + ASK_ADMIN);
        }

        String lastDateMonth = exitDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int lastDateIndex = -1;
        for (int i = 0; i < sessionMonths.size(); i++) {
            MonthMapping mm = sessionMonths.get(i);
            if (mm.getMonthMaster() != null && mm.getMonthMaster().getMonthName() != null
                    && lastDateMonth.equalsIgnoreCase(mm.getMonthMaster().getMonthName().trim())) {
                lastDateIndex = i;
                break;
            }
        }
        if (lastDateIndex < 0) {
            throw new DuesCalculationException("The last date's month (" + lastDateMonth
                    + ") is not one of this session's fee months." + ASK_ADMIN);
        }

        int cutoffIndex = includeLastMonth ? lastDateIndex : lastDateIndex - 1;
        if (cutoffIndex < 0) {
            return balanceOnly(enrollment, school, academicYear, lastDateMonth, includeLastMonth);
        }

        MonthMapping cutoff = sessionMonths.get(cutoffIndex);
        String cutoffName = cutoff.getMonthMaster().getMonthName().trim();

        Map<String, String> params = new HashMap<>();
        params.put("grade", String.valueOf(enrollment.getGrade().getId()));
        params.put("section", String.valueOf(enrollment.getSection().getId()));
        params.put("medium", String.valueOf(enrollment.getMedium().getId()));
        params.put("month", String.valueOf(cutoff.getMonthMaster().getId()));

        Map<?, ?> result = feeSubmissionService.calculateFeeReminder(params, school, academicYear);
        if (result == null) {
            throw new DuesCalculationException("The fee calculator returned nothing." + ASK_ADMIN);
        }
        failIfCalculatorReportedProblem(result, enrollment.getId());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fine = BigDecimal.ZERO;
        String months = "";
        String heads = "";
        Object finalData = result.get("finalData");
        if (finalData instanceof Map) {
            Object entry = ((Map<?, ?>) finalData).get(enrollment.getId());
            if (entry instanceof Map) {
                Map<?, ?> e = (Map<?, ?>) entry;
                total = toMoney(e.get("amount"));
                fine = toMoney(e.get("fineAmount"));
                months = tidyList(e.get("monthsList"));
                heads = tidyList(e.get("headList"));
            }
            // No entry: the calculator drops students who owe nothing (or have paid ahead), so the dues are 0.
        }
        return build(total, fine, months, heads, cutoffName, includeLastMonth, null);
    }

    /** The last date falls in the first fee month and that month is excluded: only the carried-in balance counts. */
    private DuesEstimate balanceOnly(AcademicStudent enrollment, School school, AcademicYear academicYear,
                                     String lastDateMonth, boolean includeLastMonth) {
        List<Long> ids = List.of(enrollment.getId());
        BigDecimal balance;
        boolean submitted = !feeSubmissionRepository
                .findAcademicStudentIdsWithAnySubmission(school.getId(), academicYear.getId(), ids).isEmpty();
        if (submitted) {
            List<Object[]> rows = feeSubmissionRepository
                    .getLatestBalanceAmountsForStudents(school.getId(), academicYear.getId(), ids);
            balance = (rows == null || rows.isEmpty() || rows.get(0)[1] == null)
                    ? BigDecimal.ZERO : toMoney(rows.get(0)[1]);
        } else {
            balance = enrollment.getOpeningBalance() != null ? enrollment.getOpeningBalance() : BigDecimal.ZERO;
        }
        return build(balance, BigDecimal.ZERO, "", "", lastDateMonth, includeLastMonth,
                "No fee month falls before the last date; only the balance carried in is counted.");
    }

    private void failIfCalculatorReportedProblem(Map<?, ?> result, Long academicStudentId) {
        Object error = result.get("error");
        if (error != null) {
            log.warn("Fee calculator problem for academicStudentId={}: {}", academicStudentId, error);
            String text = String.valueOf(error);
            if (text.contains("Fee date not found")) {
                throw new DuesCalculationException("The fee dates are not set up for this session." + ASK_ADMIN);
            }
            if (text.contains("Fee-class-map")) {
                throw new DuesCalculationException("The fee amounts are not set up for this class." + ASK_ADMIN);
            }
            throw new DuesCalculationException("The fee calculator could not finish. Check the fee date, "
                    + "fee amounts and fine setting for this session." + ASK_ADMIN);
        }
        if (result.get("FEE_CLASS_MAP_NOT_FOUND") != null) {
            throw new DuesCalculationException("The fee amounts are not set up for this class." + ASK_ADMIN);
        }
        if (result.get("STUDENT_NOT_FOUND") != null) {
            throw new DuesCalculationException("The student was not found in the fee calculation." + ASK_ADMIN);
        }
    }

    private DuesEstimate build(BigDecimal total, BigDecimal fine, String months, String heads,
                               String cutoffName, boolean includeLastMonth, String note) {
        BigDecimal amount = total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fineAmount = fine.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("total", amount.toPlainString());
        json.put("fine", fineAmount.toPlainString());
        json.put("feesAndBalance", amount.subtract(fineAmount).max(BigDecimal.ZERO).toPlainString());
        json.put("months", months);
        json.put("heads", heads);
        json.put("cutoffMonth", cutoffName);
        json.put("includeLastMonth", includeLastMonth);
        if (note != null) {
            json.put("note", note);
        }
        String breakdown;
        try {
            breakdown = MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            // Plain strings and booleans only; this cannot really happen.
            throw new IllegalStateException("Could not write the dues breakdown", e);
        }
        return new DuesEstimate(amount, fineAmount, months, heads, cutoffName, includeLastMonth, breakdown);
    }

    private static BigDecimal toMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new DuesCalculationException("The fee calculator returned an amount that could not be read." + ASK_ADMIN);
        }
    }

    /** The calculator builds "April, May, " with a trailing separator; trim it for display. */
    private static String tidyList(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        while (text.endsWith(",")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }
}
