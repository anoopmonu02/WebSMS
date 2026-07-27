package com.smsweb.sms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Previously registered "/images/employees/**" and "/images/students/**" as
 * PUBLIC static resource mappings straight onto the filesystem folders that
 * hold student/employee photos — combined with the permitAll rule in
 * WebSecurityConfig, that meant anyone (no login required) could view any
 * student or employee's photo just by guessing/knowing the filename.
 *
 * Removed as a security fix. Photos are now served exclusively through the
 * existing authenticated, permission-checked (@CheckAccess) endpoints:
 *   GET /student/images/{filename}   (StudentController)
 *   GET /employee/images/{filename}  (EmployeeController)
 * which also sanitise the filename against path traversal.
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // No custom static resource mappings.
    }
}
