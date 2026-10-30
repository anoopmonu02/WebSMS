package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.MobileRefreshToken;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only lookups for "is this parent using the mobile app?" (Fee Submission screen).
 * Kept in its own interface so MobileRefreshTokenRepository is not touched.
 */
public interface FamilyAppUsageQueryRepository extends Repository<MobileRefreshToken, Long> {

    /** Id of the family account the student is linked to, or null when not linked. */
    @Query("SELECT s.familyAccount.id FROM Student s WHERE s.id = :studentId")
    Long findFamilyAccountIdOfStudent(@Param("studentId") Long studentId);

    /** Number of login records across every child linked to this family account. */
    @Query("SELECT COUNT(t) FROM MobileRefreshToken t " +
           "WHERE t.academicStudent.student.familyAccount.id = :familyAccountId")
    long countTokensOfFamily(@Param("familyAccountId") Long familyAccountId);
}
