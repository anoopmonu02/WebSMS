package com.smsweb.sms.services.tc;

import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only fee facts about an exit in its own session: which months the Fee Submission screen may
 * collect (session start up to the cut-off month, the same calendar rule as the dues estimate), which
 * months are already paid, and the balance carried in. Nothing here writes or changes fee data.
 */
@Service
public class ExitFeeMonthsService {

    private final MonthmappingRepository monthmappingRepository;
    private final FeeSubmissionRepository feeSubmissionRepository;

    public ExitFeeMonthsService(MonthmappingRepository monthmappingRepository,
                                FeeSubmissionRepository feeSubmissionRepository) {
        this.monthmappingRepository = monthmappingRepository;
        this.feeSubmissionRepository = feeSubmissionRepository;
    }

    /**
     * Month names from the start of the session up to the cut-off (the month of the last date, or the
     * month before it when that month is not counted). Empty Optional when the last date's month is not
     * one of the session's fee months, so the limit cannot be worked out.
     */
    @Transactional(readOnly = true)
    public Optional<List<String>> allowedMonths(StudentExit exit) {
        List<MonthMapping> sessionMonths = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(
                exit.getAcademicYear().getId(), exit.getSchool().getId());
        if (sessionMonths == null || sessionMonths.isEmpty()) {
            return Optional.empty();
        }
        String lastDateMonth = exit.getExitDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int lastIndex = -1;
        for (int i = 0; i < sessionMonths.size(); i++) {
            MonthMapping mm = sessionMonths.get(i);
            if (mm.getMonthMaster() != null && mm.getMonthMaster().getMonthName() != null
                    && lastDateMonth.equalsIgnoreCase(mm.getMonthMaster().getMonthName().trim())) {
                lastIndex = i;
                break;
            }
        }
        if (lastIndex < 0) {
            return Optional.empty();
        }
        int cutoff = exit.isIncludeLastMonth() ? lastIndex : lastIndex - 1;
        List<String> names = new ArrayList<>();
        for (int i = 0; i <= cutoff; i++) {
            names.add(sessionMonths.get(i).getMonthMaster().getMonthName().trim());
        }
        return Optional.of(names);
    }

    /** Month names that already have an Active fee submission for the exit's enrollment in its session. */
    @Transactional(readOnly = true)
    public Set<String> paidMonths(StudentExit exit) {
        Set<String> names = new LinkedHashSet<>();
        List<FeeSubmission> submissions = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(
                exit.getSchool().getId(), exit.getAcademicYear().getId(), exit.getAcademicStudent().getId());
        if (submissions != null) {
            for (FeeSubmission fs : submissions) {
                if (fs.getFeeSubmissionMonths() == null) {
                    continue;
                }
                for (FeeSubmissionMonths m : fs.getFeeSubmissionMonths()) {
                    if (m.getMonthMaster() != null && m.getMonthMaster().getMonthName() != null) {
                        names.add(m.getMonthMaster().getMonthName().trim());
                    }
                }
            }
        }
        return names;
    }

    /**
     * The balance the Fee Submission screen shows as "Previous balance": the latest submission's balance
     * this session, or the opening balance when nothing was submitted yet. Never negative.
     */
    @Transactional(readOnly = true)
    public BigDecimal carriedBalance(StudentExit exit) {
        List<Long> ids = List.of(exit.getAcademicStudent().getId());
        Long schoolId = exit.getSchool().getId();
        Long yearId = exit.getAcademicYear().getId();
        boolean submitted = !feeSubmissionRepository.findAcademicStudentIdsWithAnySubmission(schoolId, yearId, ids).isEmpty();
        BigDecimal balance;
        if (submitted) {
            List<Object[]> rows = feeSubmissionRepository.getLatestBalanceAmountsForStudents(schoolId, yearId, ids);
            balance = (rows == null || rows.isEmpty() || rows.get(0)[1] == null)
                    ? BigDecimal.ZERO : new BigDecimal(String.valueOf(rows.get(0)[1]));
        } else {
            balance = exit.getAcademicStudent().getOpeningBalance() != null
                    ? exit.getAcademicStudent().getOpeningBalance() : BigDecimal.ZERO;
        }
        return balance.max(BigDecimal.ZERO);
    }

    /** Splits "April, May, " into trimmed month names; null or blank gives an empty list. */
    public static List<String> splitMonths(Object csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String part : String.valueOf(csv).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
