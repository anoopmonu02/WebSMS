package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.dto.FamilyGroupPreview;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Family Account Migration — Super Admin only.
 *
 * GET  /admin/family-migration          → Step 1: scan button
 * POST /admin/family-migration/scan     → Step 2: preview groups
 * POST /admin/family-migration/execute  → Step 3: run migration + result
 */
@Controller
@RequestMapping("/admin/family-migration")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final FamilyAccountService familyAccountService;

    public MigrationController(FamilyAccountService familyAccountService) {
        this.familyAccountService = familyAccountService;
    }

    @GetMapping
    public String showPage(Model model) {
        log.info("Inside showPage");
        model.addAttribute("page", "plain");
        return "admin/familyMigration";
    }

    @PostMapping("/scan")
    public String scan(Model model) {
        log.info("Inside scan");
        try {
            List<FamilyGroupPreview> groups = familyAccountService.scanFamilyGroups();
            int totalGroups   = groups.size();
            int totalStudents = groups.stream().mapToInt(FamilyGroupPreview::getTotalStudents).sum();
            int needsLink     = groups.stream().mapToInt(FamilyGroupPreview::getNeedsLink).sum();
            int alreadyLinked = groups.stream().mapToInt(FamilyGroupPreview::getAlreadyLinked).sum();
            model.addAttribute("groups",        groups);
            model.addAttribute("totalGroups",   totalGroups);
            model.addAttribute("totalStudents", totalStudents);
            model.addAttribute("needsLink",     needsLink);
            model.addAttribute("alreadyLinked", alreadyLinked);
        } catch (Exception e) {
            log.error("Family migration scan error", e);
            model.addAttribute("error", "Scan failed: " + e.getMessage());
        }
        model.addAttribute("page", "plain");
        return "admin/familyMigration";
    }

    @PostMapping("/execute")
    public String execute(Model model) {
        log.info("Inside execute");
        int created = 0, alreadyExisted = 0, errors = 0;
        List<String> errorList = new ArrayList<>();
        try {
            List<FamilyGroupPreview> groups = familyAccountService.scanFamilyGroups();
            for (FamilyGroupPreview group : groups) {
                try {
                    boolean existed = familyAccountService.findByMobile(group.getMobile()).isPresent();
                    familyAccountService.createIfAbsent(group.getMobile());
                    if (existed) alreadyExisted++;
                    else         created++;
                } catch (Exception e) {
                    errors++;
                    errorList.add("Mobile " + group.getMobile() + ": " + e.getMessage());
                    log.error("Family migration error for {}: {}", group.getMobile(), e.getMessage());
                }
            }
            model.addAttribute("result", Map.of(
                "totalGroups",    groups.size(),
                "created",        created,
                "alreadyExisted", alreadyExisted,
                "errors",         errors,
                "errorList",      errorList
            ));
            log.info("Family migration done — created={}, alreadyExisted={}, errors={}", created, alreadyExisted, errors);
        } catch (Exception e) {
            log.error("Family migration execute error", e);
            model.addAttribute("error", "Migration failed: " + e.getMessage());
        }
        model.addAttribute("page", "plain");
        return "admin/familyMigration";
    }
}
