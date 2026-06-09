package com.smsweb.sms.services.mobile;

import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.FamilyAccountRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    private final FamilyAccountRepository repo;
    private final StudentRepository        studentRepository;
    private final PasswordEncoder          encoder;

    public FamilyAccountService(FamilyAccountRepository repo,
                                 StudentRepository studentRepository,
                                 PasswordEncoder encoder) {
        this.repo              = repo;
        this.studentRepository = studentRepository;
        this.encoder           = encoder;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Optional<FamilyAccount> findActive(String mobile) {
        return repo.findByMobileAndStatus(mobile, "ACTIVE");
    }

    public Optional<FamilyAccount> findByMobile(String mobile) {
        return repo.findByMobile(mobile);
    }

    // ── Password verification ─────────────────────────────────────────────────

    public boolean verifyPassword(FamilyAccount account, String rawPassword) {
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

        // 2. Link all students with this mobile1 to the account (sets the FK)
        //    This handles both new registration and migration of existing records.
        studentRepository.findAllByMobile1(mobile).forEach(student -> {
            if (student.getFamilyAccount() == null ||
                    !student.getFamilyAccount().getId().equals(account.getId())) {
                student.setFamilyAccount(account);
                studentRepository.save(student);
            }
        });

        return account;
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
        account.setPasswordHash(encoder.encode(newRawPassword));
        account.setMustChangePassword(true);
        repo.save(account);
    }
}
