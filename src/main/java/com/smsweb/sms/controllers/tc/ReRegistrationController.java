package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.tc.ReRegisterInfo;
import com.smsweb.sms.dto.tc.ReRegistrationResult;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.tc.ReRegistrationService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Registering a student again in a later session: a search page, the registration form pre-filled from
 * the old record, and the save. The form is the existing student registration page; only its action and
 * a few hidden/info items differ. Thin by design: every rule lives in {@link ReRegistrationService}.
 */
@Controller
@RequestMapping("/tc/re-register")
public class ReRegistrationController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(ReRegistrationController.class);

    private final ReRegistrationService reRegistrationService;
    private final DropdownService dropdownService;

    public ReRegistrationController(ReRegistrationService reRegistrationService,
                                    DropdownService dropdownService,
                                    TcConfigService tcConfigService,
                                    UserService userService) {
        super(tcConfigService, userService);
        this.reRegistrationService = reRegistrationService;
        this.dropdownService = dropdownService;
    }

    // ───────────────────────────── search ─────────────────────────────

    @GetMapping
    @CheckAccess(screen = "TC_RE_REGISTER", type = AccessType.VIEW)
    public String search(@RequestParam(name = "q", required = false) String q,
                         HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside re-register search");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        model.addAttribute("q", q == null ? "" : q);
        if (q != null && !q.isBlank()) {
            if (q.trim().length() < 2) {
                model.addAttribute("error", "Type at least 2 characters.");
            } else {
                try {
                    model.addAttribute("rows", reRegistrationService.search(q, school, academicYear));
                    model.addAttribute("searched", true);
                } catch (Exception e) {
                    log.error("Re-register search failed", e);
                    model.addAttribute("error", "The search failed. Please try again.");
                }
            }
        }
        return "tc/re-register";
    }

    // ───────────────────────────── pre-filled form ─────────────────────────────

    @GetMapping("/form/{exitUuid}")
    @CheckAccess(screen = "TC_RE_REGISTER", type = AccessType.CREATE)
    public String form(@PathVariable UUID exitUuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside re-register form {}", exitUuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            ReRegisterInfo info = reRegistrationService.info(exitUuid, school, academicYear);
            Student prefill = reRegistrationService.prefill(exitUuid, school, academicYear);
            model.addAttribute("student", prefill);
            addFormData(model, info);
            return "student/add-student";
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not open the re-register form {}", exitUuid, e);
            ra.addFlashAttribute("error", "Could not open the form. Please try again.");
        }
        return "redirect:/tc/re-register";
    }

    // ───────────────────────────── save ─────────────────────────────

    @PostMapping("/save")
    @CheckAccess(screen = "TC_RE_REGISTER", type = AccessType.CREATE)
    public String save(@Valid @ModelAttribute("student") Student student, BindingResult result,
                       @RequestParam("customerPic") MultipartFile customerPic,
                       @RequestParam(name = "exitUuid", required = false) UUID exitUuid,
                       HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside re-register save: exit={}", exitUuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        String error = null;
        if (result.hasErrors()) {
            error = result.getFieldError() != null && result.getFieldError().getDefaultMessage() != null
                    ? result.getFieldError().getDefaultMessage() : "Please check the form and try again.";
        } else {
            try {
                ReRegistrationResult done = reRegistrationService.reRegister(
                        student, customerPic, exitUuid, school, academicYear, currentUser());
                ra.addFlashAttribute("success", "Registered again. The new registration number is "
                        + done.getNewRegistrationNo() + ". Assign the new SR number on the Assign SR screen.");
                return "redirect:/tc/timeline/" + done.getNewStudentUuid();
            } catch (TcValidationException e) {
                error = e.getMessage();
            } catch (Exception e) {
                log.error("Could not re-register exit={}", exitUuid, e);
                error = "Could not register the student again. Please try again.";
            }
        }

        // Show the form again with what the user typed, so nothing has to be entered twice.
        ReRegisterInfo info = null;
        try {
            info = reRegistrationService.info(exitUuid, school, academicYear);
        } catch (Exception e) {
            // The state changed (for example already registered): the search page explains it.
            ra.addFlashAttribute("error", error != null ? error : e.getMessage());
            return "redirect:/tc/re-register";
        }
        addFormData(model, info);
        model.addAttribute("error", error);
        return "student/add-student";
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** The dropdown lists the registration page needs (the same ones the normal Add Student page loads) plus the form switches. */
    private void addFormData(Model model, ReRegisterInfo info) {
        model.addAttribute("categories", dropdownService.getCategories());
        model.addAttribute("casts", dropdownService.getCasts());
        model.addAttribute("provinces", dropdownService.getProvinces());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("banks", dropdownService.getBanks());
        model.addAttribute("relationships", dropdownService.getRelationships());
        model.addAttribute("bloodGroups", dropdownService.getBloodGroups());
        model.addAttribute("religions", dropdownService.getReligions());
        model.addAttribute("bodyTypes", dropdownService.getBodyTypes());
        model.addAttribute("qualifications", dropdownService.getQualifications());
        model.addAttribute("formAction", "/tc/re-register/save");
        model.addAttribute("reRegister", info);
    }
}
