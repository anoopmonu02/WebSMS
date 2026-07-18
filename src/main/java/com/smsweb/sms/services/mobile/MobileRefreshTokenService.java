package com.smsweb.sms.services.mobile;

import com.smsweb.sms.models.mobile.MobileRefreshToken;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.mobile.MobileRefreshTokenRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NEW service (feature #10 — refresh tokens, Option B / rotation), brand-new
 * package (services.mobile). Owns the new MobileRefreshToken table outright;
 * injects the existing AcademicStudentRepository read-only (no changes to it)
 * to resolve the owning student after a rotation.
 *
 * Design recap:
 *  - Login / select-child / switch-child each call issueToken() → one new row.
 *  - Every successful POST /auth/refresh calls rotate(): the presented token's
 *    row is marked revoked, a brand-new row + brand-new raw token is created
 *    and returned. A raw refresh token is therefore single-use.
 *  - Logout calls revoke() (or revokeAllForStudent()) to invalidate server-side
 *    immediately instead of relying on natural expiry.
 *  - A scheduled job (MobileRefreshTokenCleanupScheduler) and an admin "Run
 *    Cleanup Now" button both call cleanupNow() — same code path either way.
 */
@Service
public class MobileRefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(MobileRefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    @Autowired
    private MobileRefreshTokenRepository refreshTokenRepository;

    // Existing repository — injected read-only, never modified by this class.
    @Autowired
    private AcademicStudentRepository academicStudentRepository;

    // Refresh token lifetime — default 30 days if not set in application.properties.
    @Value("${app.jwt.refreshExpiration:2592000000}")
    private long refreshExpirationMs;

    // How long a revoked/expired row is kept before the cleanup job purges it —
    // default 7 days, giving a window to investigate before data is gone.
    @Value("${app.mobile.refreshToken.cleanupGraceDays:7}")
    private int cleanupGraceDays;

    // ── Issue (login / select-child / switch-child) ─────────────────────────

    @Transactional
    public String issueToken(AcademicStudent academicStudent) {
        String raw = generateRawToken();
        LocalDateTime now = LocalDateTime.now();

        MobileRefreshToken row = new MobileRefreshToken(
                academicStudent, hash(raw), now, now.plusSeconds(refreshExpirationMs / 1000));
        refreshTokenRepository.save(row);
        return raw;
    }

    // ── Rotate (POST /auth/refresh) ──────────────────────────────────────────

    public static class RotationResult {
        public final AcademicStudent academicStudent;
        public final String newRawRefreshToken;
        public RotationResult(AcademicStudent academicStudent, String newRawRefreshToken) {
            this.academicStudent = academicStudent;
            this.newRawRefreshToken = newRawRefreshToken;
        }
    }

    /**
     * Validates the presented refresh token and, if valid, rotates it:
     * marks the old row revoked and creates a brand-new row + token.
     * Returns Optional.empty() if the token is unknown, already revoked
     * (possible theft/reuse), or expired — caller must treat all of those
     * the same way: reject with 401, do NOT reveal which case it was.
     */
    @Transactional
    public Optional<RotationResult> rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        Optional<MobileRefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        MobileRefreshToken row = found.get();
        LocalDateTime now = LocalDateTime.now();

        if (row.isRevoked()) {
            // Reuse of an already-rotated (or already-logged-out) token —
            // treat as a possible theft signal: kill every session for this
            // student so a real attacker can't keep using other tokens from
            // the same compromised device/session chain.
            log.warn("Refresh token reuse detected for academicStudentId={} — revoking all sessions",
                    row.getAcademicStudent().getId());
            revokeAllForStudent(row.getAcademicStudent().getId());
            return Optional.empty();
        }

        if (row.getExpiresAt().isBefore(now)) {
            return Optional.empty();
        }

        // Rotate: revoke the old row, issue a new one.
        row.setRevoked(true);
        row.setRevokedAt(now);
        row.setLastUsedAt(now);
        refreshTokenRepository.save(row);

        AcademicStudent academicStudent = row.getAcademicStudent();
        String newRaw = issueToken(academicStudent);

        return Optional.of(new RotationResult(academicStudent, newRaw));
    }

    // ── Revoke (logout) ──────────────────────────────────────────────────────

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(row -> {
            row.setRevoked(true);
            row.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(row);
        });
    }

    /** Revokes every active session for a student — used on logout (fallback,
     *  if no specific token was passed) and on theft-detection above. */
    @Transactional
    public void revokeAllForStudent(Long academicStudentId) {
        LocalDateTime now = LocalDateTime.now();
        List<MobileRefreshToken> active =
                refreshTokenRepository.findAllByAcademicStudent_IdAndRevokedFalse(academicStudentId);
        for (MobileRefreshToken row : active) {
            row.setRevoked(true);
            row.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(active);
    }

    /**
     * Admin "Force Logout" for a whole family — revokes every active session across
     * every child linked to that mobile number. Deliberately just loops the existing,
     * already-exercised revokeAllForStudent() rather than writing new revoke logic, so
     * this reuses the exact same tested behaviour as self-logout/theft-detection.
     */
    @Transactional
    public void revokeAllForFamily(List<Long> academicStudentIds) {
        if (academicStudentIds == null) return;
        for (Long id : academicStudentIds) {
            revokeAllForStudent(id);
        }
    }

    // ── Session summary for the Mobile Users admin screen ───────────────────

    public static class SessionSummary {
        public final boolean hasValidSession;
        public final boolean everLoggedIn;
        public final LocalDateTime lastActive; // null if never logged in
        public SessionSummary(boolean hasValidSession, boolean everLoggedIn, LocalDateTime lastActive) {
            this.hasValidSession = hasValidSession;
            this.everLoggedIn = everLoggedIn;
            this.lastActive = lastActive;
        }
    }

    /**
     * Builds a session summary across every child linked to one family, using only
     * existing MobileRefreshToken data — no new tracking.
     *   hasValidSession = at least one row that's neither revoked nor expired.
     *   lastActive      = latest known activity across all rows: a row's lastUsedAt
     *                      if it's been rotated at least once, otherwise its issuedAt
     *                      (a not-yet-rotated row has no lastUsedAt yet, but its
     *                      issuedAt is still the most recent thing we know happened).
     */
    public SessionSummary getSessionSummary(List<Long> academicStudentIds) {
        if (academicStudentIds == null || academicStudentIds.isEmpty()) {
            return new SessionSummary(false, false, null);
        }
        List<MobileRefreshToken> tokens = refreshTokenRepository.findAllByAcademicStudent_IdIn(academicStudentIds);
        if (tokens.isEmpty()) {
            return new SessionSummary(false, false, null);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean hasValidSession = false;
        LocalDateTime lastActive = null;
        for (MobileRefreshToken row : tokens) {
            if (!row.isRevoked() && row.getExpiresAt() != null && row.getExpiresAt().isAfter(now)) {
                hasValidSession = true;
            }
            LocalDateTime activityMoment = row.getLastUsedAt() != null ? row.getLastUsedAt() : row.getIssuedAt();
            if (activityMoment != null && (lastActive == null || activityMoment.isAfter(lastActive))) {
                lastActive = activityMoment;
            }
        }
        return new SessionSummary(hasValidSession, true, lastActive);
    }

    // ── Cleanup — called by the scheduler AND the admin "Run Now" button ────

    @Transactional
    public int cleanupNow() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cleanupGraceDays);
        int deleted = refreshTokenRepository.deleteStaleTokens(cutoff);
        log.info("Mobile refresh token cleanup: deleted {} stale row(s) (cutoff={})", deleted, cutoff);
        return deleted;
    }

    // ── Stats for the admin page ─────────────────────────────────────────────

    public Map<String, Long> getStats() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> stats = new HashMap<>();
        stats.put("active",  refreshTokenRepository.countByRevokedFalseAndExpiresAtAfter(now));
        stats.put("expired", refreshTokenRepository.countByRevokedFalseAndExpiresAtBefore(now));
        stats.put("revoked", refreshTokenRepository.countByRevokedTrue());
        stats.put("total",   refreshTokenRepository.count());
        return stats;
    }

    public int getCleanupGraceDays() {
        return cleanupGraceDays;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM — this can't happen in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
