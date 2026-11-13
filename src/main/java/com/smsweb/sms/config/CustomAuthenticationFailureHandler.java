package com.smsweb.sms.config;

import com.smsweb.sms.services.security.ClientIpResolver;
import com.smsweb.sms.services.security.LoginThrottleService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);

    private final LoginThrottleService throttle;
    private final ClientIpResolver clientIp;

    /** Kept so any code that still creates the handler without the throttle keeps working (no counting then). */
    public CustomAuthenticationFailureHandler() {
        this(null, null);
    }

    public CustomAuthenticationFailureHandler(LoginThrottleService throttle, ClientIpResolver clientIp) {
        this.throttle = throttle;
        this.clientIp = clientIp;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.info("Inside onAuthenticationFailure");
        request.getSession().setAttribute("errorMessage", exception.getMessage());
        log.warn("Authentication failed: {}", exception.getMessage());
        recordFailure(request);
        response.sendRedirect(request.getContextPath() + "/login?error=true" + (captchaNowRequired(request) ? "&captcha=true" : ""));
    }

    /** After several wrong attempts the sign-in page shows the CAPTCHA (only when it is switched on). */
    private boolean captchaNowRequired(HttpServletRequest request) {
        if (throttle == null || clientIp == null) {
            return false;
        }
        try {
            return throttle.captchaRequired(request.getParameter("username"), clientIp.resolve(request));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Counts the wrong attempt for the throttle. Never allowed to break the sign-in page. */
    private void recordFailure(HttpServletRequest request) {
        if (throttle == null || clientIp == null) {
            return;
        }
        try {
            throttle.recordFailure(LoginThrottleService.Channel.WEB, request.getParameter("username"),
                    request.getParameter("password"), clientIp.resolve(request));
        } catch (RuntimeException e) {
            log.error("Could not record the failed sign-in for throttling", e);
        }
    }


}
