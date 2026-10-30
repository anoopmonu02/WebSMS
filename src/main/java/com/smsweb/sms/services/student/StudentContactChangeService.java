package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.ContactChangeResult;
import com.smsweb.sms.dto.ContactChangeResult.Outcome;
import com.smsweb.sms.dto.ContactFamilyChildRow;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentContactChangeLog;
import com.smsweb.sms.repositories.student.FamilyAccountRepository;
import com.smsweb.sms.repositories.student.FamilyMappingQueryRepository;
import com.smsweb.sms.repositories.student.StudentContactChangeLogRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import com.smsweb.sms.services.mobile.MobileRefreshTokenService;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fee Submission "Update Contact" (mobile1) with safety checks, a confirmation step and an audit row.
 *
 * check(): read only, tells the screen what will happen (nothing is changed).
 * apply(): re-runs every check inside one transaction, then updates mobile1, links the family
 *          account (FamilyAccountService.createIfAbsent), revokes the child's app sessions when
 *          the family link changed, and writes the audit row. Any failure rolls all of it back.
 *
 * The old family account is left ACTIVE (it may simply have no children left).
 * StudentService.updateContact is not used or changed.
 */
@Service
public class StudentContactChangeService {

    private static final Logger log = LoggerFactory.getLogger(StudentContactChangeService.class);
    private static final String SOURCE = "FEE_SCREEN";

    private final StudentRepository studentRepository;
    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMappingQueryRepository queryRepository;
    private final StudentContactChangeLogRepository logRepository;
    private final FamilyAccountService familyAccountService;
    private final MobileRefreshTokenService refreshTokenService;
    private final UserService userService;

