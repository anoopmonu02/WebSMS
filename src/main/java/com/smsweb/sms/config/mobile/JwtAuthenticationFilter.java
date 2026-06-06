package com.smsweb.sms.config.mobile;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for the Mobile API (/api/v1/**).
 *
 * Runs once per request. Reads the "Authorization: Bearer <token>" header,
 * validates the JWT, loads the UserDetails, and sets the SecurityContext.
 *
 * Also stores decoded claims (academicStudentId, schoolId, academicYearId, studentName)
 * as request attributes so controllers can access them without re-parsing the token.
 *
 * Usage in controllers:
 *   Long academicStudentId = (Long) request.getAttribute("academicStudentId");
 *   Long schoolId          = (Long) request.getAttribute("schoolId");
 *   Long academicYearId    = (Long) request.getAttribute("academicYearId");
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider   jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   UserDetailsService userDetailsService) {
        this.jwtTokenProvider  = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractBearerToken(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {

                String   username = jwtTokenProvider.extractUsername(jwt);
                Claims   claims   = jwtTokenProvider.extractAllClaims(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
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
            }

        } catch (Exception ex) {
            log.error("Cannot set user authentication from JWT: {}", ex.getMessage());
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
