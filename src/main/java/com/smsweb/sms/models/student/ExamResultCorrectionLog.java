package com.smsweb.sms.models.student;

import com.smsweb.sms.models.Users.UserEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * Audit trail for the Admin/SuperAdmin-only bulk-correct flow
 * (StudentService.bulkCorrectExamResult). One row is written for every
 * ExamResultSummary row that gets inserted or updated through that flow —
 * never for the regular Teacher/Staff/Accountant upload path
 * (StudentService.uploadExamResult), which stays insert-only and untouched.
 *
 * For an update, old* fields capture the values immediately before the
 * change. For a fresh insert (no prior result existed for that
 * student+exam+date), old* fields are left null so it's visible in the log
 * that this row had no prior state.
 */
@Data
@Entity
@Table(name = "exam_result_correction_log")
public class ExamResultCorrectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_result_summary_id")
    @NotNull(message = "Exam result summary reference should be available")
    private ExamResultSummary examResultSummary;

    // Groups every row changed/inserted in the same bulk-correct submission
    // so a future "undo this batch" action has something to key off.
    @Column(name = "batch_id", nullable = false, updatable = false)
    private String batchId;

    // INSERT or UPDATE
    @Column(name = "change_type", nullable = false, updatable = false)
    private String changeType;

    @Column(name = "old_total_marks", updatable = false)
    private Long oldTotalMarks;

    @Column(name = "old_obtained_marks", updatable = false)
    private Long oldObtainedMarks;

    @Column(name = "old_percentage_marks", updatable = false)
    private Double oldPercentageMarks;

    @Column(name = "old_division", updatable = false)
    private String oldDivision;

    @Column(name = "old_result", updatable = false)
    private String oldResult;

    @Column(name = "new_total_marks", updatable = false)
    private Long newTotalMarks;

    @Column(name = "new_obtained_marks", updatable = false)
    private Long newObtainedMarks;

    @Column(name = "new_percentage_marks", updatable = false)
    private Double newPercentageMarks;

    @Column(name = "new_division", updatable = false)
    private String newDivision;

    @Column(name = "new_result", updatable = false)
    private String newResult;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    @Size(max = 500, message = "Reason should not exceed 500 characters")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by", nullable = false, updatable = false)
    private UserEntity correctedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date correctedAt;
}
