package com.smsweb.sms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Value("${employee.image.storage.path}")
    private String employeeImageDirectory;

    @Value("${student.image.storage.path}")
    private String studentImageDirectory;
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/employees/**")
                .addResourceLocations(toFileUrl(employeeImageDirectory));

        registry.addResourceHandler("/images/students/**")
                .addResourceLocations(toFileUrl(studentImageDirectory));
    }

    /**
     * Converts a filesystem path to a Spring resource location URL.
     * Handles both Windows (C:/...) and Linux (/data/...) paths correctly.
     * Windows: file:///C:/path/   Linux: file:/data/path/
     */
    private String toFileUrl(String path) {
        String normalized = path.replace("\\", "/");
        if (!normalized.endsWith("/")) normalized += "/";
        // Windows absolute path starts with drive letter (e.g. C:/)
        if (normalized.length() > 1 && normalized.charAt(1) == ':') {
            return "file:///" + normalized;
        }
        // Linux/Unix absolute path starts with /
        return "file:" + normalized;
    }
}
