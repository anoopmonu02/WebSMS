package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.tc.StudentTimelineService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * The student timeline page. Read-only; the school comes from the session, never from the request.
 * Uses the exit list screen key, the same as the exit detail page it is opened from.
 */
@Controller
@RequestMapping("/tc")
public class StudentTimelineController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentTimelineController.class);

    private final StudentTimelineService studentTimelineService;

    public StudentTimelineController(StudentTimelineService studentTimelineService,
                                     TcConfigService tcConfigService,
                                     UserService userService) {
        super(tcConfigService, userService);
        this.studentTimelineService = studentTimelineService;
    }

    @GetMapping("/timeline/{studentUuid}")
    @CheckAccess(screen = "TC_EXIT_LIST", type = AccessType.VIEW)
    public String timeline(@PathVariable UUID studentUuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside timeline page {}", studentUuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            model.addAttribute("tl", studentTimelineService.timeline(studentUuid, school));
            return "tc/timeline";
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not open the timeline {}", studentUuid, e);
            ra.addFlashAttribute("error", "Could not open the timeline. Please try again.");
        }
        return "redirect:/tc/exits";
    }
}