    public StudentContactChangeService(StudentRepository studentRepository,
                                       FamilyAccountRepository familyAccountRepository,
                                       FamilyMappingQueryRepository queryRepository,
                                       StudentContactChangeLogRepository logRepository,
                                       FamilyAccountService familyAccountService,
                                       MobileRefreshTokenService refreshTokenService,
                                       UserService userService) {
        this.studentRepository = studentRepository;
        this.familyAccountRepository = familyAccountRepository;
        this.queryRepository = queryRepository;
        this.logRepository = logRepository;
        this.familyAccountService = familyAccountService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    /** What would happen? Changes nothing. */
    @Transactional(readOnly = true)
    public ContactChangeResult check(Long studentId, String rawMobile, Long schoolId, Long academicYearId) {
        return evaluate(studentId, rawMobile, schoolId, academicYearId).result;
    }

    /** Re-checks everything, then makes the change. Returns APPLIED, or BLOCKED / NO_CHANGE untouched. */
    @Transactional
    public ContactChangeResult apply(Long studentId, String rawMobile, Long schoolId, Long academicYearId) {
        Evaluation ev = evaluate(studentId, rawMobile, schoolId, academicYearId);
        Outcome o = ev.result.getOutcome();
        if (o == Outcome.BLOCKED || o == Outcome.NO_CHANGE) {
            return ev.result;
        }

        Student student = ev.student;
        String mobile = ev.mobile;
        String oldMobile = student.getMobile1();
        Long oldFamilyId = student.getFamilyAccount() != null ? student.getFamilyAccount().getId() : null;

        student.setMobile1(mobile);
        student.setUpdatedBy(userService.getLoggedInUser());
        studentRepository.save(student);

        // Finds or creates the account for the new number and links every student with that mobile1.
        FamilyAccount newAccount = familyAccountService.createIfAbsent(mobile);
        Long newFamilyId = newAccount.getId();

        boolean revoked = false;
        if (oldFamilyId != null && !oldFamilyId.equals(newFamilyId)) {
            List<Long> enrollmentIds = queryRepository.findAcademicStudentIds(student.getId());
            refreshTokenService.revokeAllForFamily(enrollmentIds);
            revoked = true;
        }

        StudentContactChangeLog entry = new StudentContactChangeLog();
        entry.setStudentId(student.getId());
        entry.setSchoolId(schoolId);
        entry.setAcademicYearId(academicYearId);
        entry.setOldMobile(oldMobile);
        entry.setNewMobile(mobile);
        entry.setOldFamilyAccountId(oldFamilyId);
        entry.setNewFamilyAccountId(newFamilyId);
        entry.setSessionsRevoked(revoked);
        entry.setSource(SOURCE);
        entry.setChangedBy(currentUsername());
        logRepository.save(entry);

        log.info("Contact changed: studentId={}, oldFamilyId={}, newFamilyId={}, sessionsRevoked={}, by={}",
                student.getId(), oldFamilyId, newFamilyId, revoked, entry.getChangedBy());

        return ContactChangeResult.of(Outcome.APPLIED,
                "Contact number " + mobile + " updated successfully for " + student.getStudentName() + ".");
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static final class Evaluation {
        ContactChangeResult result;
        Student student;
        String mobile;
    }

    private Evaluation evaluate(Long studentId, String rawMobile, Long schoolId, Long academicYearId) {
        Evaluation ev = new Evaluation();

        String mobile = rawMobile == null ? "" : rawMobile.trim();
        if (!mobile.matches("^[0-9]{10}$")) {
            ev.result = ContactChangeResult.of(Outcome.BLOCKED,
                    "Mobile number is required and must be exactly 10 digits.");
            return ev;
        }
        if (studentId == null || schoolId == null || academicYearId == null) {
            ev.result = ContactChangeResult.of(Outcome.BLOCKED,
                    "Student, school or session is missing. Please reselect the student.");
            return ev;
        }
        // Scope: only an Active student of the logged-in school and session may be changed here.
        if (queryRepository.countActiveEnrollment(studentId, schoolId, academicYearId) == 0) {
            ev.result = ContactChangeResult.of(Outcome.BLOCKED,
                    "Student not found in the current school and session.");
            return ev;
        }
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            ev.result = ContactChangeResult.of(Outcome.BLOCKED,
                    "Student not found in the current school and session.");
            return ev;
        }
        ev.student = student;
        ev.mobile = mobile;
        String name = student.getStudentName();

        if (mobile.equals(student.getMobile1())) {
            ev.result = ContactChangeResult.of(Outcome.NO_CHANGE,
                    "This is already the contact number of " + name + ". No change made.");
            return ev;
        }

        Optional<FamilyAccount> existing = familyAccountRepository.findByMobile(mobile);
        if (existing.isPresent() && !"ACTIVE".equals(existing.get().getStatus())) {
            ev.result = ContactChangeResult.of(Outcome.BLOCKED,
                    "The parent app account for " + mobile + " is switched off (inactive), so this number "
                  + "cannot be used here. Please contact an administrator.");
            return ev;
        }

        Long accountId = existing.map(FamilyAccount::getId).orElse(-1L);
        List<ContactFamilyChildRow> rows = queryRepository.findFamilyChildren(studentId, mobile, accountId);

        Set<Long> distinct = new HashSet<>();
        Set<Long> inSchoolIds = new HashSet<>();
        ContactChangeResult r = new ContactChangeResult();
        for (ContactFamilyChildRow row : rows) {
            if (!distinct.add(row.getStudentId())) continue;
            if (schoolId.equals(row.getSchoolId())) {
                inSchoolIds.add(row.getStudentId());
                String cls = row.getGradeName() == null ? ""
                        : row.getSectionName() == null ? row.getGradeName()
                        : row.getGradeName() + "-" + row.getSectionName();
                r.getChildrenInSchool().add(row.getStudentName() + (cls.isEmpty() ? "" : " (" + cls + ")"));
            }
        }
        r.setFamilyChildCount(distinct.size());
        r.setOtherBranchCount(distinct.size() - inSchoolIds.size());

        String oldNote = "";
        if (student.getFamilyAccount() != null) {
            oldNote = " " + name + " will no longer see the old family's app account"
                    + (student.getMobile1() != null && !student.getMobile1().isBlank()
                        ? " (" + student.getMobile1() + ")" : "")
                    + ", and any app login of " + name + " will be signed out.";
        }

        if (distinct.isEmpty()) {
            r.setOutcome(Outcome.NEW_FAMILY);
            r.setMessage("No other child is registered on " + mobile + ". A new parent app account will be "
                    + "created for this number and " + name + " will be linked to it." + oldNote);
        } else {
            r.setOutcome(Outcome.EXISTING_FAMILY);
            r.setMessage(mobile + " already belongs to a family with " + distinct.size()
                    + (distinct.size() == 1 ? " child" : " children") + ". Moving " + name
                    + " to this number gives that family's parent app access to " + name + "'s data. "
                    + "Continue only if this parent is the same family." + oldNote);
        }
        ev.result = r;
        return ev;
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null ? a.getName() : null;
    }
}
