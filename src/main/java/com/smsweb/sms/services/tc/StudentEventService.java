package com.smsweb.sms.services.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.StudentExecution;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.repositories.tc.StudentExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Writes rows to the append-only student timeline ({@code student_execution}).
 *
 * <p>{@link #log} joins the caller's transaction (propagation MANDATORY): the status change and
 * its timeline row commit or roll back together. Calling it without a transaction fails loudly
 * instead of silently writing on its own.</p>
 */
@Service
public class StudentEventService {

    private static final Logger log = LoggerFactory.getLogger(StudentEventService.class);

    private static final int MAX_VALUE_LENGTH = 255;
    private static final int MAX_REMARK_LENGTH = 500;

    private final StudentExecutionRepository studentExecutionRepository;

    public StudentEventService(StudentExecutionRepository studentExecutionRepository) {
        this.studentExecutionRepository = studentExecutionRepository;
    }

    /**
     * Adds one timeline row.
     *
     * @param academicStudent the enrollment affected, or null; when given, its grade, section and
     *                        medium are stored as the class snapshot
     * @param oldValue        optional, for example the old SR number (cut to 255 characters)
     * @param newValue        optional, for example a receipt number (cut to 255 characters)
     * @param remark          optional (cut to 500 characters)
     * @param refType         optional pointer type (EXIT, TC, LINK, RECEIPT)
     * @param refId           optional pointer id
     * @param source          defaults to WEB when null
     * @param user            the user who did it; may be null only for SYSTEM or IMPORT events
     * @return the saved row (managed, so the caller may still set {@code exitReason} before commit)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StudentExecution log(EventType eventType,
                                Student student,
                                AcademicStudent academicStudent,
                                AcademicYear academicYear,
                                LocalDate eventDate,
                                String oldValue,
                                String newValue,
                                String remark,
                                RefType refType,
                                Long refId,
                                EventSource source,
                                UserEntity user) {
        log.info("Inside log: event={}, studentId={}", eventType, student == null ? null : student.getId());
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(student, "student is required");
        Objects.requireNonNull(academicYear, "academicYear is required");
        Objects.requireNonNull(eventDate, "eventDate is required");

        StudentExecution row = new StudentExecution();
        row.setSchool(student.getSchool());
        row.setAcademicYear(academicYear);
        row.setStudent(student);
        row.setAcademicStudent(academicStudent);
        row.setEventType(eventType);
        row.setEventDate(eventDate);
        if (academicStudent != null) {
            row.setGrade(academicStudent.getGrade());
            row.setSection(academicStudent.getSection());
            row.setMedium(academicStudent.getMedium());
        }
        row.setOldValue(cut(oldValue, MAX_VALUE_LENGTH));
        row.setNewValue(cut(newValue, MAX_VALUE_LENGTH));
        row.setRemark(cut(remark, MAX_REMARK_LENGTH));
        row.setRefType(refType);
        row.setRefId(refId);
        row.setSource(source == null ? EventSource.WEB : source);
        row.setCreatedBy(user);
        return studentExecutionRepository.save(row);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
