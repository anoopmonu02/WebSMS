package com.smsweb.sms.config;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
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
import java.util.Optional;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    AcademicyearRepository academicyearRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // Access the logged-in user's details
        System.out.println("CustomAuthenticationSuccessHandler called");
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        System.out.println("username--"+username);
        Optional<Employee> employee = employeeRepository.findByUsername(username);
        // If Employee is a UserDetails implementation, cast it
        HttpSession session = request.getSession();
        if (employee.isPresent()) {


            // You can directly access properties of Employee
            // For example, get school or any other details
            School school = employee.get().getSchool(); // Assume you have a getSchool() method in Employee



            session.setAttribute("school", school);


            // Fetch the active academic year if needed
            // Note: You would need to have another service or method to get the academic year
        }
        else{
            session.setAttribute("username",username);
        }
        AcademicYear academicYear = academicyearRepository.findTopByStatusOrderByIdDesc("active");
        session.setAttribute("activeAcademicYear", academicYear);

        // Redirect to the default success URL
        response.sendRedirect("/dashboard");
    }
}
