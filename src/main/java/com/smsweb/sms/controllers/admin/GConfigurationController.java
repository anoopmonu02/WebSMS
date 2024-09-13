package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.helper.FieldUtils;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.admin.GConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/admin")
public class GConfigurationController {
    private final GConfigurationService configurationService;

    @Autowired
    public GConfigurationController(GConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/select-columns")
    public String getColumns(Model model) {
        // Use reflection utility to get field names and labels
        Map<String, String> fieldLabels = FieldUtils.getFieldLabels(Student.class);

        // Add the field map to the model
        model.addAttribute("fieldLabels", fieldLabels);

        // Return the Thymeleaf template
        return "/admin/selectColumns";
    }
}
