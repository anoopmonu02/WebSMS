package com.smsweb.sms.controllers.grievance;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.grievance.Grievance;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.grievance.GrievanceService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.users.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST endpoints backing the "Grievance" tab on /message/sendMessage (formerly "Activities").
 * Deliberately a standalone model/table (see Grievance.java) rather than reusing the shared
 * SmsMessage entity that backs Complaint/Notification.
 */
@RestController
@RequestMapping("/grievance")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class GrievanceController {
    private static final Logger log = LoggerFactory.getLogger(GrievanceController.class);
    private static final String DATE_PATTERN = "dd/MMM/yyyy"; // matches flatpickr's "d/M/Y" format used app-wide

    private final GrievanceService grievanceService;
    private final AcademicStudentService academicStudentService;
    private final UserService userService;

    public GrievanceController(GrievanceService grievanceService, AcademicStudentService academicStudentService, UserService userService) {
        this.grievanceService = grievanceService;
        this.academicStudentService = academicStudentService;
        this.userService = userService;
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/getGrievancesByStudent/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getGrievancesByStudent(@PathVariable Long studentId) {
        log.info("Inside getGrievancesByStudent");
        List<Grievance> grievances = grievanceService.getGrievancesByStudentId(studentId);
        List<Map<String, Object>> leanList = new ArrayList<>();
        for (Grievance g : grievances) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("title", g.getTitle());
            m.put("description", g.getDescription());
            m.put("createdAt", g.getCreatedAt());
            m.put("dueDate", g.getDueDate());
            m.put("closed", g.getClosedAt() != null);
            m.put("closedAt", g.getClosedAt());
            m.put("closerStatementRemark", g.getCloserStatementRemark());
            leanList.add(m);
        }
        return ResponseEntity.ok(leanList);
    }

    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
    @PostMapping("/saveGrievance")
    public ResponseEntity<Map<String, Object>> saveGrievance(@RequestBody Map<String, Object> payload) {
        log.info("Inside saveGrievance");
        try {
            String title = (String) payload.get("title");
            String description = (String) payload.get("description");
            Object studentIdObj = payload.get("studentId");
            String dueDateStr = (String) payload.get("dueDate");

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
            }
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Description is required"));
            }
            if (dueDateStr == null || dueDateStr.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Due date is required"));
            }
            if (studentIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "studentId is required"));
            }

            Long studentId = Long.valueOf(studentIdObj.toString());
            Optional<AcademicStudent> studentOpt = academicStudentService.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid studentId"));
            }
            AcademicStudent student = studentOpt.get();

            Date dueDate;
            try {
                dueDate = new SimpleDateFormat(DATE_PATTERN).parse(dueDateStr);
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid due date format"));
            }

            Grievance saved = grievanceService.saveGrievance(title.trim(), description.trim(), student, dueDate, userService.getLoggedInUser());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", saved.getId());
            response.put("title", saved.getTitle());
            response.put("description", saved.getDescription());
            response.put("createdAt", saved.getCreatedAt());
            response.put("dueDate", saved.getDueDate());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to save grievance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error saving grievance: " + e.getMessage()));
        }
    }

    /** Reschedule a grievance's due date. Blocked once the grievance is closed. */
    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
    @PostMapping("/updateGrievanceDueDate/{id}")
    public ResponseEntity<Map<String, Object>> updateGrievanceDueDate(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        log.info("Inside updateGrievanceDueDate");
        try {
            String dueDateStr = (String) payload.get("dueDate");
            if (dueDateStr == null || dueDateStr.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Due date is required"));
            }
            Date dueDate = new SimpleDateFormat(DATE_PATTERN).parse(dueDateStr);
            Grievance updated = grievanceService.updateDueDate(id, dueDate);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Grievance not found"));
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dueDate", updated.getDueDate());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            // Already closed — no further action allowed
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update grievance due date", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error updating due date: " + e.getMessage()));
        }
    }

    /** Closes a grievance. A non-blank remark is mandatory. */
    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.EDIT)
    @PostMapping("/closeGrievance/{id}")
    public ResponseEntity<Map<String, Object>> closeGrievance(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        log.info("Inside closeGrievance");
        try {
            String remark = (String) payload.get("remark");
            Grievance closed = grievanceService.closeGrievance(id, remark, userService.getLoggedInUser());
            if (closed == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Grievance not found"));
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("closedAt", closed.getClosedAt());
            response.put("closerStatementRemark", closed.getCloserStatementRemark());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // Blank remark
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Already closed
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to close grievance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error closing grievance: " + e.getMessage()));
        }
    }
}
