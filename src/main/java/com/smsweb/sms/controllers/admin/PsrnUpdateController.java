package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.PsrnUpdatePreviewResult;
import com.smsweb.sms.services.student.PsrnBulkUpdateService;
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
 * Bulk-updates Student.penNo / Student.apaarId from an uploaded sheet, matched by PSRN
 * (globally unique — see StudentRepository.findByPsrn). Super-admin only.
 *
 * Fully separate from StudentImportController — no shared code path, no school/academic-year
 * context needed since PSRN alone identifies the student.
 */
@Controller
@RequestMapping("/admin/psrn-update")
@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
public class PsrnUpdateController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(PsrnUpdateController.class);

    private static final String SESSION_FILE_KEY    = "psrnUpdateFileBytes";
    private static final String SESSION_TARGET_KEY   = "psrnUpdateTargetField";
    private static final String SESSION_PREVIEW_KEY  = "psrnUpdatePreviewResult";

    private final PsrnBulkUpdateService psrnBulkUpdateService;

    public PsrnUpdateController(PsrnBulkUpdateService psrnBulkUpdateService) {
        this.psrnBulkUpdateService = psrnBulkUpdateService;
    }

    /** Step 1 — upload form. */
    @GetMapping
    public String showUploadForm(Model model) {
        log.info("Inside showUploadForm");
        model.addAttribute("page", "plain");
        return "admin/psrn-update";
    }

    /** Step 2 — parse & preview. */
    @PostMapping("/preview")
    public String preview(@RequestParam("targetField") String targetField,
                          @RequestParam("file") MultipartFile file,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside preview - targetField={}", targetField);

        if (!PsrnBulkUpdateService.FIELD_PEN_NO.equals(targetField)
                && !PsrnBulkUpdateService.FIELD_APAAR_ID.equals(targetField)) {
            ra.addFlashAttribute("error", "Please select PEN No or Apaar ID.");
            return "redirect:/admin/psrn-update";
        }

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a .xls or .xlsx file to upload.");
            return "redirect:/admin/psrn-update";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xls") && !filename.toLowerCase().endsWith(".xlsx"))) {
            ra.addFlashAttribute("error", "Only .xls or .xlsx files are supported.");
            return "redirect:/admin/psrn-update";
        }

        try {
            byte[] fileBytes = file.getBytes();
            session.setAttribute(SESSION_FILE_KEY, fileBytes);
            session.setAttribute(SESSION_TARGET_KEY, targetField);

            PsrnUpdatePreviewResult preview = psrnBulkUpdateService.parseAndValidate(fileBytes, targetField);
            session.setAttribute(SESSION_PREVIEW_KEY, preview);

            model.addAttribute("preview", preview);
            model.addAttribute("filename", filename);
            model.addAttribute("page", "datatable");
            return "admin/psrn-update";

        } catch (Exception e) {
            log.error("Error parsing PSRN update file", e);
            ra.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            return "redirect:/admin/psrn-update";
        }
    }

    /** Step 3 — execute. includeWarnings=true only when the user confirmed the mismatch modal. */
    @PostMapping("/execute")
    public String execute(@RequestParam(value = "includeWarnings", defaultValue = "false") boolean includeWarnings,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {
        log.info("Inside execute - includeWarnings={}", includeWarnings);

        byte[] fileBytes = (byte[]) session.getAttribute(SESSION_FILE_KEY);
        String targetField = (String) session.getAttribute(SESSION_TARGET_KEY);
        if (fileBytes == null || targetField == null) {
            ra.addFlashAttribute("error", "Session expired. Please upload the file again.");
            return "redirect:/admin/psrn-update";
        }

        try {
            PsrnUpdatePreviewResult result = psrnBulkUpdateService.executeUpdate(fileBytes, targetField, includeWarnings);
            session.removeAttribute(SESSION_FILE_KEY);
            session.removeAttribute(SESSION_TARGET_KEY);
            session.removeAttribute(SESSION_PREVIEW_KEY);

            model.addAttribute("result", result);
            model.addAttribute("updateDone", true);
            model.addAttribute("page", "datatable");
            return "admin/psrn-update";

        } catch (Exception e) {
            log.error("PSRN update execution failed", e);
            ra.addFlashAttribute("error", "Update failed: " + e.getMessage());
            return "redirect:/admin/psrn-update";
        }
    }

    /** Reset session. */
    @GetMapping("/reset")
    public String reset(HttpSession session, RedirectAttributes ra) {
        log.info("Inside reset");
        session.removeAttribute(SESSION_FILE_KEY);
        session.removeAttribute(SESSION_TARGET_KEY);
        session.removeAttribute(SESSION_PREVIEW_KEY);
        ra.addFlashAttribute("success", "Session cleared.");
        return "redirect:/admin/psrn-update";
    }
}
