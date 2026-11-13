package com.smsweb.sms.config.mobile;

import com.smsweb.sms.services.security.ApiRateLimitService;
import com.smsweb.sms.services.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Request-rate limit for the mobile API chain ({@code /api/v1/**}). Registered only in that chain, right after
 * {@link JwtAuthenticationFilter}, so the signed-in user (token subject) is known. The web chain is untouched.
 *
 * <p>Over the limit (enforce mode only) the caller gets HTTP 429 with a {@code Retry-After} header and the same
 * JSON envelope the rest of the API uses. In the default {@code log} mode nothing is ever blocked.
 * Fail-open: any unexpected error lets the request through.</p>
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    static final String BODY = "{\"success\":false,\"message\":\"Too many requests. Please try again shortly.\",\"data\":null}";

    private final ApiRateLimitService service;
    private final ClientIpResolver clientIp;

    public ApiRateLimitFilter(ApiRateLimitService service, ClientIpResolver clientIp) {
        this.service = service;
        this.clientIp = clientIp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiRateLimitService.Decision decision = ApiRateLimitService.Decision.allow();
        try {
            String path = request.getRequestURI();
            String ctx = request.getContextPath();
            if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
                path = path.substring(ctx.length());
            }
            ApiRateLimitService.Kind kind = ApiRateLimitService.kindOf(path);
            if (kind != ApiRateLimitService.Kind.SKIP) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String user = auth != null && auth.isAuthenticated() ? auth.getName() : null;
                decision = service.check(kind, user, clientIp.resolve(request));
            }
        } catch (RuntimeException e) {
            log.error("API rate limit failed; allowing the request", e);
        }
        if (decision.block()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }
}
