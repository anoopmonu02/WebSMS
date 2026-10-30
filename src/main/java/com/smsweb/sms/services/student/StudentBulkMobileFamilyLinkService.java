package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.BulkMobileLinkRow;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.repositories.student.FamilyMappingQueryRepository;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs AFTER Bulk Update (group MOBILE1_MOBILE2) has saved: makes sure each saved student
 * is linked to the family account of their mobile1, then confirms the link.
 *
 * Why a separate, NON-transactional step: saveFieldGroup() saves the whole batch in one
 * transaction and swallows per-row errors. A failure inside FamilyAccountService.createIfAbsent
 * (which joins that transaction) would mark the whole batch rollback-only and undo every row.
 * Running after the batch has committed keeps the saved mobiles safe: a failed link only
 * produces a warning on that row (decision F6), and each number gets its own transaction.
 *
 * No linking rule of its own: createIfAbsent(mobile1) is the single place that links.
 * An INACTIVE family account is never auto-activated (decision F5).
 */
@Service
public class StudentBulkMobileFamilyLinkService {

    private static final Logger log = LoggerFactory.getLogger(StudentBulkMobileFamilyLinkService.class);
    private static final String ACTIVE = "ACTIVE";

    private final FamilyMappingQueryRepository queryRepository;
    private final FamilyAccountService familyAccountService;

    public StudentBulkMobileFamilyLinkService(FamilyMappingQueryRepository queryRepository,
                                              FamilyAccountService familyAccountService) {
        this.queryRepository = queryRepository;
        this.familyAccountService = familyAccountService;
    }

    /**
     * @param results the per-row results returned by saveFieldGroup(); rows that saved
     *                successfully but could not be linked get "warning"=true and an
     *                explanatory message. Rows that failed to save are left untouched.
     */
    public void applyFamilyLink(List<Map<String, Object>> results, Long schoolId, Long academicYearId) {
        log.info("Inside applyFamilyLink");
        Map<UUID, Map<String, Object>> savedByUuid = new LinkedHashMap<>();
        for (Map<String, Object> r : results) {
            if (!Boolean.TRUE.equals(r.get("success"))) continue;
            try {
                savedByUuid.put(UUID.fromString(String.valueOf(r.get("uuid"))), r);
            } catch (IllegalArgumentException ignored) {
                // not a valid uuid: nothing to link for this row
            }
        }
        if (savedByUuid.isEmpty()) return;

        // Query 1: current link state of every saved row (committed data).
        List<BulkMobileLinkRow> before = queryRepository.findLinkRows(savedByUuid.keySet(), schoolId, academicYearId);

        // One createIfAbsent per distinct mobile1 that still needs a link.
        Set<String> numbers = new LinkedHashSet<>();
        for (BulkMobileLinkRow row : before) {
            if (!isLinked(row) && row.getMobile1() != null && !row.getMobile1().isBlank()) {
                numbers.add(row.getMobile1().trim());
            }
        }
        Map<String, String> problemByNumber = new HashMap<>();
        for (String mobile : numbers) {
            if (!mobile.matches("^[0-9]{10}$")) {
                problemByNumber.put(mobile, "Mobile 1 is not a 10-digit number.");
                continue;
            }
            Optional<FamilyAccount> existing = familyAccountService.findByMobile(mobile);
            if (existing.isPresent() && !ACTIVE.equals(existing.get().getStatus())) {
                problemByNumber.put(mobile, "The family account for this number is INACTIVE.");
                continue;
            }
            try {
                familyAccountService.createIfAbsent(mobile);
            } catch (Exception e) {
                log.error("Family link failed after bulk mobile update", e);
                problemByNumber.put(mobile, "The family account could not be created or linked.");
            }
        }

        // Query 2: confirm the links (skipped when nothing needed linking).
        List<BulkMobileLinkRow> after = numbers.isEmpty()
                ? before : queryRepository.findLinkRows(savedByUuid.keySet(), schoolId, academicYearId);
        for (BulkMobileLinkRow row : after) {
            if (isLinked(row)) continue;
            Map<String, Object> result = savedByUuid.get(row.getUuid());
            if (result == null) continue;
            String reason = problemByNumber.getOrDefault(row.getMobile1() == null ? "" : row.getMobile1().trim(),
                    "The family account link could not be confirmed.");
            result.put("warning", true);
            result.put("message", "Saved, but not linked to a family account. " + reason
                    + " Use Admin > Family Mapping to fix it.");
        }
    }

    /** Linked = points to an ACTIVE family account whose mobile equals the student's mobile1. */
    private static boolean isLinked(BulkMobileLinkRow row) {
        String m1 = row.getMobile1() == null ? "" : row.getMobile1().trim();
        return !m1.isEmpty() && m1.equals(row.getFamilyMobile()) && ACTIVE.equals(row.getFamilyStatus());
    }
}
