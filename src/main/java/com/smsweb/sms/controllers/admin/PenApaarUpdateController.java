package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.PenApaarUpdatePreviewResult;
import com.smsweb.sms.services.student.PenApaarUpdateService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bulk-updates Student.apaarId from an uploaded sheet, matched by PEN No (see
 * StudentRepository.findAllByPenNo — PEN No has no DB uniqueness constraint, so an ambiguous
 * match is possible and is never guessed). Super-admin only.
 *
 * Fully separate from PsrnUpdateController / StudentImportController — no shared code path.
 * Unlike the PSRN-based flow, only PEN No is used to match (Student Name/Gender/DOB/Mobile/
 * Class/Section are shown for context only, never for matching), so there is only one target
 * field (Apaar ID) and no mismatch-confirmation step.
 */
@Controller
@RequestMapping("/admin/pen-apaar-update")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class PenApaarUpdateController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(PenApaarUpdateController.class);

    private static final String SESSION_FILE_KEY    = "penApaarUpdateFileBytes";
    private static final String SESSION_PREVIEW_KEY = "penApaarUpdatePreviewResult";

    private final PenApaarUpdateService penApaarUpdateService;

    public PenApaarUpdateController(PenApaarUpdateService penApaarUpdateService) {
        this.penApaarUpdateService = penApaarUpdateService;
    }

    /** Step 1 — upload form. */
    @GetMapping
    public String showUploadForm(Model model) {
        log.info("Inside showUploadForm");
        model.addAttribute("page", "plain");
        return "admin/pen-apaar-update";
    }

    /** Step 2 — parse & preview. */
    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside preview");

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a .xls or .xlsx file to upload.");
            return "redirect:/admin/pen-apaar-update";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xls") && !filename.toLowerCase().endsWith(".xlsx"))) {
            ra.addFlashAttribute("error", "Only .xls or .xlsx files are supported.");
            return "redirect:/admin/pen-apaar-update";
        }

        try {
            byte[] fileBytes = file.getBytes();
            session.setAttribute(SESSION_FILE_KEY, fileBytes);

            PenApaarUpdatePreviewResult preview = penApaarUpdateService.parseAndValidate(fileBytes);
            session.setAttribute(SESSION_PREVIEW_KEY, preview);

            model.addAttribute("preview", preview);
            model.addAttribute("filename", filename);
            model.addAttribute("page", "datatable");
            return "admin/pen-apaar-update";

        } catch (Exception e) {
            log.error("Error parsing Apaar ID update file", e);
            ra.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            return "redirect:/admin/pen-apaar-update";
        }
    }

    /** Step 3 — execute. */
    @PostMapping("/execute")
    public String execute(HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside execute");

        byte[] fileBytes = (byte[]) session.getAttribute(SESSION_FILE_KEY);
        if (fileBytes == null) {
            ra.addFlashAttribute("error", "Session expired. Please upload the file again.");
            return "redirect:/admin/pen-apaar-update";
        }

        try {
            PenApaarUpdatePreviewResult result = penApaarUpdateService.executeUpdate(fileBytes);
            session.removeAttribute(SESSION_FILE_KEY);
            session.removeAttribute(SESSION_PREVIEW_KEY);

            model.addAttribute("result", result);
            model.addAttribute("updateDone", true);
            model.addAttribute("page", "datatable");
            return "admin/pen-apaar-update";

        } catch (Exception e) {
            log.error("Apaar ID update execution failed", e);
            ra.addFlashAttribute("error", "Update failed: " + e.getMessage());
            return "redirect:/admin/pen-apaar-update";
        }
    }

    /** Reset session. */
    @GetMapping("/reset")
    public String reset(HttpSession session, RedirectAttributes ra) {
        log.info("Inside reset");
        session.removeAttribute(SESSION_FILE_KEY);
        session.removeAttribute(SESSION_PREVIEW_KEY);
        ra.addFlashAttribute("success", "Session cleared.");
        return "redirect:/admin/pen-apaar-update";
    }
}
