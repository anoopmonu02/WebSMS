package com.smsweb.sms.config;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.admin.AcademicyearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    AcademicyearRepository academicyearRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AcademicyearService academicYearService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info("Inside onAuthenticationSuccess");

        // ── STUDENT login → restricted portal only ────────────────────────────────
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));

        if (isStudent) {
            response.sendRedirect(request.getContextPath() + "/student-portal/home");
            return;
        }

        // ── Employee / Admin / Teacher / Accountant login ─────────────────────────
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        UserEntity userEntity = userRepository.findByUsername(username);
        Employee employee = null;

        try {
            if (userEntity != null) {
                employee = employeeRepository.findByUserEntity(userEntity);
            }
        } catch (Exception e) {
            log.error("Failed to resolve employee for logged-in user", e);
        }

        HttpSession session = request.getSession();
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

        if (employee != null && !isSuperAdmin) {
            School school = employee.getSchool();
            session.setAttribute("school", school);

            if (school != null) {
                // Bounded to LIMIT 1 (findTopBy...) so a school with more than one AcademicYear
                // row marked active can never throw NonUniqueResultException here.
                AcademicYear academicYear = academicyearRepository.findTopByStatusAndSchool_IdOrderByIdDesc("active", school.getId());
                if (academicYear == null) {
                    academicYear = academicyearRepository.findTopByStatusAndSchool_IdOrderByIdDesc("Active", school.getId());
                }
                if (academicYear == null) {
                    academicYear = academicYearService.saveAcademicYearIfNotFound();
                }
                if (academicYear != null) {
                    session.setAttribute("activeAcademicYear", academicYear);
                }
            }
        } else {
            session.setAttribute("username", username);
        }

        // Direct redirect — no SavedRequestAwareAuthenticationSuccessHandler complexity.
        // We use NullRequestCache so there are no saved requests to restore anyway.
        // response.sendRedirect() with a context-path-prefixed URL is the simplest and
        // most reliable approach — no singleton state mutation, no redirect chain.
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

}
