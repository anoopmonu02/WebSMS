package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Read-only access to the fees a student already paid at the PREVIOUS branch(es) of a Mid
 * Session Migration.
 *
 * A migrated student's new enrollment row points to the old one (AcademicStudent
 * .carriedFromAcademicStudent). Nothing is copied: this service follows that link and reads the
 * old rows' own active fee receipts, so the new branch can lock those months and show the old
 * receipts. Months are identified by MonthMaster id, which is the same in every branch, so a
 * month paid at the old branch matches the same month at the new one.
 *
 * Students without a link (the normal case) cost one small id lookup and get an empty result,
 * so every caller behaves exactly as before for them. This class never writes anything. If the
 * linked data cannot be read it throws: callers that protect money (duplicate-month guard,
 * migration gate) must refuse, they must never treat a failure as "nothing paid".
 */
@Service
public class LinkedFeeHistoryService {

    private static final Logger log = LoggerFactory.getLogger(LinkedFeeHistoryService.class);

    /** Longest chain followed (A to B to C ...). A longer chain is cut here and logged. */
    static final int MAX_CHAIN_DEPTH = 5;

    private final AcademicStudentRepository academicStudentRepository;
    private final FeeSubmissionRepository feeSubmissionRepository;

    @Autowired
    public LinkedFeeHistoryService(AcademicStudentRepository academicStudentRepository,
                                   FeeSubmissionRepository feeSubmissionRepository) {
        this.academicStudentRepository = academicStudentRepository;
        this.feeSubmissionRepository = feeSubmissionRepository;
    }

    /** One month paid at a previous branch, with the receipt that paid it. Plain values only. */
    public static final class LinkedPaidMonth {
        private final Long monthMasterId;
        private final String monthName;
        private final Long feeSubmissionId;
        private final String receiptNo;
        private final Date submissionDate;
        private final Long branchSchoolId;
        private final String branchName;
        private final Long academicStudentId;

        LinkedPaidMonth(Long monthMasterId, String monthName, Long feeSubmissionId, String receiptNo,
                        Date submissionDate, Long branchSchoolId, String branchName, Long academicStudentId) {
            this.monthMasterId = monthMasterId;
            this.monthName = monthName;
            this.feeSubmissionId = feeSubmissionId;
            this.receiptNo = receiptNo;
            this.submissionDate = submissionDate;
            this.branchSchoolId = branchSchoolId;
            this.branchName = branchName;
            this.academicStudentId = academicStudentId;
        }

        public Long getMonthMasterId() { return monthMasterId; }
        public String getMonthName() { return monthName; }
        public Long getFeeSubmissionId() { return feeSubmissionId; }
        public String getReceiptNo() { return receiptNo; }
        public Date getSubmissionDate() { return submissionDate; }
        public Long getBranchSchoolId() { return branchSchoolId; }
        public String getBranchName() { return branchName; }
        public Long getAcademicStudentId() { return academicStudentId; }
    }

    /**
     * Ids of the previous enrollment rows of this student, nearest first (the row it was moved
     * out of, then that row's own predecessor, ...). Empty for an ordinary student. Stops at
     * MAX_CHAIN_DEPTH and on a repeated id, so a bad link can never loop.
     */
    public List<Long> getChainIds(Long academicStudentId) {
        if (academicStudentId == null) return Collections.emptyList();
        List<Long> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(academicStudentId);
        Long current = academicStudentId;
        for (int depth = 0; depth < MAX_CHAIN_DEPTH; depth++) {
            Long previous = academicStudentRepository.findCarriedFromAcademicStudentId(current);
            if (previous == null) return chain;
            if (!seen.add(previous)) {
                log.warn("Mid session link loop detected at academicStudentId={}, stopping", previous);
                return chain;
            }
            chain.add(previous);
            current = previous;
        }
        if (academicStudentRepository.findCarriedFromAcademicStudentId(current) != null) {
            log.warn("Mid session link chain for academicStudentId={} is longer than {}, older rows ignored",
                    academicStudentId, MAX_CHAIN_DEPTH);
        }
        return chain;
    }

