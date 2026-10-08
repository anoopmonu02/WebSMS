package com.smsweb.sms.services.tc;

import com.smsweb.sms.dto.tc.ReRegisterInfo;
import com.smsweb.sms.dto.tc.ReRegisterRowDto;
import com.smsweb.sms.dto.tc.ReRegistrationResult;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.StudentLink;
import com.smsweb.sms.models.tc.TransferCertificate;
import com.smsweb.sms.models.tc.enums.AdmissionBasis;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.tc.enums.LinkType;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.models.tc.enums.TcStatus;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.StudentLinkRepository;
import com.smsweb.sms.repositories.tc.StudentLookupRepository;
import com.smsweb.sms.repositories.tc.TransferCertificateRepository;
import com.smsweb.sms.services.student.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registers a student again in a later session after the school issued a TC (plan section 6.7).
 *
 * <p>Rules (decisions D15 and D16): only an exit whose TC was issued ({@code TC_COLLECTED}) can be
 * continued, and only in a session later than the one in which the student left. A student who left
 * without a TC must first be issued one. Issuing a TC already requires the dues to be cleared and a
 * receipt cannot be voided once a valid TC exists, so a TC-issued exit has no unpaid frozen dues.</p>
 *
 * <p>The new record is created by the existing {@link StudentService#saveStudent}, so it gets a new
 * registration number, login, PSRN, enrollment and health row exactly like a normal registration.
 * Everything (the save, the link, the exit status and the timeline rows) happens in one transaction;
 * the exit row is locked first, so posting the form twice cannot create two records. The old record
 * and its login stay as they are (Inactive, login off).</p>
 */
@Service
public class ReRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ReRegistrationService.class);

    private static final int SEARCH_LIMIT = 25;
    private static final String REGISTRATION_PREFIX_FORMAT = "ddMMyyyyhhmmss";
    private static final Set<ExitStatus> OPEN = EnumSet.of(ExitStatus.EXITED, ExitStatus.APPROVED);

    private final StudentExitRepository studentExitRepository;
    private final TransferCertificateRepository transferCertificateRepository;
    private final StudentLinkRepository studentLinkRepository;
    private final StudentLookupRepository studentLookupRepository;
    private final StudentService studentService;
    private final StudentEventService studentEventService;

    public ReRegistrationService(StudentExitRepository studentExitRepository,
                                 TransferCertificateRepository transferCertificateRepository,
                                 StudentLinkRepository studentLinkRepository,
                                 StudentLookupRepository studentLookupRepository,
                                 StudentService studentService,
                                 StudentEventService studentEventService) {
        this.studentExitRepository = studentExitRepository;
        this.transferCertificateRepository = transferCertificateRepository;
        this.studentLinkRepository = studentLinkRepository;
        this.studentLookupRepository = studentLookupRepository;
        this.studentService = studentService;
        this.studentEventService = studentEventService;
    }

    // ───────────────────────────── search ─────────────────────────────

    /** Exits matching the typed text, each with whether the student can be registered again now. */
    @Transactional(readOnly = true)
    public List<ReRegisterRowDto> search(String text, School school, AcademicYear currentYear) {
        String cleaned = text == null ? "" : text.replace("%", "").replace("_", "").trim().toLowerCase();
        if (cleaned.length() < 2 || school == null || currentYear == null) {
            return List.of();
        }
        List<StudentExit> found = studentExitRepository.searchForReRegistration(
                school.getId(), ExitStatus.CANCELLED, "%" + cleaned + "%", PageRequest.of(0, SEARCH_LIMIT));
        List<ReRegisterRowDto> rows = new ArrayList<>();
        for (StudentExit e : found) {
            TransferCertificate tc = e.getStatus() == ExitStatus.TC_COLLECTED
                    ? transferCertificateRepository.findFirstByExit_IdAndStatus(e.getId(), TcStatus.VALID).orElse(null)
                    : null;
            String block = blockReason(e, currentYear, tc);
            Student s = e.getStudent();
            rows.add(new ReRegisterRowDto(e.getUuid(), s.getUuid(), s.getStudentName(), s.getFatherName(),
                    s.getMotherName(), s.getRegistrationNo(), s.getPsrn(), e.getAcademicStudent().getClassSrNo(),
                    e.getGrade().getGradeName() + " " + e.getSection().getSectionName(),
                    e.getAcademicYear().getSessionFormat(), e.getExitDate(), e.getStatus().getLabel(),
                    tc == null ? null : tc.getTcNumber(), tc == null ? null : tc.getIssueDate(),
                    block == null, block));
        }
        return rows;
    }

    // ───────────────────────────── the pre-filled form ─────────────────────────────

    /** What the form page shows: whose record is continued. Throws when the student cannot be registered again. */
    @Transactional(readOnly = true)
    public ReRegisterInfo info(UUID exitUuid, School school, AcademicYear currentYear) {
        StudentExit exit = readExit(exitUuid, school);
        TransferCertificate tc = validTc(exit);
        requireAllowed(exit, currentYear, tc);
        return new ReRegisterInfo(exit.getUuid(), exit.getStudent().getStudentName(), exit.getStudent().getRegistrationNo(),
                exit.getGrade().getGradeName() + " " + exit.getSection().getSectionName(),
                exit.getAcademicYear().getSessionFormat(), tc.getTcNumber(), tc.getIssueDate());
    }

    /**
     * A new, unsaved {@link Student} filled from the old record, to pre-fill the registration form. It is
     * never saved: the form posts a fresh Student. Not copied on purpose: registration number, login,
     * PSRN, photo (so changing one record's photo can never break the other), remark, height and weight.
     */
    @Transactional(readOnly = true)
    public Student prefill(UUID exitUuid, School school, AcademicYear currentYear) {
        StudentExit exit = readExit(exitUuid, school);
        requireAllowed(exit, currentYear, validTc(exit));
        Student old = exit.getStudent();

        Student n = new Student();
        UserEntity login = new UserEntity();
        if (old.getUserEntity() != null) {
            login.setEmail(old.getUserEntity().getEmail());
        }
        n.setUserEntity(login);
        n.setSchool(school);
        n.setAcademicYear(currentYear);

        n.setStudentName(old.getStudentName());
        n.setDob(old.getDob());
        n.setFatherName(old.getFatherName());
        n.setFatherOccupation(old.getFatherOccupation());
        n.setFatherQualification(old.getFatherQualification());
        n.setMotherName(old.getMotherName());
        n.setMotherOccupation(old.getMotherOccupation());
        n.setMotherQualification(old.getMotherQualification());
        n.setReligion(old.getReligion());
        n.setNationality(old.getNationality());
        n.setGender(old.getGender());
        n.setCategory(old.getCategory());
        n.setCast(old.getCast());
        n.setDescription(old.getDescription());
        n.setBloodGroup(old.getBloodGroup());
        n.setBodyType(old.getBodyType());
        n.setAddress(old.getAddress());
        n.setLandmark(old.getLandmark());
        n.setProvince(old.getProvince());
        n.setCity(old.getCity());
        n.setPincode(old.getPincode());
        n.setMobile1(old.getMobile1());
        n.setMobile2(old.getMobile2());
        n.setDistanceFromSchool(old.getDistanceFromSchool());
        n.setPreviousSchool(old.getPreviousSchool());
        n.setPreviousClass(old.getPreviousClass());
        n.setTcNo(old.getTcNo());
        n.setRemovalCause(old.getRemovalCause());
        n.setPassingYear(old.getPassingYear());
        n.setPersonName(old.getPersonName());
        n.setPersonContact(old.getPersonContact());
        n.setRelationship(old.getRelationship());
        n.setGrade(old.getGrade());
        n.setSection(old.getSection());
        n.setMedium(old.getMedium());
        n.setStudentType(old.getStudentType());
        n.setSchoolStatus(old.getSchoolStatus());
        n.setBank(old.getBank());
        n.setBranchName(old.getBranchName());
        n.setIfscCode(old.getIfscCode());
        n.setAccountNo(old.getAccountNo());
        n.setAadharNo(old.getAadharNo());
        n.setApaarId(old.getApaarId());
        n.setPenNo(old.getPenNo());
        return n;
    }

    // ───────────────────────────── save ─────────────────────────────

    /**
     * Saves the new registration and links it to the old record. {@code student} is the form-bound
     * Student; the school, session and every field that must come from the server are set here.
     *
     * @throws TcValidationException for every rule violation or failed save (message is safe to show)
     */
    @Transactional
    public ReRegistrationResult reRegister(Student student, MultipartFile photo, UUID exitUuid, School school,
                                           AcademicYear currentYear, UserEntity user) {
        log.info("Inside reRegister: exit={}", exitUuid);
        if (school == null) {
            throw new TcValidationException("No school is selected for this login.");
        }
        if (currentYear == null) {
            throw new TcValidationException("No active session was found.");
        }
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
        if (student == null || student.getId() != null) {
            throw new TcValidationException("This form can only register a new student record.");
        }
        if (exitUuid == null) {
            throw new TcValidationException("Choose the student to register again from the search page.");
        }

        // First lock in the order: exit row (a double post waits here and then finds RE_REGISTERED).
        StudentExit exit = studentExitRepository.findByUuidForUpdate(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
        TransferCertificate tc = validTc(exit);
        requireAllowed(exit, currentYear, tc);

        Student old = exit.getStudent();
        AcademicStudent oldEnrollment = exit.getAcademicStudent();

        student.setSchool(school);
        student.setAcademicYear(currentYear);
        student.setPic(null);

        Student saved;
        try {
            String prefix = new SimpleDateFormat(REGISTRATION_PREFIX_FORMAT).format(new Date());
            saved = studentService.saveStudent(student, photo, prefix, null);
        } catch (IOException e) {
            throw new TcValidationException("The photo could not be read. Please choose it again.");
        }
        // saveStudent swallows its own errors and returns null (known weakness C4): never trust a silent failure.
        if (saved == null || saved.getId() == null) {
            throw new TcValidationException("The student could not be saved. Check the details and the photo "
                    + "(JPG or PNG, up to 2 MB) and try again.");
        }
        AcademicStudent newEnrollment = studentLookupRepository
                .findEnrollmentsOfStudentInYear(saved.getId(), currentYear.getId(), school.getId())
                .stream().findFirst()
                .orElseThrow(() -> new TcValidationException("The new enrollment was not created. Nothing was saved."));

        StudentLink link = new StudentLink();
        link.setSchool(school);
        link.setOldStudent(old);
        link.setNewStudent(saved);
        link.setOldAcademicStudent(oldEnrollment);
        link.setNewAcademicStudent(newEnrollment);
        link.setLinkType(LinkType.RE_REGISTRATION);
        link.setOldRegistrationNo(old.getRegistrationNo());
        link.setNewRegistrationNo(saved.getRegistrationNo());
        link.setOldPsrn(old.getPsrn());
        link.setNewPsrn(saved.getPsrn());
        link.setOldClassSrNo(oldEnrollment.getClassSrNo());
        link.setTc(tc);
        link.setAdmissionBasis(AdmissionBasis.TC);
        link.setCreatedBy(user);
        link = studentLinkRepository.saveAndFlush(link);

        exit.setStatus(ExitStatus.RE_REGISTERED);
        exit.setUpdatedBy(user);

        LocalDate today = LocalDate.now();
        String oldSession = exit.getAcademicYear() == null ? null : exit.getAcademicYear().getSessionFormat();
        String previous = "Previous record: registration no. " + nz(old.getRegistrationNo())
                + ", PSRN " + nz(old.getPsrn()) + ", last SR no. " + nz(oldEnrollment.getClassSrNo())
                + ", class " + classLabel(oldEnrollment) + ", session " + nz(oldSession);
        // New record: where the student came from, with every old number, so the chain can be read from one row.
        studentEventService.log(EventType.RE_REGISTERED, saved, newEnrollment, currentYear, today,
                old.getRegistrationNo(), saved.getRegistrationNo(),
                "Registered again after TC " + tc.getTcNumber() + ". " + previous + ". New PSRN " + nz(saved.getPsrn()),
                RefType.LINK, link.getId(), EventSource.WEB, user);
        studentEventService.log(EventType.REGISTRATION_NO_ASSIGNED, saved, newEnrollment, currentYear, today,
                null, saved.getRegistrationNo(), "New registration number. PSRN " + nz(saved.getPsrn()),
                RefType.LINK, link.getId(), EventSource.WEB, user);
        studentEventService.log(EventType.ADMISSION_TC_SUBMITTED, saved, newEnrollment, currentYear, today,
                null, String.valueOf(tc.getTcNumber()), "TC issued by this school", RefType.TC, tc.getId(),
                EventSource.WEB, user);
        // Old record: points forward to the new one, so the old timeline also shows the hand-over.
        studentEventService.log(EventType.RE_REGISTERED, old, oldEnrollment, currentYear, today,
                old.getRegistrationNo(), saved.getRegistrationNo(),
                "Re-registered as a new record: registration no. " + nz(saved.getRegistrationNo())
                        + ", PSRN " + nz(saved.getPsrn()) + ", session " + currentYear.getSessionFormat()
                        + ". This record stays inactive",
                RefType.LINK, link.getId(), EventSource.WEB, user);
        return new ReRegistrationResult(saved.getUuid(), saved.getRegistrationNo());
    }

    private static String nz(Object v) {
        return v == null || String.valueOf(v).trim().isEmpty() ? "not set" : String.valueOf(v);
    }

    private static String classLabel(AcademicStudent e) {
        if (e == null || e.getGrade() == null) {
            return "not set";
        }
        String grade = e.getGrade().getGradeName();
        return e.getSection() == null ? grade : grade + " " + e.getSection().getSectionName();
    }

    // ───────────────────────────── rules ─────────────────────────────

    private StudentExit readExit(UUID exitUuid, School school) {
        if (exitUuid == null || school == null) {
            throw new TcValidationException("This exit record was not found.");
        }
        return studentExitRepository.findByUuidAndSchool_Id(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
    }

    private TransferCertificate validTc(StudentExit exit) {
        return transferCertificateRepository.findFirstByExit_IdAndStatus(exit.getId(), TcStatus.VALID).orElse(null);
    }

    private void requireAllowed(StudentExit exit, AcademicYear currentYear, TransferCertificate tc) {
        String block = blockReason(exit, currentYear, tc);
        if (block != null) {
            throw new InvalidExitStateException(block);
        }
    }

    /** Null when the student may be registered again now, otherwise the reason (safe to show). */
    private String blockReason(StudentExit exit, AcademicYear currentYear, TransferCertificate validTc) {
        switch (exit.getStatus()) {
            case RE_REGISTERED:
                return "Already registered again in a later session.";
            case CANCELLED:
                return "This strike-off was cancelled.";
            case REACTIVATED:
                return "This student was reactivated in the same session.";
            case EXITED:
                return "Waiting for approval. A TC must be issued before the student can be registered again.";
            case APPROVED:
                return "No TC has been issued. Issue the TC first, then register the student again.";
            case TC_COLLECTED:
                break;
            default:
                return "This exit cannot be continued.";
        }
        if (validTc == null) {
            return "No valid TC was found for this exit.";
        }
        if (exit.getAcademicYear().getId().equals(currentYear.getId())) {
            return "Same session: an admin can reactivate the student instead (Exited Students, Reactivate).";
        }
        if (exit.getAcademicYear().getStartDate() != null && currentYear.getStartDate() != null
                && !exit.getAcademicYear().getStartDate().isBefore(currentYear.getStartDate())) {
            return "The current session is not later than the session in which the student left.";
        }
        Student old = exit.getStudent();
        AcademicStudent enrollment = exit.getAcademicStudent();
        if (Student.STATUS_ACTIVE.equalsIgnoreCase(old.getStatus())
                || AcademicStudent.STATUS_ACTIVE.equalsIgnoreCase(enrollment.getStatus())) {
            return "This student is active again, so there is nothing to register.";
        }
        Optional<StudentExit> otherOpen = studentExitRepository.findFirstByStudent_IdAndSchool_IdAndStatusIn(
                old.getId(), exit.getSchool().getId(), OPEN);
        if (otherOpen.isPresent()) {
            return "This student has another strike-off that is still open. Settle it first.";
        }
        return null;
    }
}
