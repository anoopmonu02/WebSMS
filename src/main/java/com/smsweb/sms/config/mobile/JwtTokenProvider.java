package com.smsweb.sms.config.mobile;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token Provider for the Student Mobile API.
 *
 * Generates and validates JWT tokens for /api/v1/** endpoints.
 * Embeds academicStudentId, schoolId, and academicYearId as claims
 * so controllers can identify the authenticated student without extra DB lookups.
 *
 * Config keys (application.properties):
 *   app.jwt.secret     — raw string used as HMAC key (64+ chars recommended)
 *   app.jwt.expiration — token TTL in milliseconds (86400000 = 24 h)
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    // ── Key ──────────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        // The secret stored in application.properties is a 64-char hex/ASCII string.
        // Using UTF-8 bytes gives 64 bytes (512 bits) — more than sufficient for HS256/HS512.
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Token generation ─────────────────────────────────────────────────────

    /**
     * Builds a signed JWT for a successfully authenticated student.
     *
     * @param username          Spring Security username (from UserEntity.username)
     * @param academicStudentId AcademicStudent.id — the primary identity for all data queries
     * @param schoolId          School.id
     * @param academicYearId    AcademicYear.id (current active year)
     * @param studentName       Display name for the app header
     */
    public String generateToken(String username,
                                Long academicStudentId,
                                Long schoolId,
                                Long academicYearId,
                                String studentName) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("academicStudentId", academicStudentId)
                .claim("schoolId",          schoolId)
                .claim("academicYearId",    academicYearId)
                .claim("studentName",       studentName)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // ── Claims extraction ────────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractAcademicStudentId(String token) {
        return extractAllClaims(token).get("academicStudentId", Long.class);
    }

    public Long extractSchoolId(String token) {
        return extractAllClaims(token).get("schoolId", Long.class);
    }

    public Long extractAcademicYearId(String token) {
        return extractAllClaims(token).get("academicYearId", Long.class);
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /**
     * Returns true if the token signature is valid and it has not expired.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT error: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }
}
