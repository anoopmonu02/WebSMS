package com.smsweb.sms.models.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "exam_result_summary")
public class ExamResultSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    @NotNull(message = "Exam should be available")
    private ExamDetails examDetails;

    @NotNull(message = "Exam result date should be available")
    @DateTimeFormat(pattern = "dd/MMM/yyyy")
    private Date examResultDate;

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-year should be available")
    private AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "academic_student_id")
    @NotNull(message = "Academic student should be available")
    private AcademicStudent academicStudent;

    private String result;

    private Long totalMarks = 0L;

    private Long obtainedMarks = 0L;

    private Double percentageMarks = 0.0;

    private String division;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Remark should not exceed 500 characters")
    private String remarks;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;

    @JoinColumn(name = "created_by", updatable = false)
    private String createdBy;

    @JoinColumn(name = "updated_by")
    private String updatedBy;

    @Column(updatable = false, nullable = false, unique = true)
    private UUID uuid;
    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
