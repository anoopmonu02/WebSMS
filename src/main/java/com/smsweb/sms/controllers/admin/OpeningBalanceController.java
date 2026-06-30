package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.dto.OpeningBalanceRowDto;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.services.fees.OpeningBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Previous / Opening Balance Upload — Super Admin only.
 *
 * GET  /admin/opening-balance            → Step 1: school selector + file upload
 * POST /admin/opening-balance/preview    → Step 2: parsed preview table
 * POST /admin/opening-balance/save       → Step 3: save confirmed rows + result
 */
@Controller
@RequestMapping("/admin/opening-balance")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class OpeningBalanceController {

    private static final Logger log = LoggerFactory.getLogger(OpeningBalanceController.class);

    private final OpeningBalanceService openingBalanceService;
    private final SchoolRepository schoolRepository;
    private final AcademicyearRepository academicyearRepository;

    public OpeningBalanceController(OpeningBalanceService openingBalanceService,
                                    SchoolRepository schoolRepository,
                                    AcademicyearRepository academicyearRepository) {
        this.openingBalanceService = openingBalanceService;
        this.schoolRepository = schoolRepository;
        this.academicyearRepository = academicyearRepository;
    }

    @GetMapping
    public String showPage(Model model) {
        model.addAttribute("schools", schoolRepository.findAllByStatus("Active"));
        model.addAttribute("page", "plain");
        return "admin/openingBalance";
    }

    /** AJAX — returns active academic year id + sessionFormat for a school (lean, no lazy relations) */
    @GetMapping("/active-year")
    @ResponseBody
    public Map<String, Object> getActiveYear(@RequestParam Long schoolId) {
        AcademicYear ay = academicyearRepository.findActiveBySchoolId(schoolId);
        if (ay == null) return Map.of();
        return Map.of("id", ay.getId(), "sessionFormat", ay.getSessionFormat());
    }

    @PostMapping("/preview")
    public String preview(@RequestParam Long schoolId,
                          @RequestParam Long academicYearId,
                          @RequestParam MultipartFile file,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        model.addAttribute("schools", schoolRepository.findAllByStatus("Active"));
        model.addAttribute("page", "plain");

        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Please select an Excel file to upload.");
            return "admin/openingBalance";
        }

        try {
            School school = schoolRepository.findById(schoolId).orElse(null);
            AcademicYear ay = academicyearRepository.findById(academicYearId).orElse(null);

            List<OpeningBalanceRowDto> rows = openingBalanceService.preview(file, schoolId, academicYearId);

            long matched  = rows.stream().filter(OpeningBalanceRowDto::isMatched).count();
            long mismatch = rows.stream().filter(OpeningBalanceRowDto::isMismatch).count();
            long notFound = rows.stream().filter(OpeningBalanceRowDto::isNotFound).count();

            model.addAttribute("rows",        rows);
            model.addAttribute("school",      school);
            model.addAttribute("academicYear", ay);
            model.addAttribute("schoolId",    schoolId);
            model.addAttribute("academicYearId", academicYearId);
            model.addAttribute("totalRows",   rows.size());
            model.addAttribute("matched",     matched);
            model.addAttribute("mismatch",    mismatch);
            model.addAttribute("notFound",    notFound);

        } catch (Exception e) {
            log.error("Opening balance preview failed", e);
            model.addAttribute("error", "Failed to parse file: " + e.getMessage());
        }
        return "admin/openingBalance";
    }

    @PostMapping("/save")
    public String save(@RequestParam Long schoolId,
                       @RequestParam Long academicYearId,
                       @RequestParam(required = false) List<Long> academicStudentIds,
                       @RequestParam(required = false) List<String> amounts,
                       @RequestParam(required = false) List<String> remarks,
                       Model model) {
        model.addAttribute("schools", schoolRepository.findAllByStatus("Active"));
        model.addAttribute("page", "plain");

        if (academicStudentIds == null || academicStudentIds.isEmpty()) {
            model.addAttribute("error", "No matched rows to save.");
            return "admin/openingBalance";
        }

        try {
            School school = schoolRepository.findById(schoolId).orElse(null);
            AcademicYear ay = academicyearRepository.findById(academicYearId).orElse(null);
            int[] result = openingBalanceService.save(academicStudentIds, amounts, remarks);

            model.addAttribute("resultSaved",   result[0]);
            model.addAttribute("resultSkipped", result[1]);
            model.addAttribute("resultErrors",  result[2]);
            model.addAttribute("school",        school);
            model.addAttribute("academicYear",  ay);
            log.info("Opening balance saved — school={}, ay={}, saved={}, skipped={}, errors={}",
                     schoolId, academicYearId, result[0], result[1], result[2]);
        } catch (Exception e) {
            log.error("Opening balance save failed", e);
            model.addAttribute("error", "Save failed: " + e.getMessage());
        }
        return "admin/openingBalance";
    }
}
