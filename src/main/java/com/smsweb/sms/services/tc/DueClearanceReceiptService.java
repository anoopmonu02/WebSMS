package com.smsweb.sms.services.tc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsweb.sms.dto.tc.ClearDuesRequest;
import com.smsweb.sms.dto.tc.DuesPanelDto;
import com.smsweb.sms.dto.tc.ReceiptFilter;
import com.smsweb.sms.dto.tc.ReceiptPrintDto;
import com.smsweb.sms.dto.tc.ReceiptRowDto;
import com.smsweb.sms.dto.tc.ReceiptTotals;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.fees.FeeSubmissionSub;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.DueClearanceReceipt;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.tc.enums.ReceiptMode;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.models.tc.enums.TcStatus;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.tc.DueClearanceReceiptRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.TransferCertificateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Due Clearance Receipts: the dues panel of an exit, ledger-mode clearing (an exit from an earlier
 * session is cleared here, in one exact payment), voiding a ledger receipt (admin), the receipt
 * register and the print data.
 *
 * <p>Rule: no partial clearance. A ledger receipt must make {@code paid + waiver} equal the pending
 * amount. A waiver is admin only and needs a reason. Receipt numbers come from one counter per
 * school, taken under the school row lock (the same mutex the PSRN service uses), and are never
 * reused. Lock order: exit row, then school row.</p>
 *
 * <p>Same-session receipts (paid through the Fee Submission screen) share the same table. They are
 * created right after the fee is saved ({@link #createFeeReceipt}); what counts as cleared in that mode is
 * decided in {@code dueState}.</p>
 */
@Service
public class DueClearanceReceiptService {

    private static final Logger log = LoggerFactory.getLogger(DueClearanceReceiptService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PAYMENT_MODES = Set.of("CASH", "UPI", "CHEQUE", "BANK_TRANSFER", "CARD", "OTHER");
    private static final int MIN_REASON = 5;
    private static final int MAX_REASON = 255;

    private final DueClearanceReceiptRepository receiptRepository;
    private final StudentExitRepository studentExitRepository;
    private final TransferCertificateRepository transferCertificateRepository;
    private final SchoolRepository schoolRepository;
    private final TcConfigService tcConfigService;
    private final StudentEventService studentEventService;
    private final FeeSubmissionRepository feeSubmissionRepository;
    private final ExitFeeMonthsService exitFeeMonthsService;

    @PersistenceContext
    private EntityManager entityManager;

    public DueClearanceReceiptService(DueClearanceReceiptRepository receiptRepository,
                                      StudentExitRepository studentExitRepository,
                                      TransferCertificateRepository transferCertificateRepository,
                                      SchoolRepository schoolRepository,
                                      TcConfigService tcConfigService,
                                      StudentEventService studentEventService,
                                      FeeSubmissionRepository feeSubmissionRepository,
                                      ExitFeeMonthsService exitFeeMonthsService) {
        this.receiptRepository = receiptRepository;
        this.studentExitRepository = studentExitRepository;
        this.transferCertificateRepository = transferCertificateRepository;
        this.schoolRepository = schoolRepository;
        this.tcConfigService = tcConfigService;
        this.studentEventService = studentEventService;
        this.feeSubmissionRepository = feeSubmissionRepository;
        this.exitFeeMonthsService = exitFeeMonthsService;
    }

    // ───────────────────────────── dues panel ─────────────────────────────

    @Transactional(readOnly = true)
    public DuesPanelDto panel(UUID exitUuid, School school, AcademicYear currentYear) {
        StudentExit exit = studentExitRepository.findByUuidAndSchool_Id(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
        return buildPanel(exit, currentYear);
    }

    /** Works out the dues state of an exit. Must run inside a transaction (reads lazy data). */
    @Transactional(readOnly = true)
    public DuesPanelDto buildPanel(StudentExit exit, AcademicYear currentYear) {
        DueState st = dueState(exit, currentYear);
        DuesPanelDto p = new DuesPanelDto();
        p.setFrozenAmount(st.frozen);
        p.setSettledAmount(st.settled);
        p.setPendingAmount(st.pending);
        p.setCleared(st.cleared);
        p.setLedgerMode(st.ledgerYear);
        p.setCanClear(st.canClear);
        p.setAwaitingFeeScreen(st.awaitingFeeScreen);
        p.setOutstandingMonths(String.join(", ", st.outstanding));
        p.setMissingReceipts(st.awaitingFeeScreen || st.cleared ? findMissingFeeSubmissions(exit).size() : 0);
        p.setReceipts(receiptRepository.findByExit_IdOrderByIdAsc(exit.getId()).stream()
                .map(this::toRow).collect(Collectors.toList()));
        return p;
    }

    /**
     * The one place that decides what is still owed.
     *
     * <ul>
     *   <li><b>Earlier-session exit (ledger):</b> pending is the fixed dues minus the valid receipts.</li>
     *   <li><b>Same-session exit:</b> the Fee Submission screen works out the amounts (months, previous balance,
     *   fine). The exit is cleared when every month counted at strike-off is paid there and no balance is carried.
     *   A balance left with no month to collect it on is cleared through the ledger form.</li>
     *   <li>An exit with an amount typed by an admin, or with nothing to pay, uses the plain amount rule.</li>
     * </ul>
     */
    private DueState dueState(StudentExit exit, AcademicYear currentYear) {
        DueState s = new DueState();
        boolean fixed = exit.isDuesFrozen();
        s.frozen = fixed ? nz(exit.getFrozenAmount()) : BigDecimal.ZERO;
        s.settled = nz(receiptRepository.sumSettledByExit(exit.getId(), RecordStatus.VALID));
        s.ledgerYear = currentYear != null && !currentYear.getId().equals(exit.getAcademicYear().getId());
        boolean approved = exit.getStatus() == ExitStatus.APPROVED && fixed;
        BigDecimal byAmount = s.frozen.subtract(s.settled).max(BigDecimal.ZERO);

        if (!fixed) {
            s.pending = BigDecimal.ZERO;
            return s;
        }
        if (s.ledgerYear) {
            s.pending = byAmount;
            s.cleared = byAmount.signum() <= 0;
            s.canClear = approved && byAmount.signum() > 0;
            return s;
        }
        if (exit.isDuesManual() || s.frozen.signum() <= 0) {
            s.pending = byAmount;
            s.cleared = byAmount.signum() <= 0;
            s.awaitingFeeScreen = approved && byAmount.signum() > 0;
            return s;
        }

        // Same session, dues worked out by the fee calculator: follow the fee screen.
        Set<String> paid = lowerSet(exitFeeMonthsService.paidMonths(exit));
        List<String> outstanding = new ArrayList<>();
        for (String m : ExitFeeMonthsService.splitMonths(parseJson(exit.getFrozenBreakdown()).get("months"))) {
            if (!paid.contains(m.toLowerCase())) {
                outstanding.add(m);
            }
        }
        s.outstanding = outstanding;
        BigDecimal ledgerPaid = nz(receiptRepository.sumSettledByExitAndMode(exit.getId(), RecordStatus.VALID, ReceiptMode.LEDGER));
        BigDecimal balanceOwed = exitFeeMonthsService.carriedBalance(exit).subtract(ledgerPaid).max(BigDecimal.ZERO);
        if (outstanding.isEmpty()) {
            s.pending = balanceOwed;
            s.cleared = balanceOwed.signum() <= 0;
            s.canClear = approved && balanceOwed.signum() > 0;
        } else {
            s.pending = byAmount;
            s.awaitingFeeScreen = approved;
        }
        return s;
    }

    private static final class DueState {
        BigDecimal frozen = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        boolean cleared;
        boolean ledgerYear;
        boolean canClear;
        boolean awaitingFeeScreen;
        List<String> outstanding = Collections.emptyList();
    }

    private static Set<String> lowerSet(Set<String> in) {
        return in.stream().map(x -> x.toLowerCase()).collect(Collectors.toSet());
    }

    // ───────────────────────────── clear dues (ledger) ─────────────────────────────

    /**
     * Records the payment that clears an earlier-session exit and returns the receipt. Posting the
     * same {@code submissionToken} again returns the first receipt instead of creating another.
     */
    @Transactional
    public DueClearanceReceipt createLedgerReceipt(UUID exitUuid, ClearDuesRequest req, School school,
                                                   AcademicYear currentYear, UserEntity user) {
        log.info("Inside createLedgerReceipt: exit={}", exitUuid);
        requireContext(school, currentYear, user);
        if (req == null) {
            throw new TcValidationException("Enter the payment details.");
        }
        StudentExit exit = lockExit(exitUuid, school);

        String token = blankToNull(req.getSubmissionToken());
        if (token != null) {
            Optional<DueClearanceReceipt> prior = receiptRepository.findBySubmissionToken(token);
            if (prior.isPresent()) {
                if (prior.get().getExit().getId().equals(exit.getId())) {
                    return prior.get();
                }
                throw new TcValidationException("This form was already used. Please open it again.");
            }
        }

        if (exit.getStatus() != ExitStatus.APPROVED || !exit.isDuesFrozen()) {
            throw new InvalidExitStateException("Dues can be cleared only after the admin has approved the exit.");
        }
        DueState state = dueState(exit, currentYear);
        if (!state.canClear) {
            if (state.awaitingFeeScreen) {
                throw new InvalidExitStateException(
                        "This exit is from the current session. Its dues are collected on the Fee Submission screen.");
            }
            throw new InvalidExitStateException("No dues are pending for this exit.");
        }
        BigDecimal pending = state.pending;

        BigDecimal paid = money(req.getAmountPaid());
        BigDecimal waiver = money(req.getDiscountAmount());
        if (paid.add(waiver).compareTo(pending) != 0) {
            throw new TcValidationException("Amount paid plus waiver must be exactly the pending amount (₹ "
                    + pending.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        String discountReason = null;
        if (waiver.signum() > 0) {
            if (!StudentExitService.isAdmin()) {
                throw new TcValidationException("Only an admin can give a waiver.");
            }
            discountReason = reason(req.getDiscountReason(), "reason for the waiver");
        }
        String paymentMode = null;
        if (paid.signum() > 0) {
            paymentMode = blankToNull(req.getPaymentMode());
            if (paymentMode == null || !PAYMENT_MODES.contains(paymentMode.toUpperCase())) {
                throw new TcValidationException("Choose the payment mode.");
            }
            paymentMode = paymentMode.toUpperCase();
        }
        String reference = cut(req.getReferenceNo(), 100, "Reference number");
        String remark = cut(req.getRemark(), 500, "Remark");

        long[] seqHolder = new long[1];
        String number = allocateReceiptNumber(school, seqHolder);
        long seq = seqHolder[0];

        DueClearanceReceipt r = new DueClearanceReceipt();
        r.setSchool(school);
        r.setExit(exit);
        r.setReceiptSeq(seq);
        r.setReceiptNo(number);
        r.setReceiptDate(LocalDate.now());
        r.setMode(ReceiptMode.LEDGER);
        r.setAmountPaid(paid);
        r.setDiscountAmount(waiver);
        r.setDiscountReason(discountReason);
        r.setPaymentMode(paymentMode);
        r.setReferenceNo(reference);
        r.setBreakdown(exit.getFrozenBreakdown());
        r.setRemark(remark);
        r.setSubmissionToken(token);
        r.setCreatedBy(user);
        r = receiptRepository.saveAndFlush(r);

        LocalDate today = LocalDate.now();
        if (paid.signum() > 0) {
            studentEventService.log(EventType.DUES_PAID, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                    today, null, paid.toPlainString(), "Receipt " + number, RefType.RECEIPT, r.getId(),
                    EventSource.WEB, user);
        }
        if (waiver.signum() > 0) {
            studentEventService.log(EventType.DUES_WAIVED, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                    today, null, waiver.toPlainString(), discountReason, RefType.RECEIPT, r.getId(),
                    EventSource.WEB, user);
        }
        return r;
    }

    // ───────────────────────────── same-session receipts (Fee Submission screen) ─────────────────────────────

    /**
     * Creates the receipt for a fee that was just saved on the Fee Submission screen for a struck-off
     * student. Returns null when the fee does not belong to an approved same-session exit (a normal fee).
     * Safe to call twice: the second call returns the first receipt. The amounts come from the saved fee
     * (paid amount and the admin discount); nothing is recalculated here.
     */
    @Transactional
    public DueClearanceReceipt createFeeReceipt(Long feeSubmissionId, School school, AcademicYear currentYear,
                                                UserEntity user) {
        log.info("Inside createFeeReceipt: feeSubmissionId={}", feeSubmissionId);
        requireContext(school, currentYear, user);
        if (feeSubmissionId == null) {
            return null;
        }
        Optional<DueClearanceReceipt> prior = receiptRepository.findByFeeSubmission_Id(feeSubmissionId);
        if (prior.isPresent()) {
            return prior.get();
        }
        FeeSubmission fs = feeSubmissionRepository.findById(feeSubmissionId).orElse(null);
        if (fs == null || fs.getSchool() == null || !school.getId().equals(fs.getSchool().getId())
                || !"Active".equalsIgnoreCase(fs.getStatus()) || fs.getAcademicStudent() == null) {
            return null;
        }
        UUID exitUuid = studentExitRepository.findFirstByAcademicStudent_IdAndSchool_IdAndStatus(
                fs.getAcademicStudent().getId(), school.getId(), ExitStatus.APPROVED)
                .map(StudentExit::getUuid).orElse(null);
        if (exitUuid == null) {
            return null;
        }
        StudentExit exit = lockExit(exitUuid, school);
        prior = receiptRepository.findByFeeSubmission_Id(feeSubmissionId);
        if (prior.isPresent()) {
            return prior.get();
        }
        if (exit.getStatus() != ExitStatus.APPROVED || !exit.isDuesFrozen()
                || !exit.getAcademicYear().getId().equals(currentYear.getId())
                || fs.getAcademicYear() == null || !fs.getAcademicYear().getId().equals(exit.getAcademicYear().getId())) {
            return null;
        }
        return saveFeeReceipt(fs, exit, school, currentYear, user);
    }

    /** Creates receipts for fees saved after approval that somehow have none (the repair button on the exit page). */
    @Transactional
    public int createMissingReceipts(UUID exitUuid, School school, AcademicYear currentYear, UserEntity user) {
        log.info("Inside createMissingReceipts: exit={}", exitUuid);
        requireContext(school, currentYear, user);
        StudentExit exit = lockExit(exitUuid, school);
        if (exit.getStatus() != ExitStatus.APPROVED || !exit.isDuesFrozen()
                || !exit.getAcademicYear().getId().equals(currentYear.getId())) {
            throw new InvalidExitStateException("Receipts can be created only for an approved exit of the current session.");
        }
        int made = 0;
        for (FeeSubmission fs : findMissingFeeSubmissions(exit)) {
            saveFeeReceipt(fs, exit, school, currentYear, user);
            made++;
        }
        return made;
    }

    private DueClearanceReceipt saveFeeReceipt(FeeSubmission fs, StudentExit exit, School school,
                                               AcademicYear currentYear, UserEntity user) {
        BigDecimal paid = nz(fs.getPaidAmount());
        BigDecimal discount = nz(fs.getMigrationDiscountAmount());

        long[] seqHolder = new long[1];
        String number = allocateReceiptNumber(school, seqHolder);

        DueClearanceReceipt r = new DueClearanceReceipt();
        r.setSchool(school);
        r.setExit(exit);
        r.setReceiptSeq(seqHolder[0]);
        r.setReceiptNo(number);
        r.setReceiptDate(LocalDate.now());
        r.setMode(ReceiptMode.SAME_SESSION);
        r.setFeeSubmission(fs);
        r.setAmountPaid(paid);
        r.setDiscountAmount(discount);
        r.setDiscountReason(discount.signum() > 0 ? "Admin discount on the Fee Submission screen" : null);
        r.setPaymentMode(fs.getPaymentType());
        r.setBreakdown(feeBreakdownJson(fs, paid, discount));
        r.setRemark("Fee receipt " + fs.getReceiptNo());
        r.setCreatedBy(user);
        r = receiptRepository.saveAndFlush(r);

        LocalDate today = LocalDate.now();
        if (paid.signum() > 0) {
            studentEventService.log(EventType.DUES_PAID, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                    today, null, paid.toPlainString(), "Receipt " + number + " (fee receipt " + fs.getReceiptNo() + ")",
                    RefType.RECEIPT, r.getId(), EventSource.WEB, user);
        }
        if (discount.signum() > 0) {
            studentEventService.log(EventType.DUES_WAIVED, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                    today, null, discount.toPlainString(), r.getDiscountReason(), RefType.RECEIPT, r.getId(),
                    EventSource.WEB, user);
        }
        return r;
    }

    /** Active fee submissions of the exit's enrollment, made at or after approval, with no receipt yet. */
    private List<FeeSubmission> findMissingFeeSubmissions(StudentExit exit) {
        List<FeeSubmission> out = new ArrayList<>();
        if (exit.getStatus() != ExitStatus.APPROVED || !exit.isDuesFrozen() || exit.getApprovedAt() == null) {
            return out;
        }
        Date since = Date.from(exit.getApprovedAt().atZone(ZoneId.systemDefault()).toInstant());
        List<FeeSubmission> all = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(
                exit.getSchool().getId(), exit.getAcademicYear().getId(), exit.getAcademicStudent().getId());
        if (all == null) {
            return out;
        }
        for (FeeSubmission fs : all) {
            if (fs.getCreationDate() != null && !fs.getCreationDate().before(since)
                    && receiptRepository.findByFeeSubmission_Id(fs.getId()).isEmpty()) {
                out.add(fs);
            }
        }
        return out;
    }

    /** What the receipt prints for a fee-screen payment: the months and heads paid, the fine and the total. */
    private String feeBreakdownJson(FeeSubmission fs, BigDecimal paid, BigDecimal discount) {
        List<String> months = new ArrayList<>();
        if (fs.getFeeSubmissionMonths() != null) {
            for (FeeSubmissionMonths m : fs.getFeeSubmissionMonths()) {
                if (m.getMonthMaster() != null) {
                    months.add(m.getMonthMaster().getMonthName());
                }
            }
        }
        List<String> heads = new ArrayList<>();
        if (fs.getFeeSubmissionSub() != null) {
            for (FeeSubmissionSub sub : fs.getFeeSubmissionSub()) {
                if (sub.getFeehead() != null && sub.getFeehead().getFeeHeadName() != null) {
                    heads.add(sub.getFeehead().getFeeHeadName());
                }
            }
        }
        BigDecimal fine = nz(fs.getFineAmount());
        BigDecimal total = paid.add(discount);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("total", total.toPlainString());
        json.put("fine", fine.toPlainString());
        json.put("feesAndBalance", total.subtract(fine).max(BigDecimal.ZERO).toPlainString());
        json.put("months", String.join(", ", months));
        json.put("heads", String.join(", ", heads));
        try {
            return MAPPER.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write the receipt breakdown", e);
        }
    }

    /** Takes the next receipt number of the school. The school row must already be locked by the caller. */
    private String allocateReceiptNumber(School school, long[] seqOut) {
        schoolRepository.findByIdForUpdate(school.getId())
                .orElseThrow(() -> new TcValidationException("The school was not found."));
        Long max = receiptRepository.findMaxReceiptSeqBySchoolId(school.getId());
        long seq = max != null ? max + 1 : Math.max(1L, tcConfigService.receiptStart(school));
        seqOut[0] = seq;
        return tcConfigService.receiptPrefix(school)
                + String.format("%0" + tcConfigService.receiptPad(school) + "d", seq);
    }

    // ───────────────────────────── void (admin) ─────────────────────────────

    /** Voids a ledger receipt (admin only) so the exit shows the amount as pending again. The number is never reused. */
    @Transactional
    public DueClearanceReceipt voidReceipt(UUID receiptUuid, String reason, School school,
                                           AcademicYear currentYear, UserEntity user) {
        log.info("Inside voidReceipt: receipt={}", receiptUuid);
        if (!StudentExitService.isAdmin()) {
            throw new TcValidationException("Only an admin can void a receipt.");
        }
        requireContext(school, currentYear, user);
        String why = reason(reason, "reason for voiding");

        DueClearanceReceipt probe = receiptRepository.findByUuidAndSchool_Id(receiptUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This receipt was not found."));
        UUID exitUuid = probe.getExit().getUuid();
        StudentExit exit = lockExit(exitUuid, school);
        // Read again after the lock so a concurrent void is seen.
        entityManager.refresh(probe);

        if (probe.getRecordStatus() != RecordStatus.VALID) {
            throw new InvalidExitStateException("This receipt is already voided.");
        }
        if (probe.getMode() != ReceiptMode.LEDGER) {
            throw new InvalidExitStateException(
                    "A receipt made from the Fee Submission screen cannot be voided here.");
        }
        if (transferCertificateRepository.findFirstByExit_IdAndStatus(exit.getId(), TcStatus.VALID).isPresent()) {
            throw new InvalidExitStateException("A TC was already issued for this exit, so the receipt cannot be voided.");
        }
        probe.setRecordStatus(RecordStatus.VOIDED);
        probe.setVoidedBy(user);
        probe.setVoidedAt(java.time.LocalDateTime.now());
        probe.setVoidReason(why);

        studentEventService.log(EventType.DUES_RECEIPT_VOIDED, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                LocalDate.now(), probe.getReceiptNo(), null, why, RefType.RECEIPT, probe.getId(), EventSource.WEB, user);
        return probe;
    }

    // ───────────────────────────── register and print ─────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReceiptRowDto> search(ReceiptFilter filter, School school) {
        ReceiptFilter f = filter == null ? new ReceiptFilter() : filter;
        int size = Math.min(Math.max(f.getSize(), 1), 100);
        Pageable pageable = PageRequest.of(Math.max(f.getPage(), 0), size, Sort.by(Sort.Direction.DESC, "id"));
        return receiptRepository.findAll(registerSpec(f, school.getId()), pageable).map(this::toRow);
    }

    /** Paid and waived totals of the VALID receipts that match the filter (whole result, not one page). */
    @Transactional(readOnly = true)
    public ReceiptTotals totals(ReceiptFilter filter, School school) {
        ReceiptFilter f = filter == null ? new ReceiptFilter() : filter;
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<DueClearanceReceipt> root = q.from(DueClearanceReceipt.class);
        Join<DueClearanceReceipt, StudentExit> exit = root.join("exit");
        Join<StudentExit, Student> student = exit.join("student");
        List<Predicate> p = registerPredicates(cb, root, student, f, school.getId());
        p.add(cb.equal(root.get("recordStatus"), RecordStatus.VALID));
        q.multiselect(cb.sum(root.<BigDecimal>get("amountPaid")), cb.sum(root.<BigDecimal>get("discountAmount")))
                .where(p.toArray(new Predicate[0]));
        Object[] row = entityManager.createQuery(q).getSingleResult();
        return new ReceiptTotals(row[0] == null ? BigDecimal.ZERO : (BigDecimal) row[0],
                row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
    }

    @Transactional(readOnly = true)
    public ReceiptPrintDto printData(UUID receiptUuid, School school) {
        DueClearanceReceipt r = receiptRepository.findByUuidAndSchool_Id(receiptUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This receipt was not found."));
        StudentExit e = r.getExit();
        Student s = e.getStudent();

        BigDecimal settledUpToHere = receiptRepository.findByExit_IdOrderByIdAsc(e.getId()).stream()
                .filter(x -> x.getRecordStatus() == RecordStatus.VALID && x.getId() <= r.getId())
                .map(x -> nz(x.getAmountPaid()).add(nz(x.getDiscountAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal frozen = nz(e.getFrozenAmount());

        ReceiptPrintDto d = new ReceiptPrintDto();
        School sc = e.getSchool();
        d.setSchoolName(sc.getSchoolName());
        d.setSchoolAddress(joinNonBlank(" - ", sc.getAddress(), sc.getPincode()));
        d.setSchoolPhone(sc.getMobile1());
        d.setReceiptNo(r.getReceiptNo());
        d.setReceiptDate(r.getReceiptDate());
        d.setMode(r.getMode());
        d.setVoided(r.getRecordStatus() == RecordStatus.VOIDED);
        d.setVoidReason(r.getVoidReason());
        d.setStudentName(s.getStudentName());
        d.setFatherName(s.getFatherName());
        d.setClassName(e.getGrade().getGradeName() + " - " + e.getSection().getSectionName());
        d.setSessionName(e.getAcademicYear().getSessionFormat());
        d.setRegistrationNo(s.getRegistrationNo());
        d.setPsrn(s.getPsrn() == null ? null : String.valueOf(s.getPsrn()));
        d.setSrNo(e.getAcademicStudent().getClassSrNo());
        d.setExitDate(e.getExitDate());
        d.setReasonLabel(e.getExitReason().getLabel());
        boolean feeScreen = r.getMode() == ReceiptMode.SAME_SESSION;
        // A fee-screen receipt prints what that payment covered; a ledger receipt prints the fixed dues.
        d.setBreakdown(numericBreakdown(feeScreen ? r.getBreakdown() : e.getFrozenBreakdown()));
        d.setFrozenAmount(feeScreen ? nz(r.getAmountPaid()).add(nz(r.getDiscountAmount())) : frozen);
        d.setAmountPaid(nz(r.getAmountPaid()));
        d.setDiscountAmount(nz(r.getDiscountAmount()));
        d.setDiscountReason(r.getDiscountReason());
        d.setSettledUpToHere(settledUpToHere);
        d.setBalanceAfter(feeScreen && r.getFeeSubmission() != null
                ? nz(r.getFeeSubmission().getBalanceAmount())
                : frozen.subtract(settledUpToHere).max(BigDecimal.ZERO));
        d.setAmountInWords(AmountInWords.rupees(nz(r.getAmountPaid())));
        d.setPaymentMode(r.getPaymentMode());
        d.setReferenceNo(r.getReferenceNo());
        d.setRemark(r.getRemark());
        d.setExitUuid(e.getUuid());
        d.setFeeSubmissionId(r.getFeeSubmission() == null ? null : r.getFeeSubmission().getId());
        return d;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private StudentExit lockExit(UUID exitUuid, School school) {
        if (exitUuid == null) {
            throw new TcValidationException("This exit record was not found.");
        }
        return studentExitRepository.findByUuidForUpdate(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
    }

    private ReceiptRowDto toRow(DueClearanceReceipt r) {
        StudentExit e = r.getExit();
        return new ReceiptRowDto(r.getUuid(), e.getUuid(), r.getReceiptNo(), r.getReceiptDate(), r.getMode(),
                nz(r.getAmountPaid()), nz(r.getDiscountAmount()), r.getPaymentMode(), r.getReferenceNo(),
                r.getRecordStatus(), r.getVoidReason(), e.getStudent().getStudentName(),
                e.getGrade().getGradeName() + " " + e.getSection().getSectionName());
    }

    @SuppressWarnings("unchecked")
    private static Specification<DueClearanceReceipt> registerSpec(ReceiptFilter f, Long schoolId) {
        return (root, query, cb) -> {
            boolean count = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<DueClearanceReceipt, StudentExit> exit;
            Join<StudentExit, Student> student;
            if (count) {
                exit = root.join("exit");
                student = exit.join("student");
            } else {
                exit = (Join<DueClearanceReceipt, StudentExit>) (Object) root.fetch("exit");
                student = (Join<StudentExit, Student>) (Object) exit.fetch("student");
                exit.fetch("grade");
                exit.fetch("section");
            }
            List<Predicate> p = registerPredicates(cb, root, student, f, schoolId);
            if (!f.isIncludeVoided()) {
                p.add(cb.equal(root.get("recordStatus"), RecordStatus.VALID));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static List<Predicate> registerPredicates(CriteriaBuilder cb, Root<DueClearanceReceipt> root,
                                                      Join<StudentExit, Student> student, ReceiptFilter f, Long schoolId) {
        List<Predicate> p = new ArrayList<>();
        p.add(cb.equal(root.get("school").get("id"), schoolId));
        if (f.getFromDate() != null) {
            p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("receiptDate"), f.getFromDate()));
        }
        if (f.getToDate() != null) {
            p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("receiptDate"), f.getToDate()));
        }
        String text = f.getQ() == null ? "" : f.getQ().replace("%", "").replace("_", "").trim().toLowerCase();
        if (!text.isEmpty()) {
            String like = "%" + text + "%";
            p.add(cb.or(
                    cb.like(cb.lower(root.<String>get("receiptNo")), like),
                    cb.like(cb.lower(student.<String>get("studentName")), like)));
        }
        return p;
    }

    private static void requireContext(School school, AcademicYear year, UserEntity user) {
        if (school == null) {
            throw new TcValidationException("No school is selected for this login.");
        }
        if (year == null) {
            throw new TcValidationException("No active session was found.");
        }
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value.signum() < 0) {
            throw new TcValidationException("Amounts cannot be negative.");
        }
        if (value.compareTo(new BigDecimal("9999999.99")) > 0) {
            throw new TcValidationException("The amount is too large.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String reason(String value, String label) {
        String text = blankToNull(value);
        if (text == null || text.length() < MIN_REASON) {
            throw new TcValidationException("Please enter the " + label + " (at least " + MIN_REASON + " characters).");
        }
        if (text.length() > MAX_REASON) {
            throw new TcValidationException("The " + label + " is too long (most " + MAX_REASON + " characters).");
        }
        return text;
    }

    private static String cut(String value, int max, String label) {
        String text = blankToNull(value);
        if (text != null && text.length() > max) {
            throw new TcValidationException(label + " is too long (most " + max + " characters).");
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String joinNonBlank(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.trim());
            }
        }
        return String.join(separator, kept);
    }

    /** The stored breakdown with its money entries as numbers, so the print page can format them. */
    private static Map<String, Object> numericBreakdown(String json) {
        Map<String, Object> m = parseJson(json);
        for (String key : new String[] {"total", "fine", "feesAndBalance"}) {
            Object v = m.get(key);
            if (v != null) {
                try {
                    m.put(key, new BigDecimal(String.valueOf(v)));
                } catch (NumberFormatException ex) {
                    m.remove(key);
                }
            }
        }
        return m;
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            log.warn("Could not read a stored breakdown", e);
            return new LinkedHashMap<>();
        }
    }
}
