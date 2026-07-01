package com.smsweb.sms.services.mobile;

import com.smsweb.sms.dto.FamilyGroupPreview;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.FamilyAccountRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Manages FamilyAccount records — one per unique parent mobile number.
 *
 * DATA MODEL:
 *   family_accounts  →  stores mobile + BCrypt password only
 *   students.family_account_id (FK)  →  links each student to their FamilyAccount
 *
 * This means:
 *   - All siblings share ONE FamilyAccount (same mobile → same FK)
 *   - Password change updates one row — all children covered automatically
 *   - Child lookup at login time: findAllByFamilyAccount(account)
 *
 * Default password on creation:  UA@<last4ofmobile>
 *   e.g.  mobile 9876543210  →  UA@3210
 * mustChangePassword = true forces parent to set their own password on first login.
 */
@Service
public class FamilyAccountService {
    private static final Logger log = LoggerFactory.getLogger(FamilyAccountService.class);


    private final FamilyAccountRepository  repo;
    private final StudentRepository        studentRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final PasswordEncoder          encoder;

    public FamilyAccountService(FamilyAccountRepository repo,
                                 StudentRepository studentRepository,
                                 AcademicStudentRepository academicStudentRepository,
                                 PasswordEncoder encoder) {
        this.repo                     = repo;
        this.studentRepository        = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.encoder                  = encoder;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Optional<FamilyAccount> findActive(String mobile) {
        log.info("Inside findActive");
        return repo.findByMobileAndStatus(mobile, "ACTIVE");
    }

    public Optional<FamilyAccount> findByMobile(String mobile) {
        log.info("Inside findByMobile");
        return repo.findByMobile(mobile);
    }

    // ── Password verification ─────────────────────────────────────────────────

    public boolean verifyPassword(FamilyAccount account, String rawPassword) {
        log.info("Inside verifyPassword");
        return encoder.matches(rawPassword, account.getPasswordHash());
    }

    // ── Creation (called at student registration + migration) ─────────────────

    /**
     * Finds or creates a FamilyAccount for the given mobile, then links
     * all students with that mobile1 to it via FK.
     *
     * Safe to call multiple times — idempotent.
     * Default password: UA@<last4digits>
     */
    @Transactional
    public FamilyAccount createIfAbsent(String mobile) {
        log.info("Inside createIfAbsent");
        // 1. Find or create the FamilyAccount
        FamilyAccount account = repo.findByMobile(mobile).orElseGet(() -> {
            String last4 = mobile.length() >= 4
                    ? mobile.substring(mobile.length() - 4)
                    : mobile;
            return repo.save(FamilyAccount.builder()
                    .mobile(mobile)
                    .passwordHash(encoder.encode("UA@" + last4))
                    .mustChangePassword(true)
                    .status("ACTIVE")
                    .build());
        });

        // 2. Link students whose mobile1 = this mobile
        studentRepository.findAllByMobile1(mobile).forEach(s -> linkStudent(s, account));

        // 3. Also link students whose mobile2 = this mobile (cross-mobile2 family members)
        studentRepository.findAllByMobile2(mobile).forEach(s -> linkStudent(s, account));

        return account;
    }

    private void linkStudent(Student student, FamilyAccount account) {
        if (student.getFamilyAccount() == null ||
                !student.getFamilyAccount().getId().equals(account.getId())) {
            student.setFamilyAccount(account);
            studentRepository.save(student);
        }
    }

    // ── Migration scan ────────────────────────────────────────────────────────

    /**
     * Scans ALL active students with any mobile set, groups them by mobile number,
     * and returns a preview list for the admin review UI.
     * A student with both mobile1 and mobile2 can appear in two groups.
     */
    public List<FamilyGroupPreview> scanFamilyGroups() {
        log.info("Inside scanFamilyGroups");
        List<Student> allStudents = studentRepository.findAllActiveWithMobile();

        // Build map: mobile → list of (student, matchedVia)
        Map<String, List<Student[]>> mobileMap = new LinkedHashMap<>();
        for (Student s : allStudents) {
            if (s.getMobile1() != null && !s.getMobile1().isBlank()) {
                mobileMap.computeIfAbsent(s.getMobile1(), k -> new ArrayList<>())
                         .add(new Student[]{s});
            }
            if (s.getMobile2() != null && !s.getMobile2().isBlank()
                    && !s.getMobile2().equals(s.getMobile1())) {
                mobileMap.computeIfAbsent(s.getMobile2(), k -> new ArrayList<>())
                         .add(new Student[]{s});
            }
        }

        // Build academic student lookup: studentId → active AcademicStudent
        Map<Long, AcademicStudent> asMap = new HashMap<>();
        // We'll load lazily per student to avoid loading everything
        // For efficiency, build a simple name-based lookup
        List<FamilyGroupPreview> result = new ArrayList<>();

        for (Map.Entry<String, List<Student[]>> entry : mobileMap.entrySet()) {
            String mobile = entry.getKey();
            List<Student[]> rows = entry.getValue();

            boolean accountExists = repo.existsByMobile(mobile);
            FamilyGroupPreview group = new FamilyGroupPreview(mobile, accountExists);

            for (Student[] arr : rows) {
                Student s = arr[0];
                String matchedVia = (mobile.equals(s.getMobile1())) ? "mobile1" : "mobile2";
                boolean linked = s.getFamilyAccount() != null;

                // Try to get grade/section/school from active academic student
                String gradeName = "—", sectionName = "—", schoolName = "";
                try {
                    List<AcademicStudent> asList = academicStudentRepository.findAllByStudent_IdAndStatus(s.getId(), "Active");
                    if (!asList.isEmpty()) {
                        AcademicStudent as = asList.get(0);
                        gradeName   = as.getGrade()   != null ? as.getGrade().getGradeName()     : "—";
                        sectionName = as.getSection() != null ? as.getSection().getSectionName() : "—";
                        schoolName  = as.getSchool()  != null ? as.getSchool().getSchoolName()   : "";
                    }
                } catch (Exception ignored) {}

                group.getStudents().add(new FamilyGroupPreview.StudentRow(
                        s.getId(),
                        s.getStudentName(),
                        s.getFatherName(),
                        schoolName,
                        gradeName,
                        sectionName,
                        matchedVia,
                        linked
                ));
            }
            result.add(group);
        }

        // Sort: unlinked groups first, then by mobile
        result.sort(Comparator.comparingInt(FamilyGroupPreview::getNeedsLink).reversed()
                              .thenComparing(FamilyGroupPreview::getMobile));
        return result;
    }

    // ── Password change ───────────────────────────────────────────────────────

    /**
     * Changes the parent password. Validates current password first.
     * Clears mustChangePassword flag on success.
     * @return null on success, error message string on failure.
     */
    @Transactional
    public String changePassword(FamilyAccount account,
                                  String currentPassword,
                                  String newPassword) {
        log.info("Inside changePassword");
        if (!encoder.matches(currentPassword, account.getPasswordHash())) {
            return "Current password is incorrect.";
        }
        if (newPassword.length() < 6) {
            return "New password must be at least 6 characters.";
        }
        account.setPasswordHash(encoder.encode(newPassword));
        account.setMustChangePassword(false);
        repo.save(account);
        return null;
    }

    /**
     * Admin password reset — no current-password check.
     * Sets mustChangePassword = true so parent must change on next login.
     */
    @Transactional
    public void adminResetPassword(FamilyAccount account, String newRawPassword) {
        log.info("Inside adminResetPassword");
        account.setPasswordHash(encoder.encode(newRawPassword));
        account.setMustChangePassword(true);
        repo.save(account);
    }
}
