// PermissionAspect.java
package com.smsweb.sms.config.permission;

import com.smsweb.sms.services.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.lang.reflect.Method;

@Aspect @Component
public class PermissionAspect {

    @Autowired private PermissionService permissionService;

    @Around("@annotation(CheckAccess)")
    public Object checkAccess(ProceedingJoinPoint jp) throws Throwable {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        CheckAccess ann = method.getAnnotation(CheckAccess.class);

        if (!permissionService.hasAccess(ann.screen(), ann.type())) {
            HttpServletRequest req = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            HttpServletResponse res = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getResponse();

            // AJAX / REST calls get JSON 403
            String accept = req.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                res.setStatus(HttpStatus.FORBIDDEN.value());
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Access denied\",\"screen\":\"" + ann.screen() + "\"}");
                return null;
            }
            // Browser calls get redirected
            return "redirect:/access-denied";
        }
        return jp.proceed();
    }
}
