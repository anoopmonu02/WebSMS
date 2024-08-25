package com.smsweb.sms.config;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class RoleInitializer {

    @Bean
    public CommandLineRunner initializeRoles(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (roleRepository.findByName("ROLE_ACCOUNTENT") == null) {
                roleRepository.save(new Roles("ROLE_ACCOUNTENT"));
            }
            if (roleRepository.findByName("ROLE_TEACHER") == null) {
                roleRepository.save(new Roles("ROLE_TEACHER"));
            }
            if (roleRepository.findByName("ROLE_STUDENT") == null) {
                roleRepository.save(new Roles("ROLE_STUDENT"));
            }
            if (roleRepository.findByName("ROLE_ADMIN") == null) {
                roleRepository.save(new Roles("ROLE_ADMIN"));
            }
            if (roleRepository.findByName("ROLE_SUPERADMIN") == null) {
                roleRepository.save(new Roles("ROLE_SUPERADMIN"));
            }
            Roles superAdminRole = roleRepository.findByName("ROLE_SUPERADMIN");
            if (superAdminRole == null) {
                superAdminRole = new Roles("ROLE_SUPERADMIN");
                roleRepository.save(superAdminRole);
            }
            if (userRepository.findByUsername("super_admin") == null) {
                Employee superAdmin = new Employee();
                superAdmin.setUsername("super_admin");
                superAdmin.setPassword(passwordEncoder.encode("password"));
                superAdmin.setEmail("anoopmonu02@gmail.com");
                superAdmin.setEnabled(true);
                superAdmin.setEmployeeCode("SUPER_ADMIN");
                superAdmin.getRoles().add(superAdminRole);
                superAdmin.setAddress("ADDRESS1");
                superAdmin.setEmployeeName("Super Admin");

                userRepository.save(superAdmin);
            }
        };
    }
}
