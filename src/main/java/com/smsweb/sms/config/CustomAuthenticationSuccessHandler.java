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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

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
        // Access the logged-in user's details
        System.out.println("CustomAuthenticationSuccessHandler called");

        Object principal = authentication.getPrincipal();
        String username;
        UserEntity userEntity = null;
        Employee employee = null;

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }

        // Fetch UserEntity and Employee
        try {
            userEntity = userRepository.findByUsername(username);
            if (userEntity != null) {
                employee = employeeRepository.findByUserEntity(userEntity);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception or handle it as necessary
        }

        // Access the session
        HttpSession session = request.getSession();
        School school = null;
        if (employee != null && !employee.getUserEntity().getRoles().contains("ROLE_SUPERADMIN")) {
            // Set employee details in the session
            school = employee.getSchool(); // Ensure that getSchool() is a valid method
            session.setAttribute("school", school);
        } else {
            // If no employee found, store the username in the session
            session.setAttribute("username", username);
        }

        if(school!=null){
            // Fetch or create the active academic year
            AcademicYear academicYear = academicyearRepository.findTopByStatusOrderByIdDesc("active");
            if(academicYear == null){
                academicYear = academicYearService.saveAcademicYearIfNotFound();
            }
            if(academicYear!=null){
                session.setAttribute("activeAcademicYear", academicYear);
            }

        }

        // Redirect to the default success URL
        response.sendRedirect("/dashboard");
    }



}