    /**
     * Months paid at previous branches, keyed by MonthMaster id. Only active receipts count.
     * When the same month appears in more than one row of the chain, the nearest branch wins.
     * Empty (not null) for a student without a link.
     */
    @Transactional(readOnly = true)
    public Map<Long, LinkedPaidMonth> getLinkedPaidMonths(Long academicStudentId) {
        List<Long> chain = getChainIds(academicStudentId);
        if (chain.isEmpty()) return Collections.emptyMap();

        Map<Long, LinkedPaidMonth> byMonth = new LinkedHashMap<>();
        for (Long previousId : chain) {
            List<FeeSubmission> receipts = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(previousId, "Active");
            if (receipts == null) continue;
            for (FeeSubmission fs : receipts) {
                if (fs.getFeeSubmissionMonths() == null) continue;
                Long schoolId = fs.getSchool() != null ? fs.getSchool().getId() : null;
                String schoolName = fs.getSchool() != null ? fs.getSchool().getSchoolName() : null;
                for (FeeSubmissionMonths fm : fs.getFeeSubmissionMonths()) {
                    if (fm.getMonthMaster() == null) continue;
                    Long monthId = fm.getMonthMaster().getId();
                    if (byMonth.containsKey(monthId)) continue;
                    byMonth.put(monthId, new LinkedPaidMonth(monthId, fm.getMonthMaster().getMonthName(),
                            fs.getId(), fs.getReceiptNo(), fs.getFeeSubmissionDate(),
                            schoolId, schoolName, previousId));
                }
            }
        }
        return byMonth;
    }

