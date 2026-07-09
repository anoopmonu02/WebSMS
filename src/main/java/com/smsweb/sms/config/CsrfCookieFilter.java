package com.smsweb.sms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the deferred CSRF token to be loaded (and thus written to the XSRF-TOKEN cookie)
 * on every request.
 *
 * Background: Spring Security 6 uses lazy/deferred CSRF tokens with CookieCsrfTokenRepository.
 * The cookie is only written when something calls csrfToken.getToken(). If the cookie is
 * never written (e.g., because Thymeleaf's th:action access doesn't trigger the commit in
 * certain browsers or timing scenarios), the first form POST has no XSRF-TOKEN cookie, causing
 * a MissingCsrfTokenException that silently redirects back to the login page.
 *
 * This filter eagerly calls csrfToken.getToken() after CsrfFilter so the Set-Cookie header
 * is always included in every response — matching the pattern in Spring Security's own docs.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Accessing getToken() triggers the deferred supplier, which calls
            // CookieCsrfTokenRepository.saveToken() and writes the Set-Cookie header.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
