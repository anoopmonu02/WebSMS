package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.tc.DuesEstimate;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.tc.DuesCalculationException;
import com.smsweb.sms.services.tc.StudentExitService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON calls behind the strike-off form: the student picker and the live dues preview. Read only;
 * nothing is saved here.
 */
@RestController
@RequestMapping("/tc/api/exits")
public class StudentExitRestController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentExitRestController.class);

    private final StudentExitService studentExitService;

    public StudentExitRestController(StudentExitService studentExitService,
                                     TcConfigService tcConfigService,
                                     UserService userService) {
        super(tcConfigService, userService);
        this.studentExitService = studentExitService;
    }

    @GetMapping("/students")
    @CheckAccess(screen = "TC_STRIKE_OFF", type = AccessType.VIEW)
    public ResponseEntity<?> students(@RequestParam(name = "term", required = false) String term,
                                      HttpSession session, Model model) {
        log.info("Inside strike-off student search");
        School school = (School) session.getAttribute("school");
        String problem = featureMessage(school);
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            return ResponseEntity.ok(studentExitService.searchCandidates(term, school, academicYear));
        } catch (Exception e) {
            log.error("Student search failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not search students."));
        }
    }

    /** Read only: the sibling group of the chosen student (empty list when none), shown on the strike-off form. */
    @GetMapping("/sibling-preview")
    @CheckAccess(screen = "TC_STRIKE_OFF", type = AccessType.VIEW)
    public ResponseEntity<?> siblingPreview(@RequestParam(name = "academicStudentId", required = false) Long academicStudentId,
                                            HttpSession session) {
        log.info("Inside sibling preview: academicStudentId={}", academicStudentId);
        School school = (School) session.getAttribute("school");
        String problem = featureMessage(school);
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        try {
            return ResponseEntity.ok(Map.of("groups", studentExitService.siblingPreview(academicStudentId, school)));
        } catch (Exception e) {
            log.error("Sibling preview failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not check the sibling group."));
        }
    }

    @GetMapping("/dues-preview")
    @CheckAccess(screen = "TC_STRIKE_OFF", type = AccessType.VIEW)
    public ResponseEntity<?> duesPreview(@RequestParam(name = "academicStudentId", required = false) Long academicStudentId,
                                         @RequestParam(name = "exitDate", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exitDate,
                                         @RequestParam(name = "includeLastMonth", defaultValue = "true") boolean includeLastMonth,
                                         HttpSession session, Model model) {
        log.info("Inside dues preview: academicStudentId={}", academicStudentId);
        School school = (School) session.getAttribute("school");
        String problem = featureMessage(school);
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            DuesEstimate estimate = studentExitService.previewDues(academicStudentId, exitDate, includeLastMonth, school, academicYear);
            body.put("ok", true);
            body.put("amount", estimate.amount().toPlainString());
            body.put("fine", estimate.fineAmount().toPlainString());
            body.put("months", estimate.monthsLabel());
            body.put("heads", estimate.headsLabel());
            body.put("cutoffMonth", estimate.cutoffMonth());
            return ResponseEntity.ok(body);
        } catch (DuesCalculationException e) {
            // The form needs to tell this apart: an admin may type the amount, others must ask an admin.
            body.put("ok", false);
            body.put("message", e.getMessage());
            body.put("canOverride", StudentExitService.isAdmin());
            return ResponseEntity.ok(body);
        } catch (TcValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Dues preview failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not work out the dues."));
        }
    }
}
