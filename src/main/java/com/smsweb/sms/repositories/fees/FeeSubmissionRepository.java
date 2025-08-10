package com.smsweb.sms.repositories.fees;


import com.smsweb.sms.models.fees.FeeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface FeeSubmissionRepository extends JpaRepository<FeeSubmission, Long> {


    List<FeeSubmission> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<FeeSubmission> findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(Long school_id, Long academic_id, Long academic_student_id);

    List<FeeSubmission> findAllByAcademicStudent_Id(Long academic_student_id);
    List<FeeSubmission> findAllByAcademicStudent_IdAndStatus(Long academic_student_id, String status);

    @Query("SELECT f FROM FeeSubmission f JOIN f.feeSubmissionBalance b WHERE f.school.id = :schoolId AND f.academicStudent.id = :academicStudentId AND f.status='Active' AND b.status='Active' ORDER BY f.id DESC")
    List<FeeSubmission> findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(@Param("schoolId") Long schoolId, @Param("academicStudentId") Long academicStudentId);

    @Query("SELECT fs FROM FeeSubmission fs JOIN fs.academicStudent astu WHERE fs.school.id = :schoolId and fs.academicYear.id = :academicId AND fs.academicStudent.id = :academicStudentId AND fs.status='Active'")
    List<FeeSubmission> findAllBySchoolIdAndAcademicIdAndAcademicStudentId(@Param("schoolId") Long schoolId, @Param("academicId") Long academicId, @Param("academicStudentId") Long academicStudentId);

    int countAllByAcademicYear_IdAndSchool_IdAndAcademicStudent_IdAndStatus(Long academic_id, Long school_id, Long astudent_id, String status);

    @Query("SELECT MAX(f.feeSubmissionDate) FROM FeeSubmission f")
    Date findMaxSubmissionDate();

    @Query(value = "select ((select m.priority from month_master mm join month_mapping m on m.month_master_id=mm.id where mm.month_name=:monthName and m.academic_year_id=:academicId and m.school_id=:schoolId)-" +
            "(select m.priority from month_master mm join month_mapping m on m.month_master_id=mm.id where mm.month_name=monthname(curdate()) and m.academic_year_id=:academicId and m.school_id=:schoolId)) " +
            "as Result",
            nativeQuery = true)
    int getMonthDiffForFine(@Param("monthName") String monthName, @Param("academicId") Long academicId, @Param("schoolId") Long schoolId);

    @Query(value = "SELECT DATEDIFF(STR_TO_DATE(:feeSubmissionDate, '%d/%b/%Y'), CURDATE()) AS DTDIFF", nativeQuery = true)
    int getDateDifference(@Param("feeSubmissionDate") String feeSubmissionDate);



    default boolean canSubmitFee(Date submissionDate) {
        Date maxSubmissionDate = findMaxSubmissionDate();
        return maxSubmissionDate == null || submissionDate.after(maxSubmissionDate);
    }

    Optional<FeeSubmission> findByReceiptNoAndStatusAndSchool_IdAndAcademicYear_Id(String receipt_no, String status, Long school_id, Long academic_id);

    @Query("SELECT f FROM FeeSubmission f WHERE LOWER(f.receiptNo) = LOWER(:receiptNo) AND f.status = :status AND f.school.id = :schoolId AND f.academicYear.id = :academicYearId")
    FeeSubmission findByReceiptNoIgnoreCaseAndStatusAndSchoolIdAndAcademicYearId(
            @Param("receiptNo") String receiptNo,
            @Param("status") String status,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId
    );

    @Query(
            value = "SELECT SUM(paid_amount) AS totalPaid, COUNT(id) AS totalCount, created_by as createdBy " +
                    "FROM fee_submission " +
                    "WHERE status = 'active' " +
                    "  AND DATE(fee_submission_date) = STR_TO_DATE(:date, '%d/%b/%Y') " +
                    "  AND school_id = :schoolId " +
                    "  AND academic_year_id = :academicYearId " +
                    "GROUP BY created_by ",
            nativeQuery = true)
    List<Object[]> findFeeSubmissionAggregatesForCurrentDate(
            @Param("date") String date,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query(
            value = "SELECT SUM(paid_amount) AS totalPaid, COUNT(id) AS totalCount, created_by as createdBy " +
                    "FROM fee_submission " +
                    "WHERE status = 'Active' " +
                    "  AND DATE(fee_submission_date) BETWEEN STR_TO_DATE(:startDate, '%d/%b/%Y') AND STR_TO_DATE(:endDate, '%d/%b/%Y') " +
                    "  AND school_id = :schoolId " +
                    "  AND academic_year_id = :academicYearId " +
                    "GROUP BY created_by ",
            nativeQuery = true)
    List<Object[]> findFeeSubmissionAggregatesForDateRange(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query("SELECT f FROM FeeSubmission f " +
            "WHERE f.status = :status " +
            "  AND f.school.id = :schoolId " +
            "  AND f.academicYear.id = :academicYearId " +
            "  AND ((:startDate IS NOT NULL AND :endDate IS NOT NULL " +
            "        AND function('DATE', f.feeSubmissionDate) BETWEEN function('STR_TO_DATE', :startDate, '%d/%b/%Y') " +
            "                                                   AND function('STR_TO_DATE', :endDate, '%d/%b/%Y')) " +
            "       OR (:startDate IS NULL AND :endDate IS NULL " +
            "           AND function('DATE', f.feeSubmissionDate) = function('STR_TO_DATE', :feeDate, '%d/%b/%Y')))")
    List<FeeSubmission> findAllFeeDetailsByUser(
            @Param("status") String status,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("feeDate") String feeDate,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Query("SELECT f FROM FeeSubmission f " +
            "WHERE f.status = :status " +
            "  AND f.school.id = :schoolId " +
            "  AND f.academicYear.id = :academicYearId " +
            "  AND ((:startDate IS NOT NULL AND :endDate IS NOT NULL " +
            "        AND function('DATE', f.feeSubmissionDate) BETWEEN function('STR_TO_DATE', :startDate, '%d/%b/%Y') " +
            "                                                   AND function('STR_TO_DATE', :endDate, '%d/%b/%Y')) " +
            "       OR (:startDate IS NULL AND :endDate IS NULL " +
            "           AND function('DATE', f.feeSubmissionDate) = function('STR_TO_DATE', :feeDate, '%d/%b/%Y')))")
    List<FeeSubmission> findAllFeeDetailsBasedOnStatusAndInDateRange(
            @Param("status") String status,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("feeDate") String feeDate,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);


    @Query("SELECT f FROM FeeSubmission f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.medium.id = :medium")
    List<FeeSubmission> findAllFeeSubmittedDetails(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium);

    @Query("SELECT f FROM FeeSubmission f WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.medium.id = :medium AND f.academicStudent.grade.id = :grade AND f.academicStudent.section.id = :section")
    List<FeeSubmission> findAllFeeSubmittedDetailsGradeWise(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium,
            @Param("grade") Long grade, @Param("section") Long section);

    @Query("SELECT COALESCE(SUM(f.paidAmount), 0) FROM FeeSubmission f " +
            "WHERE f.feeSubmissionDate = CURRENT_DATE AND f.school.id = :school AND f.academicYear.id = :academic and f.status = 'ACTIVE'")
    BigDecimal getTodayTotalFeeSubmission(@Param("school") Long school,
                                          @Param("academic") Long academic);
}
