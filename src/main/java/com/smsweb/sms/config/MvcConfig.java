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
        employeeImageDirectory+="/";
        studentImageDirectory+="/";
        String resourceLocation = "file:///" + employeeImageDirectory.replace("\\","/");
        String studentResourceLocation = "file:///" + studentImageDirectory.replace("\\","/");
        registry.addResourceHandler("/images/employees/**")
                .addResourceLocations(resourceLocation);

        registry.addResourceHandler("/images/students/**")
                .addResourceLocations(studentResourceLocation);
    }
}
