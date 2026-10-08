package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.tc.ClassDivisionRequest;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.tc.ClassDivisionService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Class Groups page: one page for the list of groups, inline add and edit, and the grade mapping
 * of each group (plan form F1). Admin and Super Admin only, plus the TC_DIVISION_MANAGE screen
 * permission. The whole page is closed while the TC feature is off for the school.
 *
 * <p>The school always comes from the session, never from request data. Saves are plain form posts
 * that redirect back to the page (post, redirect, get) with a success or error message.</p>
 */
@Controller
@RequestMapping("/tc/divisions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class ClassDivisionController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ClassDivisionController.class);

    private static final String PAGE_URL = "/tc/divisions";

    private final ClassDivisionService classDivisionService;
    private final TcConfigService tcConfigService;
    private final UserService userService;

    public ClassDivisionController(ClassDivisionService classDivisionService,
                                   TcConfigService tcConfigService,
                                   UserService userService) {
        this.classDivisionService = classDivisionService;
        this.tcConfigService = tcConfigService;
        this.userService = userService;
    }

    @GetMapping
    @CheckAccess(screen = "TC_DIVISION_MANAGE", type = AccessType.VIEW)
    public String page(HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside class groups page");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        model.addAttribute("rows", classDivisionService.listRows(school));
        model.addAttribute("formatNames", tcConfigService.formatNames(school));
        return "tc/class-divisions";
    }

    @PostMapping("/create")
    @CheckAccess(screen = "TC_DIVISION_MANAGE", type = AccessType.CREATE)
    public String create(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String tcFormat,
                         @RequestParam(required = false) Long startNumber,
                         @RequestParam(required = false) String status,
                         HttpSession session, RedirectAttributes ra) {
        log.info("Inside create class group");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            classDivisionService.create(request(name, description, tcFormat, startNumber, status), school, currentUser());
            ra.addFlashAttribute("success", "Class group saved.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not create class group", e);
            ra.addFlashAttribute("error", "Could not save the class group. Please try again.");
        }
        return "redirect:" + PAGE_URL;
    }

    @PostMapping("/{uuid}/update")
    @CheckAccess(screen = "TC_DIVISION_MANAGE", type = AccessType.EDIT)
    public String update(@PathVariable UUID uuid,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String tcFormat,
                         @RequestParam(required = false) Long startNumber,
                         @RequestParam(required = false) String status,
                         HttpSession session, RedirectAttributes ra) {
        log.info("Inside update class group {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            classDivisionService.update(uuid, request(name, description, tcFormat, startNumber, status), school, currentUser());
            ra.addFlashAttribute("success", "Class group updated.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not update class group {}", uuid, e);
            ra.addFlashAttribute("error", "Could not update the class group. Please try again.");
        }
        return "redirect:" + PAGE_URL;
    }

    @PostMapping("/{uuid}/grades")
    @CheckAccess(screen = "TC_DIVISION_MANAGE", type = AccessType.EDIT)
    public String saveGrades(@PathVariable UUID uuid,
                             @RequestParam(name = "gradeIds", required = false) List<Long> gradeIds,
                             HttpSession session, RedirectAttributes ra) {
        log.info("Inside save class group grades {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            classDivisionService.saveGradeMapping(uuid, gradeIds, school, currentUser());
            ra.addFlashAttribute("success", "Grades saved.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not save grades of class group {}", uuid, e);
            ra.addFlashAttribute("error", "Could not save the grades. Please try again.");
        }
        return "redirect:" + PAGE_URL;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Returns a redirect when the page must not open (no school, or the feature is off); null when it may. */
    private String featureBlock(School school, RedirectAttributes ra) {
        if (school == null) {
            ra.addFlashAttribute("error", "No school is selected for this login.");
            return "redirect:/dashboard";
        }
        if (!tcConfigService.isEnabled(school)) {
            ra.addFlashAttribute("error",
                    "The Transfer Certificate feature is not enabled for this school. Turn it on in App Config, TC Settings.");
            return "redirect:/dashboard";
        }
        return null;
    }

    private UserEntity currentUser() {
        UserEntity user = userService.getLoggedInUser();
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
        return user;
    }

    private static ClassDivisionRequest request(String name, String description, String tcFormat,
                                                Long startNumber, String status) {
        ClassDivisionRequest req = new ClassDivisionRequest();
        req.setName(name);
        req.setDescription(description);
        req.setTcFormat(tcFormat);
        req.setStartNumber(startNumber);
        req.setStatus(status);
        return req;
    }
}
