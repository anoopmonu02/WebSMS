package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.student.SiblingGroupDissolutionService;
import com.smsweb.sms.services.student.StudentRemovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NEW controller (feature: remove a student who belongs to a sibling group).
 *
 *  GET  /student/delete-student/{id}/sibling-info  - read-only: is the student in an active sibling group?
 *                                                    (drives the confirmation popup, changes nothing)
 *  POST /student/delete-student/confirm            - removes the student, then applies the confirmed group
 *                                                    action (DISSOLVE or DISSOLVE_AND_CREATE) atomically.
 *
 * Both require the student to be an active student of the logged-in school, the same permission as the
 * existing delete, and the POST is CSRF-protected.
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentRemovalController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(StudentRemovalController.class);
    private static final String ACTION_DISSOLVE = "DISSOLVE";
    private static final String ACTION_DISSOLVE_AND_CREATE = "DISSOLVE_AND_CREATE";

    private final StudentRemovalService studentRemovalService;

    @Autowired
    public StudentRemovalController(StudentRemovalService studentRemovalService) {
        this.studentRemovalService = studentRemovalService;
    }

    @ResponseBody
    @CheckAccess(screen = "STUDENT_DELETE", type = AccessType.DELETE)
    @GetMapping("/delete-student/{id}/sibling-info")
    public ResponseEntity<?> siblingInfo(@PathVariable("id") Long id, Model model) {
        log.info("Inside siblingInfo - studentId={}", id);
        School school = (School) model.getAttribute("school");
        if (school == null || !studentRemovalService.isActiveStudentOfSchool(id, school.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found."));
        }
        try {
            List<Map<String, Object>> groups = studentRemovalService.previewSiblingGroups(id);
            return ResponseEntity.ok(Map.of("inGroup", !groups.isEmpty(), "groups", groups));
        } catch (Exception e) {
            log.error("Failed to load sibling group info for studentId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to check the sibling group right now. Please try again."));
        }
    }

    @CheckAccess(screen = "STUDENT_DELETE", type = AccessType.DELETE)
    @PostMapping("/delete-student/confirm")
    public String confirmRemoval(@RequestParam("id") Long id,
                                 @RequestParam("action") String action,
                                 Model model, RedirectAttributes redirectAttributes) {
        log.info("Inside confirmRemoval - studentId={}, action={}", id, action);
        if (!ACTION_DISSOLVE.equals(action) && !ACTION_DISSOLVE_AND_CREATE.equals(action)) {
            redirectAttributes.addFlashAttribute("error", "Invalid request.");
            return "redirect:/student/student";
        }
        School school = (School) model.getAttribute("school");
        if (school == null || !studentRemovalService.isActiveStudentOfSchool(id, school.getId())) {
            redirectAttributes.addFlashAttribute("error", "Student not found.");
            return "redirect:/student/student";
        }
        try {
            boolean createNew = ACTION_DISSOLVE_AND_CREATE.equals(action);
            StudentRemovalService.RemovalResult result = studentRemovalService.removeStudent(id, createNew);

            StringBuilder success = new StringBuilder("Student: " + result.studentName + " removed successfully.");
            List<String> warnings = new ArrayList<>();
            for (SiblingGroupDissolutionService.Outcome o : result.outcomes) {
                success.append(" Sibling group '").append(o.oldGroupName).append("' was dissolved.");
                if (o.newGroupName != null) {
                    success.append(" New group '").append(o.newGroupName).append("' created with ")
                            .append(o.newGroupMembers.size()).append(" students: ")
                            .append(String.join(", ", o.newGroupMembers)).append(".");
                } else if (o.notCreatedReason != null) {
                    warnings.add("Group '" + o.oldGroupName + "': " + o.notCreatedReason);
                }
            }
            redirectAttributes.addFlashAttribute("success", success.toString());
            if (!warnings.isEmpty()) {
                redirectAttributes.addFlashAttribute("warning", String.join(" ", warnings));
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove studentId={} with sibling action {}", id, action, e);
            redirectAttributes.addFlashAttribute("error", "Unable to remove the student right now. Nothing was changed. Please try again.");
        }
        return "redirect:/student/student";
    }
}
