package com.smsweb.sms.controllers.student;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.ImportPreviewResult;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.universal.*;
import com.smsweb.sms.services.student.StudentImportService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Handles legacy student bulk-import from XLS.
 * Only accessible by ROLE_SUPERADMIN and ROLE_ADMIN.
 */
@Controller
@RequestMapping("/student/import")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class StudentImportController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentImportController.class);

    private static final String SESSION_FILE_KEY    = "importFileBytes";
    private static final String SESSION_PREVIEW_KEY = "importPreviewResult";
    private static final String SESSION_SCHOOL_KEY  = "importSchool";
    private static final String SESSION_ACYEAR_KEY  = "importAcademicYear";

    private final StudentImportService   importService;
    private final AcademicyearRepository academicYearRepository;
    private final SchoolRepository       schoolRepository;
    private final GradeRepository        gradeRepository;
    private final SectionRepository      sectionRepository;
    private final CastRepository         castRepository;
    private final CityRepository         cityRepository;
    private final BankRepository         bankRepository;

    public StudentImportController(StudentImportService importService,
                                   AcademicyearRepository academicYearRepository,
                                   SchoolRepository schoolRepository,
                                   GradeRepository gradeRepository,
                                   SectionRepository sectionRepository,
                                   CastRepository castRepository,
                                   CityRepository cityRepository,
                                   BankRepository bankRepository) {
        this.importService          = importService;
        this.academicYearRepository = academicYearRepository;
        this.schoolRepository       = schoolRepository;
        this.gradeRepository        = gradeRepository;
        this.sectionRepository      = sectionRepository;
        this.castRepository         = castRepository;
        this.cityRepository         = cityRepository;
        this.bankRepository         = bankRepository;
    }

    /** Step 1 — Show upload form (also shows school-picker for SUPERADMIN with no school in session) */
    @GetMapping
    public String showUploadForm(HttpSession session, Model model) {
        log.info("Inside showUploadForm");
        School school = resolveSchool(session, model);
        if (school == null) {
            model.addAttribute("allSchools", schoolRepository.findAllByStatus("Active"));
        }
        model.addAttribute("selectedSchool", school);
        model.addAttribute("page", "plain");
        return "student/studentImport";
    }

    /** SUPERADMIN school selection — stores chosen school + academic year in session */
    @PostMapping("/select-school")
    public String selectSchool(@RequestParam Long schoolId,
                               @RequestParam String sessionFormat,
                               HttpSession session,
                               RedirectAttributes ra) {
        log.info("Inside selectSchool");
        School school = schoolRepository.findById(schoolId).orElse(null);
        if (school == null) {
            ra.addFlashAttribute("error", "School not found.");
            return "redirect:/student/import";
        }
        AcademicYear ay = academicYearRepository
                .findBySessionFormatAndSchool_Id(sessionFormat.trim(), schoolId)
                .orElse(null);
        if (ay == null) {
            ra.addFlashAttribute("error",
                "Academic year '" + sessionFormat + "' not found for " + school.getSchoolName()
                + ". Please create it first.");
            return "redirect:/student/import";
        }
        session.setAttribute(SESSION_SCHOOL_KEY, school);
        session.setAttribute(SESSION_ACYEAR_KEY, ay);
        ra.addFlashAttribute("success",
            "School set to '" + school.getSchoolName() + "' | Session: " + sessionFormat);
        return "redirect:/student/import";
    }

    /** AJAX — return academic years for a given school (used to populate session dropdown) */
    @GetMapping("/academic-years")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAcademicYears(@RequestParam Long schoolId) {
        log.info("Inside getAcademicYears");
        List<AcademicYear> years = academicYearRepository.findAllBySchoolIdOrderByIdDesc(schoolId);
        List<Map<String, Object>> result = years.stream()
                .map(y -> Map.<String, Object>of(
                        "id", y.getId(),
                        "sessionFormat", y.getSessionFormat()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** Step 2 — Upload & Preview */
    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside preview");

        if (resolveSchool(session, model) == null) {
            ra.addFlashAttribute("error", "Please select a school and academic year first.");
            return "redirect:/student/import";
        }

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a .xls file to upload.");
            return "redirect:/student/import";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xls") && !filename.toLowerCase().endsWith(".xlsx"))) {
            ra.addFlashAttribute("error", "Only .xls or .xlsx files are supported.");
            return "redirect:/student/import";
        }

        try {
            byte[] fileBytes = file.getBytes();
            session.setAttribute(SESSION_FILE_KEY, fileBytes);

            ImportPreviewResult preview = importService.parseAndValidate(fileBytes);
            session.setAttribute(SESSION_PREVIEW_KEY, preview);

            School school = resolveSchool(session, model);
            AcademicYear ay = resolveAcademicYear(session, school);

            model.addAttribute("preview", preview);
            model.addAttribute("selectedSchool", school);
            model.addAttribute("selectedAcademicYear", ay);
            model.addAttribute("filename", filename);
            model.addAttribute("page", "datatable");
            return "student/studentImport";

        } catch (Exception e) {
            log.error("Error parsing import file", e);
            ra.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            return "redirect:/student/import";
        }
    }

    /** Step 3 — Execute Import */
    @PostMapping("/execute")
    public String execute(HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside execute");

        byte[] fileBytes = (byte[]) session.getAttribute(SESSION_FILE_KEY);
        if (fileBytes == null) {
            ra.addFlashAttribute("error", "Session expired. Please upload the file again.");
            return "redirect:/student/import";
        }

        School school = resolveSchool(session, model);
        if (school == null) {
            ra.addFlashAttribute("error", "School not found. Please select a school first.");
            return "redirect:/student/import";
        }

        AcademicYear academicYear = resolveAcademicYear(session, school);
        if (academicYear == null) {
            ra.addFlashAttribute("error",
                    "Academic year not found for school '" + school.getSchoolName()
                    + "'. Please select a valid session.");
            return "redirect:/student/import";
        }

        ImportPreviewResult storedPreview = (ImportPreviewResult) session.getAttribute(SESSION_PREVIEW_KEY);
        if (storedPreview != null && !storedPreview.isImportAllowed()) {
            ra.addFlashAttribute("error",
                    "Import blocked: " + storedPreview.getErrorCount()
                    + " unresolved error(s). Fix errors first.");
            return "redirect:/student/import";
        }

        try {
            ImportPreviewResult result = importService.executeImport(fileBytes, school, academicYear);
            session.removeAttribute(SESSION_FILE_KEY);
            session.removeAttribute(SESSION_PREVIEW_KEY);

            model.addAttribute("result", result);
            model.addAttribute("importDone", true);
            model.addAttribute("page", "plain");
            return "student/studentImport";

        } catch (Exception e) {
            log.error("Import execution failed", e);
            ra.addFlashAttribute("error", "Import failed: " + e.getMessage());
            return "redirect:/student/import";
        }
    }

    /** Reset session — also clears school selection */
    @GetMapping("/reset")
    public String reset(HttpSession session, RedirectAttributes ra) {
        log.info("Inside reset");
        session.removeAttribute(SESSION_FILE_KEY);
        session.removeAttribute(SESSION_PREVIEW_KEY);
        session.removeAttribute(SESSION_SCHOOL_KEY);
        session.removeAttribute(SESSION_ACYEAR_KEY);
        ra.addFlashAttribute("success", "Import session cleared.");
        return "redirect:/student/import";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns the school: import-session key first, then BaseController model attribute. */
    private School resolveSchool(HttpSession session, Model model) {
        School s = (School) session.getAttribute(SESSION_SCHOOL_KEY);
        if (s == null) s = (School) model.getAttribute("school");
        return s;
    }

    /** Returns the academic year: import-session key first, then DB lookup. */
    private AcademicYear resolveAcademicYear(HttpSession session, School school) {
        AcademicYear ay = (AcademicYear) session.getAttribute(SESSION_ACYEAR_KEY);
        if (ay == null && school != null) {
            ay = academicYearRepository
                    .findBySessionFormatAndSchool_Id("2026-2027", school.getId())
                    .orElse(null);
        }
        return ay;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quick-Add AJAX endpoints — add a single lookup record without leaving page
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/quick-add/grade")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddGrade(@RequestParam String name) {
        log.info("Inside quickAddGrade");
        return quickAdd(name, () -> {
            if (gradeRepository.findByGradeNameIgnoreCase(name.trim()).isPresent())
                return Map.of("status", "exists", "message", "Grade '" + name + "' already exists.");
            Grade g = new Grade();
            g.setGradeName(name.trim().toUpperCase());
            gradeRepository.save(g);
            return Map.of("status", "created", "message", "Grade '" + name + "' added successfully.");
        });
    }

    @PostMapping("/quick-add/section")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddSection(@RequestParam String name) {
        log.info("Inside quickAddSection");
        return quickAdd(name, () -> {
            if (sectionRepository.findBySectionNameIgnoreCase(name.trim()).isPresent())
                return Map.of("status", "exists", "message", "Section '" + name + "' already exists.");
            Section s = new Section();
            s.setSectionName(name.trim().toUpperCase());
            sectionRepository.save(s);
            return Map.of("status", "created", "message", "Section '" + name + "' added successfully.");
        });
    }

    @PostMapping("/quick-add/cast")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddCast(@RequestParam String name) {
        log.info("Inside quickAddCast");
        return quickAdd(name, () -> {
            if (castRepository.findByCastNameIgnoreCase(name.trim()).isPresent())
                return Map.of("status", "exists", "message", "Caste '" + name + "' already exists.");
            Cast c = new Cast();
            c.setCastName(name.trim().toUpperCase());
            castRepository.save(c);
            return Map.of("status", "created", "message", "Caste '" + name + "' added successfully.");
        });
    }

    @PostMapping("/quick-add/city")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddCity(@RequestParam String name) {
        log.info("Inside quickAddCity");
        return quickAdd(name, () -> {
            if (cityRepository.findByCityNameIgnoreCase(name.trim()).isPresent())
                return Map.of("status", "exists", "message", "City '" + name + "' already exists.");
            City c = new City();
            c.setCityName(name.trim().toUpperCase());
            cityRepository.save(c);
            return Map.of("status", "created", "message", "City '" + name + "' added successfully.");
        });
    }

    @PostMapping("/quick-add/bank")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddBank(@RequestParam String name) {
        log.info("Inside quickAddBank");
        return quickAdd(name, () -> {
            if (bankRepository.findByBankNameIgnoreCase(name.trim()).isPresent())
                return Map.of("status", "exists", "message", "Bank '" + name + "' already exists.");
            Bank b = new Bank();
            b.setBankName(name.trim().toUpperCase());
            bankRepository.save(b);
            return Map.of("status", "created", "message", "Bank '" + name + "' added successfully.");
        });
    }

    /** Shared wrapper for quick-add calls. */
    private ResponseEntity<Map<String, Object>> quickAdd(String name,
            java.util.concurrent.Callable<Map<String, Object>> action) {
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Name cannot be empty."));
        try {
            return ResponseEntity.ok(action.call());
        } catch (Exception e) {
            log.error("Quick-add failed for '{}': {}", name, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed: " + e.getMessage()));
        }
    }
}
