package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.tc.IssueTcRequest;
import com.smsweb.sms.dto.tc.ReissueRequest;
import com.smsweb.sms.dto.tc.TcFilter;
import com.smsweb.sms.dto.tc.TcPrintDto;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.tc.TransferCertificate;
import com.smsweb.sms.models.tc.enums.TcStatus;
import com.smsweb.sms.repositories.tc.ClassDivisionRepository;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
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

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Issue and re-issue of the Transfer Certificate, the TC register and the TC print page. Thin by
 * design: every rule lives in {@link TcService}. The school comes from the session.
 */
@Controller
@RequestMapping("/tc")
public class TcController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(TcController.class);

    /** Format names become part of a template name, so only these characters are accepted. */
    private static final Pattern SAFE_FORMAT = Pattern.compile("[A-Z0-9_]{1,30}");

    private final TcService tcService;
    private final ClassDivisionRepository classDivisionRepository;

    public TcController(TcService tcService,
                        ClassDivisionRepository classDivisionRepository,
                        TcConfigService tcConfigService,
                        UserService userService) {
        super(tcConfigService, userService);
        this.tcService = tcService;
        this.classDivisionRepository = classDivisionRepository;
    }

    @PostMapping("/exits/{uuid}/issue")
    @CheckAccess(screen = "TC_GENERATE", type = AccessType.CREATE)
    public String issue(@PathVariable UUID uuid,
                        @RequestParam(required = false) String collectedByName,
                        @RequestParam(required = false) String collectedByRelation,
                        HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside issue TC for exit {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        IssueTcRequest req = new IssueTcRequest();
        req.setCollectedByName(collectedByName);
        req.setCollectedByRelation(collectedByRelation);
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            TransferCertificate tc = tcService.issue(uuid, req, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "TC issued. TC number " + tc.getTcNumber() + ".");
            ra.addFlashAttribute("newTcUuid", tc.getUuid().toString());
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not issue TC for exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not issue the TC. Please check the TC register before trying again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    @PostMapping("/certificates/{uuid}/reissue")
    @CheckAccess(screen = "TC_REISSUE", type = AccessType.CREATE)
    public String reissue(@PathVariable UUID uuid,
                          @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String collectedByName,
                          @RequestParam(required = false) String collectedByRelation,
                          HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside re-issue TC {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        ReissueRequest req = new ReissueRequest();
        req.setReason(reason);
        req.setCollectedByName(collectedByName);
        req.setCollectedByRelation(collectedByRelation);
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            TransferCertificate tc = tcService.reissue(uuid, req, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "TC re-issued. New TC number " + tc.getTcNumber() + ".");
            ra.addFlashAttribute("newTcUuid", tc.getUuid().toString());
            return "redirect:/tc/exits/" + tc.getExit().getUuid();
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not re-issue TC {}", uuid, e);
            ra.addFlashAttribute("error", "Could not re-issue the TC. Please check the TC register before trying again.");
        }
        return "redirect:/tc/register";
    }

    @GetMapping("/register")
    @CheckAccess(screen = "TC_REGISTER", type = AccessType.VIEW)
    public String register(@ModelAttribute("filter") TcFilter filter, BindingResult binding,
                           HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside TC register");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        TcFilter f = binding.hasErrors() ? new TcFilter() : filter;
        if (binding.hasErrors()) {
            model.addAttribute("filter", f);
            model.addAttribute("error", "Some filters were not understood and were ignored.");
        }
        model.addAttribute("rows", tcService.search(f, school));
        model.addAttribute("divisions", classDivisionRepository.findBySchool_IdOrderByNameAsc(school.getId()));
        model.addAttribute("statuses", TcStatus.values());
        return "tc/tc-register";
    }

    @GetMapping("/certificates/{uuid}/print")
    @CheckAccess(screen = "TC_REGISTER", type = AccessType.VIEW)
    public String print(@PathVariable UUID uuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside TC print {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            TcPrintDto d = tcService.printData(uuid, school);
            model.addAttribute("tc", d);
            return templateFor(d.getTcFormat());
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not open TC {}", uuid, e);
            ra.addFlashAttribute("error", "Could not open the TC. Please try again.");
        }
        return "redirect:/tc/register";
    }

    /** {@code tc/print-<format>} when the template for that format exists, otherwise the placeholder page. */
    private static String templateFor(String format) {
        if (format != null && SAFE_FORMAT.matcher(format).matches()) {
            String name = "tc/print-" + format.toLowerCase();
            if (new ClassPathResource("templates/" + name + ".html").exists()) {
                return name;
            }
        }
        return "tc/tc-print-placeholder";
    }
}
