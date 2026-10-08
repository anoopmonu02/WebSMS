package com.smsweb.sms.services.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.tc.DueClearanceReceipt;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The seam between the Fee Submission screen and the TC module, for a struck-off student whose exit is
 * approved and is in the current session. Everything here is defensive on purpose: it must never break the
 * normal fee flow, so every entry point is a no-op for an ordinary student and swallows its own failures
 * (a failure is logged and treated as "not an exit payment", except where noted).
 *
 * <ul>
 *   <li>{@link #findCollectable}: lets the fee page look the student up (the normal lookup only finds Active students).</li>
 *   <li>{@link #validateFeeSubmission}: before a save, refuses months after the last date and part payments.</li>
 *   <li>{@link #afterFeeSaved}: after a save, creates the Due Clearance Receipt.</li>
 * </ul>
 */
@Service
public class ExitFeeCollectionService {

    private static final Logger log = LoggerFactory.getLogger(ExitFeeCollectionService.class);

    private final StudentExitRepository studentExitRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final ExitFeeMonthsService exitFeeMonthsService;
    private final DueClearanceReceiptService receiptService;
    private final TcConfigService tcConfigService;
    private final UserService userService;

    public ExitFeeCollectionService(StudentExitRepository studentExitRepository,
                                    AcademicStudentRepository academicStudentRepository,
                                    ExitFeeMonthsService exitFeeMonthsService,
                                    DueClearanceReceiptService receiptService,
                                    TcConfigService tcConfigService,
                                    UserService userService) {
        this.studentExitRepository = studentExitRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.exitFeeMonthsService = exitFeeMonthsService;
        this.receiptService = receiptService;
        this.tcConfigService = tcConfigService;
        this.userService = userService;
    }

    /** A save that must be refused: the message for the user and the exit to return to. */
    public static final class Rejection {
        private final String message;
        private final UUID exitUuid;

        Rejection(String message, UUID exitUuid) {
            this.message = message;
            this.exitUuid = exitUuid;
        }

        public String getMessage() {
            return message;
        }

        public UUID getExitUuid() {
            return exitUuid;
        }
    }

    /**
     * The enrollment of a struck-off student for the fee page lookup, or null. Only for an APPROVED exit
     * with fixed dues in the current session; null in every other case (including any failure).
     */
    @Transactional(readOnly = true)
    public AcademicStudent findCollectable(Long academicStudentId, School school, AcademicYear year) {
        try {
            StudentExit exit = collectableExit(academicStudentId, school, year);
            if (exit == null) {
                return null;
            }
            AcademicStudent as = academicStudentRepository.findById(academicStudentId).orElse(null);
            if (as == null || as.getSchool() == null || !school.getId().equals(as.getSchool().getId())) {
                return null;
            }
            return as;
        } catch (Exception e) {
            log.warn("Fee lookup for an exit student failed (treated as not found): academicStudentId={}", academicStudentId, e);
            return null;
        }
    }

    /** The enrollment id the fee page should preselect for an exit the user opened, or null when it cannot be collected. */
    @Transactional(readOnly = true)
    public Long preselectAcademicStudentId(UUID exitUuid, School school, AcademicYear year) {
        try {
            if (exitUuid == null || school == null || year == null || !tcConfigService.isEnabled(school)) {
                return null;
            }
            StudentExit exit = studentExitRepository.findByUuidAndSchool_Id(exitUuid, school.getId()).orElse(null);
            if (exit == null) {
                return null;
            }
            Long id = exit.getAcademicStudent().getId();
            return collectableExit(id, school, year) == null ? null : id;
        } catch (Exception e) {
            log.warn("Could not preselect the exit student on the fee page: exit={}", exitUuid, e);
            return null;
        }
    }

    /** The months still to pay for an exit, comma separated ("April, May"), or an empty string. Never throws. */
    // Not transactional on purpose: the panel call may throw for an unknown exit, and that must not mark a
    // surrounding transaction rollback-only.
    public String outstandingMonths(UUID exitUuid, School school, AcademicYear year) {
        try {
            return receiptService.panel(exitUuid, school, year).getOutstandingMonths();
        } catch (Exception e) {
            log.warn("Could not read the outstanding months of exit {}", exitUuid, e);
            return "";
        }
    }

    /**
     * Checks a fee form before it is saved. Returns null when it may be saved (always, for an ordinary
     * student). For a struck-off student: months after the cut-off month are refused, and so is a part
     * payment (the balance must be 0).
     */
    @Transactional(readOnly = true)
    public Rejection validateFeeSubmission(Map<String, String[]> params, School school, AcademicYear year) {
        try {
            Long id = parseLong(first(params, "academicStudent.id"));
            StudentExit exit = collectableExit(id, school, year);
            if (exit == null) {
                return null;
            }
            Optional<List<String>> allowed = exitFeeMonthsService.allowedMonths(exit);
            String[] months = params.get("months");
            if (allowed.isPresent() && months != null) {
                Set<String> ok = allowed.get().stream().map(String::toLowerCase).collect(Collectors.toSet());
                List<String> bad = new ArrayList<>();
                for (String m : months) {
                    if (m != null && !ok.contains(m.trim().toLowerCase())) {
                        bad.add(m.trim());
                    }
                }
                if (!bad.isEmpty()) {
                    List<String> list = allowed.get();
                    String upTo = list.isEmpty() ? "no month" : list.get(list.size() - 1);
                    return new Rejection("This student has left school. Fee can be collected only up to "
                            + upTo + ". Not allowed: " + String.join(", ", bad) + ".", exit.getUuid());
                }
            }
            BigDecimal balance = parseMoney(first(params, "balanceAmount"));
            if (balance != null && balance.signum() > 0) {
                return new Rejection("This student has left school, so the dues must be paid in full. "
                        + "The balance amount must be 0.", exit.getUuid());
            }
            return null;
        } catch (Exception e) {
            log.warn("Exit fee validation failed (the fee is allowed to continue)", e);
            return null;
        }
    }

    /**
     * Creates the Due Clearance Receipt for a fee that was just saved, when it belongs to an approved
     * same-session exit. Returns null for an ordinary fee or when the receipt could not be created (logged;
     * the fee itself is already saved, and the exit page can create the missing receipt). Not transactional
     * on purpose, so a failure here cannot affect the saved fee.
     */
    public DueClearanceReceipt afterFeeSaved(Object feeSubmissionId, School school, AcademicYear year) {
        try {
            if (!(feeSubmissionId instanceof Number) || school == null || year == null
                    || !tcConfigService.isEnabled(school)) {
                return null;
            }
            UserEntity user = userService.getLoggedInUser();
            if (user == null) {
                return null;
            }
            return receiptService.createFeeReceipt(((Number) feeSubmissionId).longValue(), school, year, user);
        } catch (Exception e) {
            log.error("Fee saved but the Due Clearance Receipt could not be created: feeSubmissionId={}", feeSubmissionId, e);
            return null;
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** The APPROVED, dues-fixed exit of this enrollment in the current session, or null (also when the feature is off). */
    private StudentExit collectableExit(Long academicStudentId, School school, AcademicYear year) {
        if (academicStudentId == null || school == null || year == null || !tcConfigService.isEnabled(school)) {
            return null;
        }
        StudentExit exit = studentExitRepository.findFirstByAcademicStudent_IdAndSchool_IdAndStatus(
                academicStudentId, school.getId(), ExitStatus.APPROVED).orElse(null);
        if (exit == null || !exit.isDuesFrozen() || !exit.getAcademicYear().getId().equals(year.getId())) {
            return null;
        }
        return exit;
    }

    private static String first(Map<String, String[]> params, String key) {
        String[] v = params == null ? null : params.get(key);
        return v == null || v.length == 0 ? null : v[0];
    }

    private static Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseMoney(String v) {
        try {
            return v == null || v.isBlank() ? null : new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
