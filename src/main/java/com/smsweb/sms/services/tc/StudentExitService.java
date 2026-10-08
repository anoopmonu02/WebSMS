package com.smsweb.sms.services.tc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsweb.sms.dto.tc.ApproveRequest;
import com.smsweb.sms.dto.tc.DuesEstimate;
import com.smsweb.sms.dto.tc.ExitDetailDto;
import com.smsweb.sms.dto.tc.ExitFilter;
import com.smsweb.sms.dto.tc.ExitRowDto;
import com.smsweb.sms.dto.tc.ManualDues;
import com.smsweb.sms.dto.tc.StrikeOffRequest;
import com.smsweb.sms.dto.tc.StudentPickRow;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.StudentExecution;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.StudentLink;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.repositories.tc.DueClearanceReceiptRepository;
import com.smsweb.sms.repositories.tc.StudentExecutionRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.StudentLinkRepository;
import com.smsweb.sms.repositories.tc.StudentLookupRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.mobile.MobileRefreshTokenService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Strike-off, the admin's green flag (approval, which freezes the dues), cancelling a strike-off and
 * bringing a student back in the same session (reactivation after the TC was collected).
 *
 * <p>Every write runs in one transaction together with its timeline rows. Lock order is: exit row,
 * then school row, then class division row; strike-off first locks the student's enrollment so two
 * people cannot strike off the same student at once.</p>
 *
 * <p>Approve, cancel and reactivate are checked for the admin role here as well as on the controller, so a
 * mistake in one place cannot open them to other users.</p>
 */
@Service
public class StudentExitService {

