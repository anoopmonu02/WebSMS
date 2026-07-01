package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.helper.FieldUtils;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.admin.GConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class GConfigurationController {
    private static final Logger log = LoggerFactory.getLogger(GConfigurationController.class);

    private final GConfigurationService configurationService;

    @Autowired
    public GConfigurationController(GConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @CheckAccess(screen = "ADMIN_REPORT_SETTINGS", type = AccessType.VIEW)
    @GetMapping("/select-columns")
    public String getColumns(Model model) {
        log.info("Inside getColumns");
        // Use reflection utility to get field names and labels
        Map<String, String> fieldLabels = FieldUtils.getFieldLabels(Student.class);

        // Add the field map to the model
        model.addAttribute("fieldLabels", fieldLabels);

        // Return the Thymeleaf template
        return "admin/selectColumns";
    }
}
