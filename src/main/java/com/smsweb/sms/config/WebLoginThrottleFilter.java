package com.smsweb.sms.config;

import com.smsweb.sms.services.security.ClientIpResolver;
import com.smsweb.sms.services.security.LoginThrottleService;
import com.smsweb.sms.services.security.TurnstileService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stops sign-in attempts on a locked account or a blocked IP address before the password is even checked.
 *
 * <p>Registered only in the web (form login) chain, before {@code UsernamePasswordAuthenticationFilter}, and acts
 * only on {@code POST /login}; every other request passes straight through. In the default {@code log} mode
 * {@link LoginThrottleService#shouldBlock} never returns true, so this filter changes nothing for users.</p>
 *
 * <p>When the Turnstile CAPTCHA is on (see {@code app.security.turnstile.*}) and this account or address has several
 * recent wrong attempts, a sign-in without a valid CAPTCHA answer is sent back to {@code /login?captcha=required}
 * before the password is checked, so no password guess is spent. If Cloudflare cannot be reached the sign-in
 * continues (fail-open).</p>
 *
 * <p>When blocking, it redirects to {@code /login?blocked=true} (the same kind of redirect the failure handler
 * uses) and does not say whether the account exists. Fail-open: any unexpected error lets the request continue.</p>
 */
public class WebLoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebLoginThrottleFilter.class);
    private static final RequestMatcher LOGIN_POST = new AntPathRequestMatcher("/login", "POST");

    private final LoginThrottleService throttle;
    private final ClientIpResolver clientIp;
    private final TurnstileService turnstile; // may be null: no CAPTCHA

    public WebLoginThrottleFilter(LoginThrottleService throttle, ClientIpResolver clientIp) {
        this(throttle, clientIp, null);
    }

    public WebLoginThrottleFilter(LoginThrottleService throttle, ClientIpResolver clientIp, TurnstileService turnstile) {
        this.throttle = throttle;
        this.clientIp = clientIp;
        this.turnstile = turnstile;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean block = false;
        boolean captchaFailed = false;
        try {
            if (LOGIN_POST.matches(request)) {
                String username = request.getParameter("username");
                String ip = clientIp.resolve(request);
                block = throttle.shouldBlock(LoginThrottleService.Channel.WEB, username, ip);
                if (!block && turnstile != null && throttle.captchaRequired(username, ip)) {
                    captchaFailed = turnstile.verify(request.getParameter("cf-turnstile-response"), ip)
                            == TurnstileService.Result.FAILED;
                }
            }
        } catch (RuntimeException e) {
            log.error("Web login throttle failed; allowing the request", e);
        }
        if (block) {
            response.sendRedirect(request.getContextPath() + "/login?blocked=true");
            return;
        }
        if (captchaFailed) {
            response.sendRedirect(request.getContextPath() + "/login?captcha=required");
            return;
        }
        chain.doFilter(request, response);
    }
}
