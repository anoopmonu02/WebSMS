package com.smsweb.sms.config.mobile;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter for the Mobile API (/api/v1/**).
 *
 * Runs once per request. Reads the "Authorization: Bearer <token>" header,
 * validates the JWT signature + expiry, then sets a minimal Spring Security
 * authentication directly from the token claims — NO database lookup required.
 *
 * This is intentional: the JWT is the sole source of identity for the mobile API.
 * Controllers obtain the student context via request attributes stashed here.
 *
 * Usage in controllers:
 *   Long academicStudentId = (Long) request.getAttribute("academicStudentId");
 *   Long schoolId          = (Long) request.getAttribute("schoolId");
 *   Long academicYearId    = (Long) request.getAttribute("academicYearId");
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractBearerToken(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {

                Claims claims  = jwtTokenProvider.extractAllClaims(jwt);
                String subject = claims.getSubject(); // UserEntity.username

                // Authenticate directly from claims — no DB lookup.
                // The JWT signature already proves authenticity.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject,
                                null,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_STUDENT")));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Stash claims so controllers don't need to re-parse the token
                request.setAttribute("academicStudentId",
                        claims.get("academicStudentId", Long.class));
                request.setAttribute("schoolId",
                        claims.get("schoolId", Long.class));
                request.setAttribute("academicYearId",
                        claims.get("academicYearId", Long.class));
                request.setAttribute("studentName",
                        claims.get("studentName", String.class));

                log.debug("JWT authenticated: subject={} studentId={}",
                        subject, claims.get("academicStudentId"));
            }

        } catch (Exception ex) {
            log.warn("JWT authentication failed: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Extracts the raw token from the Authorization header. Returns null if absent/invalid. */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
