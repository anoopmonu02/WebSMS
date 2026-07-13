package com.smsweb.sms.services.grievance;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.grievance.Grievance;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.grievance.GrievanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class GrievanceService {
    private static final Logger log = LoggerFactory.getLogger(GrievanceService.class);
    private static final String DATE_PATTERN = "dd/MMM/yyyy"; // same "d/M/Y" flatpickr format used app-wide

    private final GrievanceRepository grievanceRepository;

    public GrievanceService(GrievanceRepository grievanceRepository) {
        this.grievanceRepository = grievanceRepository;
    }

    public Grievance saveGrievance(String title, String description, AcademicStudent student, Date dueDate, UserEntity createdBy) {
        log.info("Inside saveGrievance");
        Grievance grievance = new Grievance();
        grievance.setTitle(title);
        grievance.setDescription(description);
        grievance.setAcademicStudent(student);
        grievance.setSchool(student.getSchool());
        grievance.setAcademicYear(student.getAcademicYear());
        grievance.setDueDate(dueDate);
        grievance.setCreatedBy(createdBy);
        grievance.setCreatedAt(new Date());
        return grievanceRepository.save(grievance);
    }

    public List<Grievance> getGrievancesByStudentId(Long academicStudentId) {
        log.info("Inside getGrievancesByStudentId");
        return grievanceRepository.findAllByAcademicStudentIdOrderByCreatedAtDesc(academicStudentId);
    }

    /**
     * Reschedules the due date. Returns null if the grievance doesn't exist.
     * Throws IllegalStateException if it's already closed (enforces "no further
     * action once closed" — the caller/controller should surface this as a 409/400).
     */
    public Grievance updateDueDate(Long id, Date newDueDate) {
        log.info("Inside updateDueDate");
        Optional<Grievance> opt = grievanceRepository.findById(id);
        if (opt.isEmpty()) return null;
        Grievance grievance = opt.get();
        if (grievance.getClosedAt() != null) {
            throw new IllegalStateException("This grievance is already closed; the due date can no longer be changed.");
        }
        grievance.setDueDate(newDueDate);
        return grievanceRepository.save(grievance);
    }

    /**
     * Closes the grievance with a mandatory remark. Returns null if the grievance
     * doesn't exist. Throws IllegalStateException if already closed, and
     * IllegalArgumentException if the remark is blank.
     */
    public Grievance closeGrievance(Long id, String remark, UserEntity closedBy) {
        log.info("Inside closeGrievance");
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("A closing remark is required to close this grievance.");
        }
        Optional<Grievance> opt = grievanceRepository.findById(id);
        if (opt.isEmpty()) return null;
        Grievance grievance = opt.get();
        if (grievance.getClosedAt() != null) {
            throw new IllegalStateException("This grievance is already closed.");
        }
        grievance.setCloserStatementRemark(remark.trim());
        grievance.setClosedAt(new Date());
        grievance.setClosedBy(closedBy);
        return grievanceRepository.save(grievance);
    }

    /**
     * Dashboard "Pending Grievances" panel data — due today or overdue, not yet
     * closed, scoped to the current school + academic year. Returns lean maps
     * ready for the Thymeleaf template (student name, SR no, class-section, etc.)
     * rather than raw entities, mirroring the toLeanMap convention used elsewhere
     * in this codebase (e.g. FeeSubmissionService).
     */
    public List<Map<String, Object>> getPendingDueTodayOrOverdue(Long schoolId, Long academicYearId) {
        log.info("Inside getPendingDueTodayOrOverdue");
        List<Grievance> grievances = grievanceRepository.findPendingDueTodayOrOverdue(new Date(), schoolId, academicYearId);
        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat sf = new SimpleDateFormat(DATE_PATTERN);
        for (Grievance g : grievances) {
            AcademicStudent astu = g.getAcademicStudent();
            Map<String, Object> row = new HashMap<>();
            row.put("id", g.getId());
            row.put("academicStudentId", astu != null ? astu.getId() : null);
            row.put("studentName", astu != null && astu.getStudent() != null ? astu.getStudent().getStudentName() : "");
            row.put("srNo", astu != null && astu.getClassSrNo() != null ? astu.getClassSrNo() : "");
            String classSection = "";
            if (astu != null && astu.getGrade() != null && astu.getSection() != null) {
                classSection = astu.getGrade().getGradeName() + " - " + astu.getSection().getSectionName();
            }
            row.put("classSection", classSection);
            row.put("title", g.getTitle());
            row.put("description", g.getDescription());
            row.put("dueDateDisplay", g.getDueDate() != null ? sf.format(g.getDueDate()) : "");
            result.add(row);
        }
        return result;
    }
}
