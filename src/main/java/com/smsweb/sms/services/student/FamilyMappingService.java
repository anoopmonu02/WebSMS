package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.FamilyMappingRowDto;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.FamilyMappingQueryRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Family Mapping screen. Contains NO linking rules of its own: every Map /
 * Re-map goes through FamilyAccountService.createIfAbsent(mobile1), the same call used by
 * Add Student, Edit Student and the Family Migration job.
 *
 * Deliberately NOT @Transactional: each createIfAbsent() call runs in its own transaction,
 * so one bad number in "Map all" cannot roll back the numbers that already succeeded.
 *
 * Matching rule (unchanged): mobile1 only. mobile2 is display-only here.
 */
@Service
public class FamilyMappingService {

    private static final Logger log = LoggerFactory.getLogger(FamilyMappingService.class);
    private static final String ACTIVE = "ACTIVE";

    private final FamilyMappingQueryRepository queryRepository;
    private final FamilyAccountService familyAccountService;
    private final StudentRepository studentRepository;

    public FamilyMappingService(FamilyMappingQueryRepository queryRepository,
                                FamilyAccountService familyAccountService,
                                StudentRepository studentRepository) {
        this.queryRepository = queryRepository;
        this.familyAccountService = familyAccountService;
        this.studentRepository = studentRepository;
    }

    // ── Result types (serialised to JSON by the controller) ───────────────────

    @Getter @Setter
    public static class MapResult {
        private String mobile;
        /** MAPPED, SKIPPED or FAILED */
        private String status;
        private String message;
        private boolean accountCreated;
        private int linkedCount;
        private List<Long> linkedStudentIds = new ArrayList<>();
    }

    @Getter @Setter
    public static class MapAllSummary {
        private int totalNumbers;
        private int mapped;
        private int accountsCreated;
        private int studentsLinked;
        private int skipped;
        private int failed;
        /** Only the numbers that were skipped or failed, with the reason. */
        private List<MapResult> problems = new ArrayList<>();
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public List<FamilyMappingRowDto> getRows(Long mediumId, Long schoolId, Long academicYearId) {
        log.info("Inside getRows - mediumId={}", mediumId);
        List<FamilyMappingRowDto> rows = queryRepository.findRows(schoolId, academicYearId, mediumId);
        for (FamilyMappingRowDto r : rows) {
            resolveState(r);
        }
        return rows;
    }

    /** Pure in-memory classification - see FamilyMappingRowDto for the meaning of each state. */
    static void resolveState(FamilyMappingRowDto r) {
        String m1 = r.getMobile1() == null ? "" : r.getMobile1().trim();
        if (m1.isEmpty()) {
            r.setState(FamilyMappingRowDto.NO_MOBILE);
        } else if (r.getFamilyAccountId() == null) {
            r.setState(FamilyMappingRowDto.UNMAPPED);
        } else if (!m1.equals(r.getFamilyMobile())) {
            r.setState(FamilyMappingRowDto.MISMATCH);
            r.setMismatchReason("OTHER_MOBILE");
        } else if (!ACTIVE.equals(r.getFamilyStatus())) {
            r.setState(FamilyMappingRowDto.MISMATCH);
            r.setMismatchReason("ACCOUNT_INACTIVE");
        } else {
            r.setState(FamilyMappingRowDto.MAPPED);
        }
    }

    // ── Map one student ───────────────────────────────────────────────────────

    /** Map / Re-map for one row. mobile1 is read from the database, never trusted from the client. */
    public MapResult mapStudent(Long studentId, Long schoolId, Long academicYearId) {
        log.info("Inside mapStudent - studentId={}", studentId);
        MapResult res = new MapResult();
        Student student = studentId == null ? null : studentRepository.findById(studentId).orElse(null);
        if (student == null
                || queryRepository.countActiveEnrollment(studentId, schoolId, academicYearId) == 0) {
            res.setStatus("FAILED");
            res.setMessage("Student is not an active student of this school and session.");
            return res;
        }
        return mapMobile(student.getMobile1(), schoolId, academicYearId);
    }

    // ── Map every pending number of a medium ──────────────────────────────────

    /** The pending list is re-computed on the server; nothing is taken from the client. */
    public MapAllSummary mapAllPending(Long mediumId, Long schoolId, Long academicYearId) {
        log.info("Inside mapAllPending - mediumId={}", mediumId);
        Set<String> numbers = new LinkedHashSet<>();
        for (FamilyMappingRowDto r : getRows(mediumId, schoolId, academicYearId)) {
            if (FamilyMappingRowDto.UNMAPPED.equals(r.getState())
                    || FamilyMappingRowDto.MISMATCH.equals(r.getState())) {
                numbers.add(r.getMobile1().trim());
            }
        }
        MapAllSummary summary = new MapAllSummary();
        summary.setTotalNumbers(numbers.size());
        for (String number : numbers) {
            MapResult res = mapMobile(number, schoolId, academicYearId);
            if ("MAPPED".equals(res.getStatus())) {
                summary.setMapped(summary.getMapped() + 1);
                summary.setStudentsLinked(summary.getStudentsLinked() + res.getLinkedCount());
                if (res.isAccountCreated()) {
                    summary.setAccountsCreated(summary.getAccountsCreated() + 1);
                }
            } else {
                if ("SKIPPED".equals(res.getStatus())) {
                    summary.setSkipped(summary.getSkipped() + 1);
                } else {
                    summary.setFailed(summary.getFailed() + 1);
                }
                summary.getProblems().add(res);
            }
        }
        return summary;
    }

    // ── Core: one mobile1 ─────────────────────────────────────────────────────

    MapResult mapMobile(String rawMobile, Long schoolId, Long academicYearId) {
        MapResult res = new MapResult();
        String mobile = rawMobile == null ? "" : rawMobile.trim();
        res.setMobile(mobile);

        if (!mobile.matches("^[0-9]{10}$")) {
            res.setStatus("FAILED");
            res.setMessage("Mobile 1 is missing or not a 10-digit number. Fix it in Edit Student first.");
            return res;
        }
        // Scope: only numbers that belong to an Active student of THIS school and session.
        if (queryRepository.countActiveByMobile1(mobile, schoolId, academicYearId) == 0) {
            res.setStatus("FAILED");
            res.setMessage("No active student of this school and session has this Mobile 1.");
            return res;
        }
        Optional<FamilyAccount> existing = familyAccountService.findByMobile(mobile);
        // An INACTIVE account would link but the parent could never log in: do not hide that.
        if (existing.isPresent() && !ACTIVE.equals(existing.get().getStatus())) {
            res.setStatus("SKIPPED");
            res.setMessage("Family account for this number is INACTIVE. Review it before mapping.");
            return res;
        }
        try {
            FamilyAccount account = familyAccountService.createIfAbsent(mobile);
            res.setAccountCreated(existing.isEmpty());
            for (Student s : studentRepository.findAllByMobile1(mobile)) {
                if (s.getFamilyAccount() != null && account.getId().equals(s.getFamilyAccount().getId())) {
                    res.getLinkedStudentIds().add(s.getId());
                }
            }
            res.setLinkedCount(res.getLinkedStudentIds().size());
            res.setStatus("MAPPED");
            res.setMessage(res.isAccountCreated() ? "Family account created and linked." : "Linked to existing family account.");
        } catch (Exception e) {
            log.error("Family mapping failed for a mobile number", e);
            res.setStatus("FAILED");
            res.setMessage("Could not map this number: " + e.getMessage());
        }
        return res;
    }
}
