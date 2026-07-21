package com.smsweb.sms.services.mobile;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.mobile.StudentHealthInfo;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.mobile.StudentHealthInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * NEW, isolated service backing the student_health_info table. Does not
 * modify StudentService, AcademicStudentService, StudentMigrationService or
 * any other existing service — those files only gain a single call into
 * this service at the exact point a new AcademicStudent row is created.
 */
@Service
public class StudentHealthInfoService {

    private static final Logger log = LoggerFactory.getLogger(StudentHealthInfoService.class);

    @Autowired
    private StudentHealthInfoRepository repository;

    /**
     * Creates a blank (all-null) health-info row for a brand-new enrollment.
     * Used by: new student registration, legacy bulk import, and the
     * same-school / cross-school year-end migration branches.
     */
    @Transactional
    public StudentHealthInfo createBlank(AcademicStudent academicStudent, UserEntity actor) {
        return createBlank(academicStudent, actor, null, null);
    }

    /**
     * Same as createBlank(academicStudent, actor), but seeds the initial
     * height/weight captured from the registration form at the moment the
     * enrollment is created (student_health_info is now the sole source of
     * truth for these fields — Student.height/weight are left null/blank
     * going forward).
     */
    @Transactional
    public StudentHealthInfo createBlank(AcademicStudent academicStudent, UserEntity actor, Integer height, Integer weight) {
        if (repository.existsByAcademicStudent_Id(academicStudent.getId())) {
            log.warn("createBlank called but a health-info row already exists for academicStudentId={}",
                    academicStudent.getId());
            return repository.findByAcademicStudent_Id(academicStudent.getId()).orElse(null);
        }
        StudentHealthInfo info = new StudentHealthInfo();
        info.setAcademicStudent(academicStudent);
        info.setAcademicYearId(academicStudent.getAcademicYear().getId());
        info.setSchoolId(academicStudent.getSchool().getId());
        info.setHeight(height);
        info.setWeight(weight);
        info.setHaveHealthIssues(false);
        info.setHaveEyeIssue(false);
        info.setCreatedBy(actor);
        return repository.save(info);
    }

    /**
     * Copies the source enrollment's health values forward onto a new
     * enrollment row created in the SAME academic year (mid-session
     * migration — a branch transfer, not a new session, so the student's
     * already-recorded height/weight/health status should carry over rather
     * than reset to blank).
     *
     * Idempotent w.r.t. an already-existing target row: mid-session
     * migration's cross-school branch goes through StudentService.saveStudent()
     * first, which already auto-creates a BLANK health-info row for the new
     * AcademicStudent via createBlank(). This method fetches that row (if
     * present) and overwrites it with the source's values, rather than
     * inserting a second row and violating the one-row-per-academic-student
     * unique constraint.
     */
    @Transactional
    public StudentHealthInfo copyForward(AcademicStudent sourceAcademicStudent,
                                          AcademicStudent newAcademicStudent,
                                          UserEntity actor) {
        Optional<StudentHealthInfo> sourceOpt =
                repository.findByAcademicStudent_Id(sourceAcademicStudent.getId());

        StudentHealthInfo target = repository.findByAcademicStudent_Id(newAcademicStudent.getId())
                .orElseGet(() -> {
                    StudentHealthInfo blank = new StudentHealthInfo();
                    blank.setAcademicStudent(newAcademicStudent);
                    blank.setAcademicYearId(newAcademicStudent.getAcademicYear().getId());
                    blank.setSchoolId(newAcademicStudent.getSchool().getId());
                    blank.setHaveHealthIssues(false);
                    blank.setHaveEyeIssue(false);
                    return blank;
                });

        if (sourceOpt.isPresent()) {
            StudentHealthInfo source = sourceOpt.get();
            target.setHeight(source.getHeight());
            target.setWeight(source.getWeight());
            target.setHaveHealthIssues(source.getHaveHealthIssues());
            target.setHaveEyeIssue(source.getHaveEyeIssue());
            target.setHealthIssueDescription(source.getHealthIssueDescription());
        } else {
            log.info("copyForward: no source health-info row for academicStudentId={}, target left blank",
                    sourceAcademicStudent.getId());
        }
        if (target.getCreatedBy() == null) {
            target.setCreatedBy(actor);
        }
        target.setUpdatedBy(actor);
        return repository.save(target);
    }

    public Optional<StudentHealthInfo> getByAcademicStudentId(Long academicStudentId) {
        return repository.findByAcademicStudent_Id(academicStudentId);
    }

    /**
     * Student self-service update. Creates the row on the fly if one
     * somehow doesn't exist yet (defensive — should always exist given the
     * creation hooks, but never fail the save over it).
     */
    @Transactional
    public StudentHealthInfo updateForStudent(AcademicStudent academicStudent,
                                               Integer height,
                                               Integer weight,
                                               boolean haveHealthIssues,
                                               boolean haveEyeIssue,
                                               String healthIssueDescription,
                                               UserEntity actor) {
        StudentHealthInfo info = repository.findByAcademicStudent_Id(academicStudent.getId())
                .orElseGet(() -> createBlank(academicStudent, actor));

        info.setHeight(height);
        info.setWeight(weight);
        info.setHaveHealthIssues(haveHealthIssues);
        info.setHaveEyeIssue(haveEyeIssue);
        info.setHealthIssueDescription(
                (haveHealthIssues || haveEyeIssue) ? healthIssueDescription : null);
        info.setUpdatedBy(actor);
        return repository.save(info);
    }
}
