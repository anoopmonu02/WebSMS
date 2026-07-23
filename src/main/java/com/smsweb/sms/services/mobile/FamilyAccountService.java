package com.smsweb.sms.services.mobile;

import com.smsweb.sms.dto.FamilyGroupPreview;
import com.smsweb.sms.dto.MobileUserRowDto;
import com.smsweb.sms.dto.MobileUserStatsDto;
import com.smsweb.sms.models.mobile.MobileRefreshToken;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.FamilyAccountRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final MobileRefreshTokenService refreshTokenService;

    private static final SecureRandom RNG = new SecureRandom();
    // Excludes ambiguous characters (0/O, 1/l/I) so a temp password is easy to
    // read aloud or copy off a slip of paper without a parent mistyping it.
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    // Formats LocalDateTime server-side into a plain display string — deliberately
    // NOT sent as a raw ISO timestamp for the browser to re-parse via `new Date()`,
    // which would misinterpret an offset-less string as UTC and shift it by +5:30.
    private static final DateTimeFormatter LAST_ACTIVE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public FamilyAccountService(FamilyAccountRepository repo,
                                 StudentRepository studentRepository,
                                 AcademicStudentRepository academicStudentRepository,
                                 PasswordEncoder encoder,
                                 MobileRefreshTokenService refreshTokenService) {
        this.repo                     = repo;
        this.studentRepository        = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.encoder                  = encoder;
        this.refreshTokenService      = refreshTokenService;
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

    public Optional<FamilyAccount> findById(Long id) {
        log.info("Inside findById");
        return repo.findById(id);
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

    /** Generates a temp password an admin can hand to a parent — 8 chars, mixed
     *  case + digits, no ambiguous characters. Used by the Mobile Users screen's
     *  "Generate" button; the admin can still overwrite it before saving. */
    public String generateTempPassword() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RNG.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // ── Mobile Users admin screen ────────────────────────────────────────────

    /**
     * Builds one row per FamilyAccount in the system (global, not scoped to a
     * school/session, since one mobile number can span branches). Does the only
     * DB-heavy pass for this screen — callers should reuse the returned list for
     * both the searchable table and the summary-stat cards rather than calling
     * this twice.
     *
     * PERF NOTE (fixed — was the cause of the slow load / slow search): this used
     * to be a classic N+1, and it ran in full on every /list call including every
     * debounced search keystroke, not just the initial page load:
     *   1. repo.findAll() then account.getStudents() per account — students is a
     *      LAZY @OneToMany, so 1 extra SELECT per family account.
     *   2. academicStudentRepository.findAllByStudent_IdAndStatus(...) per student
     *      — 1 extra SELECT per linked student.
     *   3. refreshTokenService.getSessionSummary(...) per account — 1 extra SELECT
     *      per family account.
     * For F family accounts and S linked students that was roughly 1 + 2F + S
     * round trips. Now it's 3 queries total, independent of F and S: one to load
     * every account with its students already joined, one to batch-resolve every
     * linked student's active AcademicStudent, one to batch-load every relevant
     * refresh token — everything else below is in-memory.
     */
    public List<MobileUserRowDto> getAllMobileUserRows() {
        log.info("Inside getAllMobileUserRows");
        List<FamilyAccount> accounts = repo.findAllWithStudents();

        // Query 1 of 3 (done above): accounts + their students in one shot.
        Set<Long> allStudentIds = new HashSet<>();
        for (FamilyAccount account : accounts) {
            for (Student s : account.getStudents()) {
                allStudentIds.add(s.getId());
            }
        }

        // Query 2 of 3: every Active AcademicStudent for every linked student, in one call.
        Map<Long, List<AcademicStudent>> academicByStudentId = new HashMap<>();
        if (!allStudentIds.isEmpty()) {
            List<AcademicStudent> allActive =
                    academicStudentRepository.findAllByStudent_IdInAndStatus(new ArrayList<>(allStudentIds), "Active");
            for (AcademicStudent as : allActive) {
                academicByStudentId.computeIfAbsent(as.getStudent().getId(), k -> new ArrayList<>()).add(as);
            }
        }

        // Query 3 of 3: every refresh token for every resolved AcademicStudent, in one call.
        Set<Long> allAcademicStudentIds = new HashSet<>();
        for (List<AcademicStudent> asList : academicByStudentId.values()) {
            for (AcademicStudent as : asList) {
                allAcademicStudentIds.add(as.getId());
            }
        }
        Map<Long, List<MobileRefreshToken>> tokensByAcademicStudentId =
                refreshTokenService.findTokensGroupedByStudent(new ArrayList<>(allAcademicStudentIds));

        // Everything from here is pure in-memory row-building — no further DB access.
        List<MobileUserRowDto> result = new ArrayList<>();
        for (FamilyAccount account : accounts) {
            MobileUserRowDto dto = new MobileUserRowDto();
            dto.setFamilyAccountId(account.getId());
            dto.setMobile(account.getMobile());
            dto.setStatus(account.getStatus());
            dto.setMustChangePassword(account.isMustChangePassword());

            List<String> studentLines = new ArrayList<>();
            List<Long> academicStudentIds = new ArrayList<>();
            for (Student s : account.getStudents()) {
                List<AcademicStudent> asList = academicByStudentId.getOrDefault(s.getId(), Collections.emptyList());
                if (!asList.isEmpty()) {
                    AcademicStudent as = asList.get(0);
                    String grade = as.getGrade() != null ? as.getGrade().getGradeName() : "";
                    String section = as.getSection() != null ? as.getSection().getSectionName() : "";
                    String school = as.getSchool() != null ? as.getSchool().getSchoolName() : "";
                    String classPart = grade.isEmpty() ? "" : (section.isEmpty() ? grade : grade + "-" + section);
                    String label = s.getStudentName()
                            + (classPart.isEmpty() ? "" : " (" + classPart + (school.isEmpty() ? "" : ", " + school) + ")");
                    studentLines.add(label);
                    academicStudentIds.add(as.getId());
                } else {
                    studentLines.add(s.getStudentName() + " (inactive)");
                }
            }
            dto.setStudents(studentLines);
            dto.setAcademicStudentIds(academicStudentIds);

            List<MobileRefreshToken> tokens = new ArrayList<>();
            for (Long asId : academicStudentIds) {
                tokens.addAll(tokensByAcademicStudentId.getOrDefault(asId, Collections.emptyList()));
            }
            MobileRefreshTokenService.SessionSummary summary = refreshTokenService.summarizeTokens(tokens);
            dto.setEverLoggedIn(summary.everLoggedIn);
            dto.setHasValidSession(summary.hasValidSession);
            dto.setLastActive(summary.lastActive);
            dto.setLastActiveDisplay(summary.lastActive != null
                    ? summary.lastActive.format(LAST_ACTIVE_FORMAT) : "Never");

            result.add(dto);
        }
        return result;
    }

    /** Pure in-memory filter over an already-built row list — no DB access.
     *  Matches on mobile number or any linked student's display line. */
    public List<MobileUserRowDto> filterMobileUserRows(List<MobileUserRowDto> allRows, String search) {
        String q = search == null ? "" : search.trim().toLowerCase();
        List<MobileUserRowDto> filtered;
        if (q.isEmpty()) {
            filtered = new ArrayList<>(allRows);
        } else {
            filtered = allRows.stream().filter(row -> {
                if (row.getMobile() != null && row.getMobile().toLowerCase().contains(q)) return true;
                for (String line : row.getStudents()) {
                    if (line.toLowerCase().contains(q)) return true;
                }
                return false;
            }).collect(Collectors.toList());
        }
        // Most recently active first; never-logged-in rows sort last.
        filtered.sort((a, b) -> {
            if (a.getLastActive() == null && b.getLastActive() == null) return 0;
            if (a.getLastActive() == null) return 1;
            if (b.getLastActive() == null) return -1;
            return b.getLastActive().compareTo(a.getLastActive());
        });
        return filtered;
    }

    /** Admin "Force Logout" — revokes every active session across every child
     *  linked to this family, via MobileRefreshTokenService.revokeAllForFamily(). */
    @Transactional
    public void forceLogoutFamily(FamilyAccount account) {
        log.info("Inside forceLogoutFamily - familyAccountId={}", account.getId());
        List<Long> academicStudentIds = new ArrayList<>();
        for (Student s : account.getStudents()) {
            List<AcademicStudent> asList = academicStudentRepository.findAllByStudent_IdAndStatus(s.getId(), "Active");
            for (AcademicStudent as : asList) {
                academicStudentIds.add(as.getId());
            }
        }
        refreshTokenService.revokeAllForFamily(academicStudentIds);
    }

    /** Pure in-memory aggregate over an already-built row list — no DB access. */
    public MobileUserStatsDto computeMobileUserStats(List<MobileUserRowDto> allRows) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long total = allRows.size();
        long everLoggedIn = allRows.stream().filter(MobileUserRowDto::isEverLoggedIn).count();
        long activeLast30Days = allRows.stream()
                .filter(r -> r.getLastActive() != null && r.getLastActive().isAfter(cutoff))
                .count();
        long validSessionNow = allRows.stream().filter(MobileUserRowDto::isHasValidSession).count();
        return new MobileUserStatsDto(total, everLoggedIn, activeLast30Days, validSessionNow);
    }
}
