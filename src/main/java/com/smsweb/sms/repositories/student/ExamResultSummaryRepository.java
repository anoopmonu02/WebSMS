package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.student.ExamResultSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamResultSummaryRepository extends JpaRepository<ExamResultSummary, Long> {
    @Query("SELECT f FROM ExamResultSummary f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.medium.id = :mediumId AND f.academicStudent.grade.id = :gradeId AND f.academicStudent.section.id = :sectionId AND f.examDetails = :examResultsObj")
    List<ExamResultSummary> getExamResultSummariesBy(@Param("schoolId") Long schoolId,
                                                     @Param("academicYearId") Long academicYearId,
                                                     @Param("mediumId") Long mediumId,
                                                     @Param("gradeId") Long gradeId, @Param("sectionId") Long sectionId,
                                                     @Param("examResultsObj")ExamDetails examResultsObj);
}
