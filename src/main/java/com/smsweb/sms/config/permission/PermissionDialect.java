// PermissionDialect.java
package com.smsweb.sms.config.permission;

import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.permission.PermissionService;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Usage in HTML:  sec:access="STUDENT_ADD:CREATE"
 * Removes the element entirely if user lacks that access.
 */
public class PermissionDialect extends AbstractAttributeTagProcessor {

    private final PermissionService permissionService;

    public PermissionDialect(PermissionService permissionService) {
        super(
                TemplateMode.HTML,  // 1 — template mode
                "sms",              // 2 — dialect prefix
                null,               // 3 — element name  (null = any element)
                false,              // 4 — prefix element name
                "access",           // 5 — attribute name
                true,               // 6 — prefix attribute name  →  sec:access
                300,                // 7 — precedence
                true                // 8 — remove the attribute from output HTML
        );
        this.permissionService = permissionService;
    }

    @Override
    protected void doProcess(ITemplateContext context,
                             IProcessableElementTag tag,
                             AttributeName attributeName,
                             String attributeValue,
                             IElementTagStructureHandler structureHandler) {

        // attributeValue format:  "SCREEN_KEY:ACCESS_TYPE"
        String[] parts = attributeValue.split(":");
        if (parts.length != 2) {
            // Malformed value — fail safe: hide the element
            structureHandler.removeElement();
            return;
        }

        String screenKey = parts[0].trim();
        AccessType requiredType;
        try {
            requiredType = AccessType.valueOf(parts[1].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Unknown access type — fail safe: hide the element
            structureHandler.removeElement();
            return;
        }

        if (!permissionService.hasAccess(screenKey, requiredType)) {
            structureHandler.removeElement();
        }
        // If access is granted: do nothing — element renders, attribute is auto-removed
        // because removeAttribute = true was passed to the super() constructor.
    }
}