    /**
     * What the Fee Submission screen shows in its "Paid at <previous branch>" strip, as plain
     * JSON-ready values: PreviousBranchMonths (names), PreviousBranchName, PreviousBranchReceipts
     * (receiptNo, branchName, date dd-MMM-yyyy, months). Empty map for a student without a link.
     * Display only: the months are locked through getLinkedPaidMonthNames / the save guard.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFeeScreenSummary(Long academicStudentId) {
        Map<Long, LinkedPaidMonth> linked = getLinkedPaidMonths(academicStudentId);
        if (linked.isEmpty()) return Collections.emptyMap();

        List<LinkedPaidMonth> ordered = new ArrayList<>(linked.values());
        ordered.sort(Comparator.comparing(LinkedPaidMonth::getSubmissionDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd-MMM-yyyy");
        dateFmt.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        List<String> monthNames = new ArrayList<>();
        Set<String> branches = new LinkedHashSet<>();
        Map<Long, Map<String, Object>> receipts = new LinkedHashMap<>();
        for (LinkedPaidMonth m : ordered) {
            if (m.getMonthName() != null) monthNames.add(m.getMonthName());
            if (m.getBranchName() != null) branches.add(m.getBranchName());
            Map<String, Object> receipt = receipts.get(m.getFeeSubmissionId());
            if (receipt == null) {
                receipt = new LinkedHashMap<>();
                receipt.put("id", m.getFeeSubmissionId());
                receipt.put("receiptNo", m.getReceiptNo());
                receipt.put("branchName", m.getBranchName());
                receipt.put("date", m.getSubmissionDate() != null ? dateFmt.format(m.getSubmissionDate()) : null);
                receipt.put("months", new ArrayList<String>());
                receipts.put(m.getFeeSubmissionId(), receipt);
            }
            @SuppressWarnings("unchecked")
            List<String> receiptMonths = (List<String>) receipt.get("months");
            if (m.getMonthName() != null) receiptMonths.add(m.getMonthName());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("PreviousBranchMonths", monthNames);
        summary.put("PreviousBranchName", String.join(", ", branches));
        summary.put("PreviousBranchReceipts", new ArrayList<>(receipts.values()));
        return summary;
    }

    /**
     * For list-style screens (Fee Reminder, Pending Fee Summary): MonthMaster ids already paid at
     * the previous branch(es), per student of one school + academic year. Only students that
     * have a link appear in the result; for a school without any migrated-in student this is one
     * small query and an empty map, so those reports run exactly as before.
     */
    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> getLinkedPaidMonthIdsForSchoolYear(Long schoolId, Long academicYearId) {
        List<Object[]> firstHop = academicStudentRepository.findCarriedFromPairsForSchoolYear(schoolId, academicYearId);
        if (firstHop == null || firstHop.isEmpty()) return Collections.emptyMap();

        Map<Long, List<Long>> chainByStudent = new HashMap<>();   // student row -> previous rows, nearest first
        Map<Long, Long> frontier = new HashMap<>();                // student row -> last row of its chain so far
        Set<Long> allRows = new HashSet<>();
        for (Object[] p : firstHop) {
            Long studentId = ((Number) p[0]).longValue();
            Long previousId = ((Number) p[1]).longValue();
            List<Long> chain = new ArrayList<>();
            chain.add(previousId);
            chainByStudent.put(studentId, chain);
            frontier.put(studentId, previousId);
            allRows.add(previousId);
        }

        // Follow chains (A to B to C ...) one step at a time, same limits as getChainIds.
        for (int depth = 1; depth < MAX_CHAIN_DEPTH && !frontier.isEmpty(); depth++) {
            List<Object[]> hop = academicStudentRepository.findCarriedFromPairs(new HashSet<>(frontier.values()));
            if (hop == null || hop.isEmpty()) break;
            Map<Long, Long> nextOf = new HashMap<>();
            for (Object[] p : hop) nextOf.put(((Number) p[0]).longValue(), ((Number) p[1]).longValue());

            Map<Long, Long> nextFrontier = new HashMap<>();
            for (Map.Entry<Long, Long> e : frontier.entrySet()) {
                Long next = nextOf.get(e.getValue());
                List<Long> chain = chainByStudent.get(e.getKey());
                if (next == null || next.equals(e.getKey()) || chain.contains(next)) continue;
                chain.add(next);
                allRows.add(next);
                nextFrontier.put(e.getKey(), next);
            }
            frontier = nextFrontier;
        }

        Map<Long, Set<Long>> monthsByRow = new HashMap<>();
        List<Object[]> paid = feeSubmissionRepository.getSubmittedMonthIdsForStudentRows(allRows);
        if (paid != null) {
            for (Object[] p : paid) {
                monthsByRow.computeIfAbsent(((Number) p[0]).longValue(), k -> new HashSet<>())
                        .add(((Number) p[1]).longValue());
            }
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        for (Map.Entry<Long, List<Long>> e : chainByStudent.entrySet()) {
            Set<Long> months = new HashSet<>();
            for (Long row : e.getValue()) {
                Set<Long> rowMonths = monthsByRow.get(row);
                if (rowMonths != null) months.addAll(rowMonths);
            }
            if (!months.isEmpty()) result.put(e.getKey(), months);
        }
        return result;
    }

    /**
     * Active receipts of the previous branch(es), for the Fee Receipt list (display + print).
     * Each row has the same keys as the normal receipt list row plus previousBranch=true and
     * branchName. Oldest first. Empty (not null) for a student without a link. Own receipts of
     * the student are NOT included: the caller adds them as before.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChainReceipts(Long academicStudentId) {
        List<Long> chain = getChainIds(academicStudentId);
        if (chain.isEmpty()) return Collections.emptyList();

        List<FeeSubmission> all = new ArrayList<>();
        for (Long previousId : chain) {
            List<FeeSubmission> receipts = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(previousId, "Active");
            if (receipts != null) all.addAll(receipts);
        }
        all.sort(Comparator.comparing(FeeSubmission::getFeeSubmissionDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FeeSubmission fs : all) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", fs.getId());
            row.put("feeSubmissionDate", fs.getFeeSubmissionDate());
            row.put("receiptNo", fs.getReceiptNo() != null ? fs.getReceiptNo() : "");
            row.put("totalAmount", fs.getTotalAmount());
            row.put("paidAmount", fs.getPaidAmount());
            row.put("balanceAmount", fs.getBalanceAmount());
            row.put("paymentType", fs.getPaymentType() != null ? fs.getPaymentType() : "");
            row.put("status", fs.getStatus() != null ? fs.getStatus() : "");
            row.put("previousBranch", true);
            row.put("branchName", fs.getSchool() != null && fs.getSchool().getSchoolName() != null
                    ? fs.getSchool().getSchoolName() : "");
            rows.add(row);
        }
        return rows;
    }

    /** MonthMaster ids paid at previous branches (see getLinkedPaidMonths). */
    public Set<Long> getLinkedPaidMonthIds(Long academicStudentId) {
        return new HashSet<>(getLinkedPaidMonths(academicStudentId).keySet());
    }

    /** Month names paid at previous branches, in calendar order of the receipts read. */
    public Set<String> getLinkedPaidMonthNames(Long academicStudentId) {
        Set<String> names = new LinkedHashSet<>();
        for (LinkedPaidMonth m : getLinkedPaidMonths(academicStudentId).values()) {
            if (m.getMonthName() != null) names.add(m.getMonthName());
        }
        return names;
    }
}