    private static final Logger log = LoggerFactory.getLogger(StudentExitService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<ExitStatus> OPEN = EnumSet.of(ExitStatus.EXITED, ExitStatus.APPROVED);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final int MAX_REMARK = 500;
    private static final int MAX_REASON = 255;
    private static final int MIN_REASON = 5;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PICK_LIMIT = 20;

    private final StudentExitRepository studentExitRepository;
    private final StudentLookupRepository studentLookupRepository;
    private final DueClearanceReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ExitDuesService exitDuesService;
    private final StudentEventService studentEventService;
    private final MobileRefreshTokenService mobileRefreshTokenService;
    private final StudentExecutionRepository studentExecutionRepository;
    private final StudentLinkRepository studentLinkRepository;

    public StudentExitService(StudentExitRepository studentExitRepository,
                              StudentLookupRepository studentLookupRepository,
                              DueClearanceReceiptRepository receiptRepository,
                              UserRepository userRepository,
                              ExitDuesService exitDuesService,
                              StudentEventService studentEventService,
                              MobileRefreshTokenService mobileRefreshTokenService,
                              StudentExecutionRepository studentExecutionRepository,
                              StudentLinkRepository studentLinkRepository) {
        this.studentExitRepository = studentExitRepository;
        this.studentLookupRepository = studentLookupRepository;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.exitDuesService = exitDuesService;
        this.studentEventService = studentEventService;
        this.mobileRefreshTokenService = mobileRefreshTokenService;
        this.studentExecutionRepository = studentExecutionRepository;
        this.studentLinkRepository = studentLinkRepository;
    }

    // ───────────────────────────── strike-off ─────────────────────────────

    /**
     * Strikes a student off: saves the exit with a dues estimate, makes the enrollment and the
     * student Inactive, switches the login off and writes the timeline.
     *
     * @throws DuesCalculationException when the estimate fails and no admin amount was given
     * @throws TcValidationException    for every other rule violation (message is safe to show)
     */
    @Transactional
    public StudentExit strikeOff(StrikeOffRequest req, School school, AcademicYear academicYear, UserEntity user) {
        log.info("Inside strikeOff: academicStudentId={}", req == null ? null : req.getAcademicStudentId());
        requireContext(school, academicYear, user);
        validateStrikeOffInput(req, academicYear);

        AcademicStudent enrollment = studentLookupRepository
                .findByIdForUpdate(req.getAcademicStudentId(), school.getId())
                .orElseThrow(() -> new TcValidationException("The student was not found in this school."));
        Student student = enrollment.getStudent();

        if (enrollment.getAcademicYear() == null || !academicYear.getId().equals(enrollment.getAcademicYear().getId())) {
            throw new TcValidationException("Only a student of the current session can be struck off.");
        }
        if (!AcademicStudent.STATUS_ACTIVE.equalsIgnoreCase(enrollment.getStatus())
                || !Student.STATUS_ACTIVE.equalsIgnoreCase(student.getStatus())) {
            throw new TcValidationException("This student is not active, so cannot be struck off.");
        }
        if (studentExitRepository.findFirstByStudent_IdAndSchool_IdAndStatusIn(student.getId(), school.getId(), OPEN).isPresent()) {
            throw new TcValidationException("This student already has an open strike-off. Open it from Exited Students.");
        }

        // Dues estimate while the student is still Active (the fee calculator only sees Active students).
        DuesEstimate estimate = null;
        ManualDues manual = null;
        try {
            estimate = exitDuesService.estimate(enrollment, req.getExitDate(), req.isIncludeLastMonth(), school, academicYear);
        } catch (DuesCalculationException e) {
            if (!isAdmin()) {
                throw new DuesCalculationException(e.getMessage()
                        .replace(" An admin can enter the dues by hand.", "")
                        + " Please ask an admin to strike this student off.");
            }
            if (req.getManualAmount() == null) {
                throw e;
            }
            manual = new ManualDues(validateAmount(req.getManualAmount()), validateReason(req.getManualReason(), "reason for the manual amount"));
        }

        StudentExit exit = new StudentExit();
        exit.setSchool(school);
        exit.setAcademicYear(academicYear);
        exit.setStudent(student);
        exit.setAcademicStudent(enrollment);
        exit.setGrade(enrollment.getGrade());
        exit.setSection(enrollment.getSection());
        exit.setMedium(enrollment.getMedium());
        exit.setExitReason(req.getExitReason());
        exit.setAbsentDays(req.getExitReason() == ExitReason.LONG_ABSENCE ? req.getAbsentDays() : null);
        exit.setExitDate(req.getExitDate());
        exit.setIncludeLastMonth(req.isIncludeLastMonth());
        exit.setRemark(blankToNull(req.getRemark()));
        exit.setStatus(ExitStatus.EXITED);
        exit.setCreatedBy(user);
        if (estimate != null) {
            exit.setCalcAmount(estimate.amount());
            exit.setCalcBreakdown(estimate.breakdownJson());
        } else {
            exit.setCalcAmount(manual.amount());
            exit.setCalcBreakdown(manualBreakdown(manual, req.getExitDate(), req.isIncludeLastMonth()));
            exit.setDuesManual(true);
            exit.setDuesManualReason(manual.reason());
        }
        exit = studentExitRepository.saveAndFlush(exit);

        // Deactivate both records, the same two the existing Remove Student screen uses.
        enrollment.setStatus(AcademicStudent.STATUS_INACTIVE);
        student.setStatus(Student.STATUS_INACTIVE);

        boolean loginSwitchedOff = disableLogin(student, exit, enrollment.getId());

        LocalDate today = LocalDate.now();
        StudentExecution struck = studentEventService.log(EventType.STRUCK_OFF, student, enrollment, academicYear,
                req.getExitDate(), null, req.getExitReason().name(), exit.getRemark(),
                RefType.EXIT, exit.getId(), EventSource.WEB, user);
        struck.setExitReason(req.getExitReason());
        if (loginSwitchedOff) {
            studentEventService.log(EventType.LOGIN_DISABLED, student, enrollment, academicYear, today,
                    null, null, "Login switched off by strike-off", RefType.EXIT, exit.getId(), EventSource.WEB, user);
        }
        if (exit.isDuesManual()) {
            studentEventService.log(EventType.DUES_MANUAL, student, enrollment, academicYear, today,
                    null, exit.getCalcAmount().toPlainString(), exit.getDuesManualReason(),
                    RefType.EXIT, exit.getId(), EventSource.WEB, user);
        }
        return exit;
    }

    // ───────────────────────────── approve (green flag) ─────────────────────────────

    /**
     * Admin only. Confirms the dues (or replaces them with an amount typed by the admin) and freezes
     * them; the exit becomes APPROVED.
     */
    @Transactional
    public StudentExit approve(UUID exitUuid, ApproveRequest req, School school, UserEntity user) {
        log.info("Inside approve: exit={}", exitUuid);
        requireAdmin();
        if (school == null || user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
        StudentExit exit = lockExit(exitUuid, school);
        if (exit.getStatus() != ExitStatus.EXITED) {
            throw new InvalidExitStateException("Only an exit that is waiting for approval can be approved.");
        }

        ApproveRequest in = req == null ? new ApproveRequest() : req;
        boolean replaced = in.getManualAmount() != null;
        BigDecimal amount;
        String breakdown;
        if (replaced) {
            ManualDues manual = new ManualDues(validateAmount(in.getManualAmount()),
                    validateReason(in.getManualReason(), "reason for the changed amount"));
            amount = manual.amount();
            breakdown = manualBreakdown(manual, exit.getExitDate(), exit.isIncludeLastMonth());
            exit.setDuesManual(true);
            exit.setDuesManualReason(manual.reason());
        } else {
            amount = exit.getCalcAmount();
            breakdown = exit.getCalcBreakdown();
        }

        LocalDateTime now = LocalDateTime.now();
        exit.setDuesFrozen(true);
        exit.setFrozenAmount(amount);
        exit.setFrozenBreakdown(breakdown);
        exit.setFrozenAt(now);
        exit.setStatus(ExitStatus.APPROVED);
        exit.setApprovedBy(user);
        exit.setApprovedAt(now);
        exit.setApprovalRemark(cutAndCheck(in.getRemark(), MAX_REMARK, "Remark"));
        exit.setUpdatedBy(user);

        LocalDate today = LocalDate.now();
        studentEventService.log(EventType.TC_APPROVED, exit.getStudent(), exit.getAcademicStudent(), exit.getAcademicYear(),
                today, exit.getCalcAmount().toPlainString(), amount.toPlainString(), exit.getApprovalRemark(),
                RefType.EXIT, exit.getId(), EventSource.WEB, user);
        if (replaced) {
            studentEventService.log(EventType.DUES_MANUAL, exit.getStudent(), exit.getAcademicStudent(), exit.getAcademicYear(),
                    today, exit.getCalcAmount().toPlainString(), amount.toPlainString(), exit.getDuesManualReason(),
                    RefType.EXIT, exit.getId(), EventSource.WEB, user);
        }
        return exit;
    }

    // ───────────────────────────── cancel strike-off ─────────────────────────────

    /**
     * Admin only. Undoes a strike-off made by mistake, in the same session and before any TC is
     * issued: the student and the enrollment become Active again and the login is switched back on,
     * but only if strike-off was what switched it off.
     */
    @Transactional
    public StudentExit cancelStrikeOff(UUID exitUuid, String reason, School school, AcademicYear academicYear, UserEntity user) {
        log.info("Inside cancelStrikeOff: exit={}", exitUuid);
        requireAdmin();
        requireContext(school, academicYear, user);
        String why = validateReason(reason, "reason for cancelling");

        StudentExit exit = lockExit(exitUuid, school);
        if (!OPEN.contains(exit.getStatus())) {
            throw new InvalidExitStateException("This strike-off can no longer be cancelled.");
        }
        if (!academicYear.getId().equals(exit.getAcademicYear().getId())) {
            throw new InvalidExitStateException("A strike-off can be cancelled only in the session in which it was made.");
        }
        BigDecimal settled = receiptRepository.sumSettledByExit(exit.getId(), RecordStatus.VALID);
        if (settled != null && settled.compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidExitStateException("Money was already collected against this exit. Void the receipts first.");
        }

        AcademicStudent enrollment = exit.getAcademicStudent();
        Student student = exit.getStudent();
        ExitStatus previousStatus = exit.getStatus();
        enrollment.setStatus(AcademicStudent.STATUS_ACTIVE);
        student.setStatus(Student.STATUS_ACTIVE);
        boolean loginSwitchedOn = enableLoginIfWeDisabledIt(student, exit);

        exit.setStatus(ExitStatus.CANCELLED);
        exit.setCancelledBy(user);
        exit.setCancelledAt(LocalDateTime.now());
        exit.setCancelReason(why);
        exit.setUpdatedBy(user);

        LocalDate today = LocalDate.now();
        studentEventService.log(EventType.STRIKE_OFF_CANCELLED, student, enrollment, exit.getAcademicYear(), today,
                previousStatus.name(), ExitStatus.CANCELLED.name(), why, RefType.EXIT, exit.getId(), EventSource.WEB, user);
        if (loginSwitchedOn) {
            studentEventService.log(EventType.LOGIN_ENABLED, student, enrollment, exit.getAcademicYear(), today,
                    null, null, "Login switched back on: strike-off cancelled", RefType.EXIT, exit.getId(), EventSource.WEB, user);
        }
        return exit;
    }

    // ───────────────────────────── reactivate (same session) ─────────────────────────────

    /**
     * Admin only. The student comes back in the same session after the TC was collected (plan 6.6):
     * the enrollment and the student become Active again, the login is switched back on if strike-off
     * switched it off, and the exit becomes REACTIVATED.
     *
     * <p>Only the exit's own enrollment and student are touched. The student keeps the same
     * registration number, SR number and login. Months after the last date appear as normal dues on
     * the Fee Submission screen. The TC row is left as it is (history), and the exit's receipts stay.
     * Who did it and why is stored in the timeline row.</p>
     */
    @Transactional
    public StudentExit reactivateSameSession(UUID exitUuid, String reason, School school, AcademicYear academicYear, UserEntity user) {
        log.info("Inside reactivateSameSession: exit={}", exitUuid);
        requireAdmin();
        requireContext(school, academicYear, user);
        String why = validateReason(reason, "reason for reactivating");

        StudentExit exit = lockExit(exitUuid, school);
        if (exit.getStatus() != ExitStatus.TC_COLLECTED) {
            throw new InvalidExitStateException(exit.getStatus() == ExitStatus.EXITED || exit.getStatus() == ExitStatus.APPROVED
                    ? "No TC was issued for this exit. Use Cancel strike-off instead."
                    : "Only a student whose TC was collected can be reactivated here.");
        }
        if (!academicYear.getId().equals(exit.getAcademicYear().getId())) {
            throw new InvalidExitStateException("A student can be reactivated only in the session in which the strike-off was made. "
                    + "For a later session, register the student again.");
        }

        AcademicStudent enrollment = exit.getAcademicStudent();
        Student student = exit.getStudent();
        if (AcademicStudent.STATUS_ACTIVE.equalsIgnoreCase(enrollment.getStatus())
                && Student.STATUS_ACTIVE.equalsIgnoreCase(student.getStatus())) {
            throw new InvalidExitStateException("This student is already active.");
        }
        enrollment.setStatus(AcademicStudent.STATUS_ACTIVE);
        student.setStatus(Student.STATUS_ACTIVE);
        boolean loginSwitchedOn = enableLoginIfWeDisabledIt(student, exit);

        exit.setStatus(ExitStatus.REACTIVATED);
        exit.setUpdatedBy(user);

        LocalDate today = LocalDate.now();
        studentEventService.log(EventType.REACTIVATED, student, enrollment, exit.getAcademicYear(), today,
                ExitStatus.TC_COLLECTED.name(), ExitStatus.REACTIVATED.name(), why,
                RefType.EXIT, exit.getId(), EventSource.WEB, user);
        if (loginSwitchedOn) {
            studentEventService.log(EventType.LOGIN_ENABLED, student, enrollment, exit.getAcademicYear(), today,
                    null, null, "Login switched back on: student reactivated", RefType.EXIT, exit.getId(), EventSource.WEB, user);
        }
        return exit;
    }

    // ───────────────────────────── reads ─────────────────────────────

    /** Students who can be struck off in this session, matching the typed text (name, father, mother, SR or registration no). */
    @Transactional(readOnly = true)
    public List<StudentPickRow> searchCandidates(String text, School school, AcademicYear academicYear) {
        String cleaned = text == null ? "" : text.replace("%", "").replace("_", "").trim().toLowerCase();
        if (cleaned.length() < 2 || school == null || academicYear == null) {
            return List.of();
        }
        List<AcademicStudent> found = studentLookupRepository.searchStrikeOffCandidates(
                school.getId(), academicYear.getId(), "%" + cleaned + "%", OPEN, PageRequest.of(0, PICK_LIMIT));
        return found.stream().map(a -> new StudentPickRow(
                a.getId(),
                a.getStudent().getStudentName(),
                a.getStudent().getFatherName(),
                a.getStudent().getMotherName(),
                a.getGrade().getGradeName(),
                a.getSection().getSectionName(),
                a.getMedium().getMediumName(),
                a.getClassSrNo(),
                a.getStudent().getRegistrationNo())).collect(Collectors.toList());
    }

    /**
     * The picker line for one student (by Student id) when that student can be struck off now, otherwise
     * null. Lets the old Remove Student button open the strike-off form with the student already chosen.
     */
    @Transactional(readOnly = true)
    public StudentPickRow findCandidateByStudentId(Long studentId, School school, AcademicYear academicYear) {
        if (studentId == null || school == null || academicYear == null) {
            return null;
        }
        List<AcademicStudent> found = studentLookupRepository.findStrikeOffCandidateByStudentId(
                studentId, school.getId(), academicYear.getId(), OPEN);
        if (found.isEmpty()) {
            return null;
        }
        AcademicStudent a = found.get(0);
        return new StudentPickRow(a.getId(), a.getStudent().getStudentName(), a.getStudent().getFatherName(),
                a.getStudent().getMotherName(), a.getGrade().getGradeName(), a.getSection().getSectionName(),
                a.getMedium().getMediumName(), a.getClassSrNo(), a.getStudent().getRegistrationNo());
    }

    /** Dues preview for the strike-off form. Same calculation as strike-off, nothing is saved. */
    @Transactional(readOnly = true)
    public DuesEstimate previewDues(Long academicStudentId, LocalDate exitDate, boolean includeLastMonth,
                                    School school, AcademicYear academicYear) {
        if (academicStudentId == null || exitDate == null || school == null || academicYear == null) {
            throw new TcValidationException("Choose a student and a last date first.");
        }
        checkExitDate(exitDate, academicYear);
        AcademicStudent enrollment = studentLookupRepository.findDetailed(academicStudentId, school.getId())
                .orElseThrow(() -> new TcValidationException("The student was not found in this school."));
        return exitDuesService.estimate(enrollment, exitDate, includeLastMonth, school, academicYear);
    }

    @Transactional(readOnly = true)
    public Page<ExitRowDto> search(ExitFilter filter, School school) {
        ExitFilter f = filter == null ? new ExitFilter() : filter;
        int size = Math.min(Math.max(f.getSize(), 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(f.getPage(), 0), size, Sort.by(Sort.Direction.DESC, "id"));
        return studentExitRepository.findAll(filterSpec(f, school.getId()), pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public ExitDetailDto detail(UUID exitUuid, School school, AcademicYear currentYear) {
        StudentExit e = studentExitRepository.findByUuidAndSchool_Id(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
        Student s = e.getStudent();
        ExitDetailDto d = new ExitDetailDto();
        d.setUuid(e.getUuid());
        d.setStatus(e.getStatus());
        d.setStudentUuid(s.getUuid());
        d.setStudentName(s.getStudentName());
        d.setFatherName(s.getFatherName());
        d.setMotherName(s.getMotherName());
        d.setRegistrationNo(s.getRegistrationNo());
        d.setSrNo(e.getAcademicStudent().getClassSrNo());
        d.setClassName(e.getGrade().getGradeName());
        d.setSectionName(e.getSection().getSectionName());
        d.setMediumName(e.getMedium().getMediumName());
        d.setSessionName(e.getAcademicYear().getSessionFormat());
        d.setExitReason(e.getExitReason());
        d.setAbsentDays(e.getAbsentDays());
        d.setExitDate(e.getExitDate());
        d.setIncludeLastMonth(e.isIncludeLastMonth());
        d.setRemark(e.getRemark());
        d.setCreatedByName(e.getCreatedBy() == null ? null : e.getCreatedBy().getUsername());
        d.setCreationDate(e.getCreationDate());
        d.setLoginDisabledByExit(e.isLoginDisabledByExit());
        d.setCalcAmount(e.getCalcAmount());
        d.setCalcBreakdown(parseBreakdown(e.isDuesFrozen() && e.getFrozenBreakdown() != null
                ? e.getFrozenBreakdown() : e.getCalcBreakdown()));
        d.setDuesManual(e.isDuesManual());
        d.setDuesManualReason(e.getDuesManualReason());
        d.setDuesFrozen(e.isDuesFrozen());
        d.setFrozenAmount(e.getFrozenAmount());
        d.setFrozenAt(e.getFrozenAt());
        d.setApprovedByName(e.getApprovedBy() == null ? null : e.getApprovedBy().getUsername());
        d.setApprovedAt(e.getApprovedAt());
        d.setApprovalRemark(e.getApprovalRemark());
        d.setCancelledByName(e.getCancelledBy() == null ? null : e.getCancelledBy().getUsername());
        d.setCancelledAt(e.getCancelledAt());
        d.setCancelReason(e.getCancelReason());
        boolean admin = isAdmin();
        d.setCanApprove(admin && e.getStatus() == ExitStatus.EXITED);
        boolean sameSession = currentYear != null && currentYear.getId().equals(e.getAcademicYear().getId());
        d.setCanCancel(admin && OPEN.contains(e.getStatus()) && sameSession);
        d.setCanReactivate(admin && e.getStatus() == ExitStatus.TC_COLLECTED && sameSession);
        // The student may be registered again (any user with the screen) once the TC is collected and the session has moved on.
        d.setCanReRegister(e.getStatus() == ExitStatus.TC_COLLECTED && currentYear != null && !sameSession);
        if (e.getStatus() == ExitStatus.RE_REGISTERED) {
            for (StudentLink link : studentLinkRepository.findByOldStudent_Id(s.getId())) {
                if (link.getOldAcademicStudent().getId().equals(e.getAcademicStudent().getId())) {
                    d.setNewStudentUuid(link.getNewStudent().getUuid());
                    d.setNewRegistrationNo(link.getNewRegistrationNo());
                    break;
                }
            }
        }
        if (e.getStatus() == ExitStatus.REACTIVATED) {
            Optional<StudentExecution> row = studentExecutionRepository
                    .findFirstByRefTypeAndRefIdAndEventTypeOrderByIdDesc(RefType.EXIT, e.getId(), EventType.REACTIVATED);
            row.ifPresent(r -> {
                d.setReactivatedByName(r.getCreatedBy() == null ? null : r.getCreatedBy().getUsername());
                d.setReactivatedAt(r.getRecordedAt());
                d.setReactivateReason(r.getRemark());
            });
        }
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

    /**
     * Switches the student's login off when it is on, remembers that on the exit, and cuts the
     * mobile sessions. A login that was already off is left alone and not flagged.
     *
     * @return true when this call switched the login off
     */
    private boolean disableLogin(Student student, StudentExit exit, Long academicStudentId) {
        UserEntity login = student.getUserEntity();
        boolean switchedOff = false;
        if (login != null && !Boolean.FALSE.equals(login.getEnabled())) {
            login.setEnabled(false);
            userRepository.save(login);
            exit.setLoginDisabledByExit(true);
            switchedOff = true;
        }
        // Existing mobile sessions would otherwise keep working until the refresh token expires.
        mobileRefreshTokenService.revokeAllForStudent(academicStudentId);
        return switchedOff;
    }

    /** @return true when the login was switched back on */
    private boolean enableLoginIfWeDisabledIt(Student student, StudentExit exit) {
        if (!exit.isLoginDisabledByExit()) {
            return false;
        }
        UserEntity login = student.getUserEntity();
        if (login != null && Boolean.FALSE.equals(login.getEnabled())) {
            login.setEnabled(true);
            userRepository.save(login);
            return true;
        }
        return false;
    }

    private void validateStrikeOffInput(StrikeOffRequest req, AcademicYear academicYear) {
        if (req == null || req.getAcademicStudentId() == null) {
            throw new TcValidationException("Choose a student.");
        }
        if (req.getExitReason() == null) {
            throw new TcValidationException("Choose the reason for leaving.");
        }
        if (req.getExitDate() == null) {
            throw new TcValidationException("Choose the last date at school.");
        }
        checkExitDate(req.getExitDate(), academicYear);
        if (req.getExitReason() == ExitReason.LONG_ABSENCE) {
            Integer days = req.getAbsentDays();
            if (days == null || days < 1 || days > 3660) {
                throw new TcValidationException("Enter the number of days absent (1 or more).");
            }
        }
        cutAndCheck(req.getRemark(), MAX_REMARK, "Remark");
    }

    private void checkExitDate(LocalDate exitDate, AcademicYear academicYear) {
        if (exitDate.isAfter(LocalDate.now())) {
            throw new TcValidationException("The last date cannot be in the future.");
        }
        if (academicYear.getStartDate() != null && exitDate.isBefore(academicYear.getStartDate())) {
            throw new TcValidationException("The last date cannot be before the session start date.");
        }
    }

    private static void requireContext(School school, AcademicYear academicYear, UserEntity user) {
        if (school == null) {
            throw new TcValidationException("No school is selected for this login.");
        }
        if (academicYear == null) {
            throw new TcValidationException("No active session was found.");
        }
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
    }

    private static void requireAdmin() {
        if (!isAdmin()) {
            throw new TcValidationException("Only an admin can do this.");
        }
    }

    /** True for ROLE_ADMIN and ROLE_SUPERADMIN (the "principal" of the TC rules). */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    private static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new TcValidationException("The amount must be zero or more.");
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_AMOUNT) > 0) {
            throw new TcValidationException("The amount is too large.");
        }
        return rounded;
    }

    private static String validateReason(String reason, String label) {
        String text = blankToNull(reason);
        if (text == null || text.length() < MIN_REASON) {
            throw new TcValidationException("Please enter the " + label + " (at least " + MIN_REASON + " characters).");
        }
        if (text.length() > MAX_REASON) {
            throw new TcValidationException("The " + label + " is too long (most " + MAX_REASON + " characters).");
        }
        return text;
    }

    private static String cutAndCheck(String value, int max, String label) {
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

    private static String manualBreakdown(ManualDues manual, LocalDate exitDate, boolean includeLastMonth) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("total", manual.amount().toPlainString());
        json.put("manual", true);
        json.put("reason", manual.reason());
        json.put("lastDate", exitDate.toString());
        json.put("includeLastMonth", includeLastMonth);
        try {
            return MAPPER.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write the dues breakdown", e);
        }
    }

    private static Map<String, Object> parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            log.warn("Could not read a stored dues breakdown", e);
            return new LinkedHashMap<>();
        }
    }

    private ExitRowDto toRow(StudentExit e) {
        Student s = e.getStudent();
        boolean frozen = e.isDuesFrozen();
        return new ExitRowDto(e.getUuid(), s.getStudentName(), s.getFatherName(),
                e.getGrade().getGradeName(), e.getSection().getSectionName(), e.getMedium().getMediumName(),
                e.getAcademicStudent().getClassSrNo(), e.getExitReason(), e.getExitDate(),
                frozen ? e.getFrozenAmount() : e.getCalcAmount(), frozen, e.isDuesManual(), e.getStatus());
    }

    @SuppressWarnings("unchecked")
    private static Specification<StudentExit> filterSpec(ExitFilter f, Long schoolId) {
        return (root, query, cb) -> {
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<StudentExit, Student> student;
            if (countQuery) {
                student = root.join("student");
            } else {
                student = (Join<StudentExit, Student>) (Object) root.fetch("student");
                root.fetch("grade");
                root.fetch("section");
                root.fetch("medium");
                root.fetch("academicStudent");
            }
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("school").get("id"), schoolId));
            if (f.getStatus() != null) {
                p.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getReason() != null) {
                p.add(cb.equal(root.get("exitReason"), f.getReason()));
            }
            if (f.getGradeId() != null) {
                p.add(cb.equal(root.get("grade").get("id"), f.getGradeId()));
            }
            if (f.getFromDate() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("exitDate"), f.getFromDate()));
            }
            if (f.getToDate() != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("exitDate"), f.getToDate()));
            }
            String text = f.getQ() == null ? "" : f.getQ().replace("%", "").replace("_", "").trim().toLowerCase();
            if (!text.isEmpty()) {
                String like = "%" + text + "%";
                p.add(cb.or(
                        cb.like(cb.lower(student.<String>get("studentName")), like),
                        cb.like(cb.lower(student.<String>get("fatherName")), like),
                        cb.like(cb.lower(student.<String>get("motherName")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
