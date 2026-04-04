package com.smsweb.sms.auth;

import com.smsweb.sms.config.JwtService;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.users.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class JwtAuthController  {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;


    public JwtAuthController(AuthenticationManager authenticationManager, UserDetailsService userDetailsService, JwtService jwtService, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Transactional
    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody AuthRequest request) {
        System.out.println(">>> username: " + request.getUsername());
        System.out.println(">>> password: " + request.getPassword());

        if (request.getUsername() == null || request.getUsername().isBlank() ||
                request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.ok(new AuthResponse(
                    null, null, 0, null,
                    null, null, null,
                    null, null, null,
                    0, "Username and password are required"
            ));
        }


        // 1. Validate credentials
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.ok(new AuthResponse(
                    null, null, 0, null,
                    null, null, null,
                    null, null, null,
                    0, "Invalid username or password"
            ));
        }

        // 2. Load Spring UserDetails → used for token generation only


        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtService.generateToken(userDetails);
        UserEntity user = userRepository.findByUsernameWithDetails(request.getUsername());

        // 4. Extract roles as plain strings
        String role = user.getRoles().stream()
                .map(r -> r.getName()
                        .replace("ROLE_", ""))   // "ROLE_ADMIN" → "Admin"
                .findFirst()
                .orElse("Unknown");

        // 5. Determine user type
        Long   customerId  = null;   // → school_id in response (Customer.id)
        Long   schoolId    = null;   // → branch_id in response (School.id)
        Long   sessionId   = null;   // → session_id in response (AcademicYear.id)
        String displayName = user.getUsername();

        if (user.getEmployee() != null) {
            Employee emp   = user.getEmployee();
            displayName    = emp.getEmployeeName();

            if (emp.getSchool() != null) {
                schoolId   = emp.getSchool().getId();                    // School.id → branch_id

                if (emp.getSchool().getCustomer() != null) {
                    customerId = emp.getSchool().getCustomer().getId();  // Customer.id → school_id
                }
            }
            // employees not tied to academicYear — sessionId stays null

        } else if (user.getStudent() != null) {
            Student stu    = user.getStudent();
            displayName    = stu.getStudentName();

            if (stu.getSchool() != null) {
                schoolId   = stu.getSchool().getId();                    // School.id → branch_id

                if (stu.getSchool().getCustomer() != null) {
                    customerId = stu.getSchool().getCustomer().getId();  // Customer.id → school_id
                }
            }

            if (stu.getAcademicYear() != null) {
                sessionId  = stu.getAcademicYear().getId();              // AcademicYear.id → session_id
            }
        }

        // 6. Build and return response
        return ResponseEntity.ok(new AuthResponse(
                token,
                "Bearer",
                480L,
                null,           // refresh_token — future use
                user.getId(),
                displayName,
                role,
                customerId,     // school_id  = Customer.id
                schoolId,       // branch_id  = School.id
                sessionId,      // session_id = AcademicYear.id
                1,
                null
        ));
    }
}
