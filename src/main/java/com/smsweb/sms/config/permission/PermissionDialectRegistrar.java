package com.smsweb.sms.config.permission;

import com.smsweb.sms.services.permission.PermissionService;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

import java.util.HashSet;
import java.util.Set;

/**
 * Thymeleaf IDialect wrapper that registers PermissionDialect as a processor
 * under the "sec" prefix.
 *
 * Register in ThymeleafConfig:
 *
 *   @Autowired
 *   private PermissionService permissionService;
 *
 *   @Bean
 *   public SpringTemplateEngine templateEngine(ISpringTemplateEngineConfiguration config) {
 *       SpringTemplateEngine engine = new SpringTemplateEngine();
 *       engine.setEnableSpringELCompiler(true);
 *       engine.addDialect(new SpringSecurityDialect());
 *       engine.addDialect(new PermissionDialectRegistrar(permissionService));
 *       return engine;
 *   }
 *
 * NOTE: If you let Spring Boot auto-configure Thymeleaf you may already have a
 * SpringTemplateEngine bean.  In that case inject it and call addDialect() in a
 * @PostConstruct or implement a ThymeleafConfigurer instead of replacing the bean.
 */
public class PermissionDialectRegistrar extends AbstractProcessorDialect {

    private final PermissionService permissionService;

    /** Dialect name shown in Thymeleaf debug output. */
    private static final String DIALECT_NAME = "SMS Permission Dialect";

    /** Must match the prefix used in PermissionDialect constructor ("sec"). */
    private static final String PREFIX = "sms";

    /** Standard Thymeleaf processor dialect precedence. */
    private static final int PRECEDENCE = 1000;

    public PermissionDialectRegistrar(PermissionService permissionService) {
        super(DIALECT_NAME, PREFIX, PRECEDENCE);
        this.permissionService = permissionService;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new HashSet<>();
        processors.add(new PermissionDialect(permissionService));
        return processors;
    }
}
