package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.MobileRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * NEW repository (feature #10), brand-new package (repositories.mobile).
 */
public interface MobileRefreshTokenRepository extends JpaRepository<MobileRefreshToken, Long> {

    Optional<MobileRefreshToken> findByTokenHash(String tokenHash);

    List<MobileRefreshToken> findAllByAcademicStudent_IdAndRevokedFalse(Long academicStudentId);

    /** Used by the Mobile Users admin screen to compute session status / last-active
     *  across every child linked to one family account in a single query. */
    List<MobileRefreshToken> findAllByAcademicStudent_IdIn(List<Long> academicStudentIds);

    // ── Stats for the admin cleanup page ────────────────────────────────────

    long countByRevokedFalseAndExpiresAtAfter(LocalDateTime now);   // active sessions
    long countByRevokedFalseAndExpiresAtBefore(LocalDateTime now);  // expired but not yet cleaned up
    long countByRevokedTrue();                                      // revoked (logout / rotated / theft response)

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Deletes rows that are "done" (revoked, or past their expiry) AND have
     * been done for at least `cutoff` — i.e. a grace period is kept before
     * actually purging, so a recently revoked/expired row is still available
     * for a little while if anyone needs to check "was this session revoked
     * because of logout, or because we caught token reuse?".
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MobileRefreshToken t WHERE " +
           "(t.revoked = true AND t.revokedAt < :cutoff) OR " +
           "(t.revoked = false AND t.expiresAt < :cutoff)")
    int deleteStaleTokens(@Param("cutoff") LocalDateTime cutoff);
}
