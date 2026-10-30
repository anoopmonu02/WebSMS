package com.smsweb.sms.services.mobile;

import com.smsweb.sms.repositories.mobile.FamilyAppUsageQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the Fee Submission screen whether a student's parent is using the mobile app,
 * using only existing data (family account link + login records). Returns a status code
 * only; no password, hash, token or account id ever leaves this class.
 *
 * NO_ACCOUNT - student is not linked to a family account
 * NO_LOGIN   - linked, but no login record exists for any child of that family
 * USING      - at least one login record exists
 *
 * Limit: old login records are cleaned up daily (7 days after they expire or are revoked,
 * see MobileRefreshTokenService.cleanupNow), so NO_LOGIN means "no recent login found",
 * not "never". The Mobile Users admin screen has the same limit.
 *
 * REQUIRES_NEW + readOnly: the caller (getStudentDetailForFee) runs in its own
 * transaction. If this lookup failed inside that transaction it would mark it
 * rollback-only and break the whole fee screen load; in its own transaction a failure
 * stays contained. The caller must still catch exceptions and treat them as "no message".
 */
@Service
public class FamilyAppStatusService {

    private static final Logger log = LoggerFactory.getLogger(FamilyAppStatusService.class);

    public static final String NO_ACCOUNT = "NO_ACCOUNT";
    public static final String NO_LOGIN = "NO_LOGIN";
    public static final String USING = "USING";

    private final FamilyAppUsageQueryRepository usageRepository;

    public FamilyAppStatusService(FamilyAppUsageQueryRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String statusForStudent(Long studentId) {
        log.info("Inside statusForStudent");
        if (studentId == null) {
            return null;
        }
        Long familyAccountId = usageRepository.findFamilyAccountIdOfStudent(studentId);
        if (familyAccountId == null) {
            return NO_ACCOUNT;
        }
        return usageRepository.countTokensOfFamily(familyAccountId) > 0 ? USING : NO_LOGIN;
    }
}
