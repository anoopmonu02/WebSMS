package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.tc.ApproveRequest;
import com.smsweb.sms.dto.tc.ExitFilter;
import com.smsweb.sms.dto.tc.StrikeOffRequest;
import com.smsweb.sms.dto.tc.StudentPickRow;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.services.tc.DueClearanceReceiptService;
import com.smsweb.sms.services.tc.StudentExitService;
import com.smsweb.sms.services.tc.TcService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pages of the strike-off flow: the strike-off form, the exit list and the exit detail with the
 * admin-only approve (green flag) and cancel actions. Thin by design: every rule lives in
 * {@link StudentExitService}. The school comes from the session, never from request data.
 */
@Controller
@RequestMapping("/tc")
public class StudentExitController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentExitController.class);

    private final StudentExitService studentExitService;
    private final StudentExitRepository studentExitRepository;
    private final DueClearanceReceiptService dueClearanceReceiptService;
    private final TcService tcService;

    public StudentExitController(StudentExitService studentExitService,
                                 StudentExitRepository studentExitRepository,
                                 DueClearanceReceiptService dueClearanceReceiptService,
                                 TcService tcService,
                                 TcConfigService tcConfigService,
                                 UserService userService) {
        super(tcConfigService, userService);
        this.studentExitService = studentExitService;
        this.studentExitRepository = studentExitRepository;
        this.dueClearanceReceiptService = dueClearanceReceiptService;
        this.tcService = tcService;
    }

    // ───────────────────────────── strike-off form ─────────────────────────────

    @GetMapping("/strike-off")
    @CheckAccess(screen = "TC_STRIKE_OFF", type = AccessType.VIEW)
    public String strikeOffForm(@RequestParam(name = "studentId", required = false) Long studentId,
                                HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside strike-off form");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        addStrikeOffData(model);
        StrikeOffRequest form = new StrikeOffRequest();
        if (studentId != null) {
            // Arrived from the Student list: choose the student for the user.
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            StudentPickRow pick = studentExitService.findCandidateByStudentId(studentId, school, academicYear);
            if (pick != null) {
                form.setAcademicStudentId(pick.academicStudentId());
                model.addAttribute("studentLabel",
                        pick.studentName() + " — Class " + pick.className() + " " + pick.sectionName());
            } else {
                model.addAttribute("error",
                        "That student cannot be struck off: not active in the current session, or already struck off.");
            }
        }
        model.addAttribute("form", form);
        return "tc/strike-off";
    }

    @PostMapping("/strike-off")
    @CheckAccess(screen = "TC_STRIKE_OFF", type = AccessType.CREATE)
    public String strikeOff(@RequestParam(required = false) Long academicStudentId,
                            @RequestParam(required = false) String studentLabel,
                            @RequestParam(required = false) ExitReason exitReason,
                            @RequestParam(required = false) Integer absentDays,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exitDate,
                            @RequestParam(defaultValue = "true") boolean includeLastMonth,
                            @RequestParam(required = false) String remark,
                            @RequestParam(required = false) BigDecimal manualAmount,
                            @RequestParam(required = false) String manualReason,
                            HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside strike-off save: academicStudentId={}", academicStudentId);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        StrikeOffRequest req = new StrikeOffRequest();
        req.setAcademicStudentId(academicStudentId);
        req.setExitReason(exitReason);
        req.setAbsentDays(absentDays);
        req.setExitDate(exitDate);
        req.setIncludeLastMonth(includeLastMonth);
        req.setRemark(remark);
        req.setManualAmount(manualAmount);
        req.setManualReason(manualReason);

        String error;
        try {
            StudentExit exit = studentExitService.strikeOff(req, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "Student struck off. Waiting for admin approval.");
            return "redirect:/tc/exits/" + exit.getUuid();
        } catch (TcValidationException e) {
            error = e.getMessage();
        } catch (Exception e) {
            log.error("Could not strike off academicStudentId={}", academicStudentId, e);
            error = "Could not strike off the student. Please try again.";
        }
        // Show the form again with what the user typed, so nothing has to be entered twice.
        addStrikeOffData(model);
        model.addAttribute("form", req);
        model.addAttribute("studentLabel", studentLabel);
        model.addAttribute("error", error);
        return "tc/strike-off";
    }

    // ───────────────────────────── exit list ─────────────────────────────

    @GetMapping("/exits")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String exitList(@ModelAttribute("filter") ExitFilter filter, BindingResult binding,
                           HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside exit list");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        ExitFilter f = binding.hasErrors() ? new ExitFilter() : filter;
        if (binding.hasErrors()) {
            model.addAttribute("filter", f);
            model.addAttribute("error", "Some filters were not understood and were ignored.");
        }
        model.addAttribute("rows", studentExitService.search(f, school));
        model.addAttribute("statuses", ExitStatus.values());
        model.addAttribute("reasons", ExitReason.values());
        model.addAttribute("grades", studentExitRepository.findDistinctGrades(school.getId()));
        return "tc/exit-list";
    }

    // ───────────────────────────── exit detail ─────────────────────────────

    @GetMapping("/exits/{uuid}")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String exitDetail(@PathVariable UUID uuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside exit detail {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            model.addAttribute("exit", studentExitService.detail(uuid, school, academicYear));
            model.addAttribute("dues", dueClearanceReceiptService.panel(uuid, school, academicYear));
            model.addAttribute("tcPanel", tcService.panel(uuid, school, academicYear));
            model.addAttribute("isAdmin", StudentExitService.isAdmin());
            model.addAttribute("clearToken", UUID.randomUUID().toString());
            return "tc/exit-detail";
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not open exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not open the exit record. Please try again.");
        }
        return "redirect:/tc/exits";
    }

    @PostMapping("/exits/{uuid}/approve")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String approve(@PathVariable UUID uuid,
                          @RequestParam(required = false) String remark,
                          @RequestParam(required = false) BigDecimal manualAmount,
                          @RequestParam(required = false) String manualReason,
                          HttpSession session, RedirectAttributes ra) {
        log.info("Inside approve exit {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        ApproveRequest req = new ApproveRequest();
        req.setRemark(remark);
        req.setManualAmount(manualAmount);
        req.setManualReason(manualReason);
        try {
            studentExitService.approve(uuid, req, school, currentUser());
            ra.addFlashAttribute("success", "Approved. The dues are now fixed.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not approve exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not approve. Please try again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    @PostMapping("/exits/{uuid}/cancel")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String cancel(@PathVariable UUID uuid,
                         @RequestParam(required = false) String reason,
                         HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside cancel strike-off {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            studentExitService.cancelStrikeOff(uuid, reason, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "Strike-off cancelled. The student is active again.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not cancel strike-off {}", uuid, e);
            ra.addFlashAttribute("error", "Could not cancel the strike-off. Please try again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    @PostMapping("/exits/{uuid}/reactivate")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String reactivate(@PathVariable UUID uuid,
                             @RequestParam(required = false) String reason,
                             HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside reactivate exit {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            studentExitService.reactivateSameSession(uuid, reason, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "Student reactivated. The login is on again; months after the last date "
                    + "now appear as normal dues on the Fee Submission screen.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not reactivate exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not reactivate the student. Please try again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private void addStrikeOffData(Model model) {
        AcademicYear year = (AcademicYear) model.getAttribute("academicYear");
        model.addAttribute("reasons", ExitReason.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("sessionStart", year == null ? null : year.getStartDate());
        model.addAttribute("isAdmin", StudentExitService.isAdmin());
    }
}
