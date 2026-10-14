package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * NEW service (feature: remove a student who belongs to a sibling group).
 *
 * Wraps the EXISTING StudentService.deleteStudent (unchanged) and the sibling-group handling in ONE
 * transaction: first the student is removed, then the group action the user confirmed is applied.
 * Any failure rolls back both, so a student is never removed while the group is left half-changed.
 */
@Service
public class StudentRemovalService {

    private static final Logger log = LoggerFactory.getLogger(StudentRemovalService.class);

    private final StudentService studentService;
    private final AcademicStudentRepository academicStudentRepository;
    private final SiblingGroupDissolutionService dissolutionService;

    @Autowired
    public StudentRemovalService(StudentService studentService,
                                 AcademicStudentRepository academicStudentRepository,
                                 SiblingGroupDissolutionService dissolutionService) {
        this.studentService = studentService;
        this.academicStudentRepository = academicStudentRepository;
        this.dissolutionService = dissolutionService;
    }

    public static class RemovalResult {
        public String studentName;
        public List<SiblingGroupDissolutionService.Outcome> outcomes;
    }

    /** True when the student has an Active enrollment in the given school (ownership check for the endpoints). */
    @Transactional(readOnly = true)
    public boolean isActiveStudentOfSchool(Long studentId, Long schoolId) {
        if (studentId == null || schoolId == null) {
            return false;
        }
        return academicStudentRepository.findAllByStudent_IdAndStatus(studentId, AcademicStudent.STATUS_ACTIVE)
                .stream().anyMatch(a -> a.getSchool() != null && schoolId.equals(a.getSchool().getId()));
    }

    @Transactional(readOnly = true)
    public boolean belongsToActiveSiblingGroup(Long studentId) {
        return dissolutionService.hasActiveGroup(studentId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewSiblingGroups(Long studentId) {
        return dissolutionService.previewForStudent(studentId);
    }

    /**
     * @param createNewGroup true for "OK & Create New Group"
     * @throws IllegalArgumentException when the student is not an active student (message is safe to show)
     * @throws IllegalStateException    when removal fails (everything is rolled back)
     */
    @Transactional
    public RemovalResult removeStudent(Long studentId, boolean createNewGroup) {
        List<AcademicStudent> active = academicStudentRepository.findAllByStudent_IdAndStatus(studentId, AcademicStudent.STATUS_ACTIVE);
        if (active.isEmpty()) {
            throw new IllegalArgumentException("Student not found or already removed.");
        }
        String name = active.get(0).getStudent().getStudentName();

        // 1) the existing removal, unchanged
        String msg = studentService.deleteStudent(studentId);
        if (msg == null || !msg.startsWith("success")) {
            log.error("Student removal failed for studentId={}: {}", studentId, msg);
            throw new IllegalStateException("Student could not be removed.");
        }

        // 2) the sibling-group action the user confirmed
        RemovalResult result = new RemovalResult();
        result.studentName = name;
        result.outcomes = dissolutionService.dissolveForRemovedStudent(studentId, name, createNewGroup);
        return result;
    }
}
