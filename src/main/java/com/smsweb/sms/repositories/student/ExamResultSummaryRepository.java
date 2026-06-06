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

    // ── Mobile API queries ────────────────────────────────────────────────────

    /**
     * Returns all exam results for a student in a given school + academic year.
     * Used by the Results screen in the mobile app.
     * Results are ordered latest first.
     */
    @Query("SELECT r FROM ExamResultSummary r " +
           "JOIN FETCH r.examDetails e " +
           "WHERE r.academicStudent.id = :academicStudentId " +
           "AND r.school.id = :schoolId " +
           "AND r.academicYear.id = :academicYearId " +
           "ORDER BY r.examResultDate DESC")
    List<ExamResultSummary> findByAcademicStudentIdAndSchoolIdAndAcademicYearId(
            @Param("academicStudentId") Long academicStudentId,
            @Param("schoolId")         Long schoolId,
            @Param("academicYearId")   Long academicYearId);
}
