package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-time migration endpoint.
 *
 * POST /admin/migrate-family-accounts
 *   – Scans every student with a non-blank mobile1 and creates a FamilyAccount
 *     if one doesn't already exist.
 *   – Safe to call multiple times (createIfAbsent is idempotent).
 *   – Restricted to SUPERADMIN.
 *
 * After running this once, new students automatically get a FamilyAccount
 * via StudentService.saveStudent() / editStudentDetails().
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final StudentRepository    studentRepository;
    private final FamilyAccountService familyAccountService;

    public MigrationController(StudentRepository studentRepository,
                                FamilyAccountService familyAccountService) {
        this.studentRepository    = studentRepository;
        this.familyAccountService = familyAccountService;
    }

    /**
     * Backfill FamilyAccount for every existing active student that has mobile1 set.
     *
     * Returns a summary:
     * {
     *   "totalStudents":   150,
     *   "withMobile":      140,
     *   "created":          85,   ← new FamilyAccounts created
     *   "alreadyExisted":   55,   ← already had one (idempotent)
     *   "skipped":          10,   ← mobile1 blank / null
     *   "errors":          [ "Student ID 42: ..." ]
     * }
     */
    @PostMapping("/migrate-family-accounts")
    public ResponseEntity<Map<String, Object>> migrateFamilyAccounts() {

        List<Student> allStudents = studentRepository.findAllByStatus("Active");

        int withMobile   = 0;
        int created      = 0;
        int alreadyExisted = 0;
        int skipped      = 0;
        List<String> errors = new ArrayList<>();

        for (Student student : allStudents) {
            String mobile = student.getMobile1();
            if (mobile == null || mobile.isBlank()) {
                skipped++;
                continue;
            }
            withMobile++;
            try {
                boolean existed = familyAccountService.findByMobile(mobile).isPresent();
                familyAccountService.createIfAbsent(mobile);
                if (existed) alreadyExisted++;
                else         created++;
            } catch (Exception e) {
                errors.add("Student ID " + student.getId() + " (mobile: " + mobile + "): " + e.getMessage());
                log.error("Migration error for student {}: {}", student.getId(), e.getMessage());
            }
        }

        log.info("FamilyAccount migration done — created={}, alreadyExisted={}, skipped={}, errors={}",
                created, alreadyExisted, skipped, errors.size());

        return ResponseEntity.ok(Map.of(
                "totalStudents",  allStudents.size(),
                "withMobile",     withMobile,
                "created",        created,
                "alreadyExisted", alreadyExisted,
                "skipped",        skipped,
                "errors",         errors
        ));
    }
}
