package com.smsweb.sms.config;


import com.smsweb.sms.config.permission.PermissionDialectRegistrar;
import com.smsweb.sms.services.permission.PermissionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
public class ThymeleafConfig {

    /*@Bean
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
        templateResolver.setPrefix("classpath:/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        return templateResolver;
    }*/

    /*@Bean
    public SpringTemplateEngine templateEngine(SpringResourceTemplateResolver templateResolver) {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine;
    }*/
    @Autowired
    private SpringTemplateEngine templateEngine;  // auto-configured by Spring Boot

    @Autowired
    private PermissionService permissionService;

    @PostConstruct
    public void addPermissionDialect() {
        // Spring Security dialect is already registered automatically by
        // thymeleaf-extras-springsecurity6 via its own auto-configuration.
        // We only need to add our custom sms: dialect.
        templateEngine.addDialect(new PermissionDialectRegistrar(permissionService));
    }
}

