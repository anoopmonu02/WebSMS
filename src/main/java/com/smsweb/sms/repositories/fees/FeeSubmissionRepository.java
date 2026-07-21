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

    /**
     * Batch existence check: which of these students have AT LEAST ONE Active fee-submission
     * row this academic year (regardless of month or current balance). Used to distinguish
     * "has submitted, latest balance is genuinely 0" from "never submitted this year — the
     * opening balance carried from a previous year/system still applies" — see
     * calculatePendingFeeSummary()'s use of this alongside getLatestBalanceAmountsForStudents,
     * which only returns rows with balance_amount > 0 and so can't tell those two cases apart
     * on its own.
     */
    @Query("SELECT DISTINCT fs.academicStudent.id FROM FeeSubmission fs " +
            "WHERE fs.school.id = :schoolId AND fs.academicYear.id = :academicYearId " +
            "AND fs.academicStudent.id IN :studentIds AND LOWER(fs.status) = 'active'")
    List<Long> findAcademicStudentIdsWithAnySubmission(@Param("schoolId") Long schoolId,
                                                        @Param("academicYearId") Long academicYearId,
                                                        @Param("studentIds") List<Long> studentIds);

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
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Active' " +
                    "  AND DATE(fs.fee_submission_date) = STR_TO_DATE(:date, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findFeeSubmissionAggregatesForCurrentDate(
            @Param("date") String date,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query(
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Active' " +
                    "  AND DATE(fs.fee_submission_date) BETWEEN STR_TO_DATE(:startDate, '%d/%b/%Y') AND STR_TO_DATE(:endDate, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findFeeSubmissionAggregatesForDateRange(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query(
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Inactive' " +
                    "  AND DATE(fs.fee_submission_date) = STR_TO_DATE(:date, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findCancelledFeeAggregatesForCurrentDate(
            @Param("date") String date,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query(
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Inactive' " +
                    "  AND DATE(fs.fee_submission_date) BETWEEN STR_TO_DATE(:startDate, '%d/%b/%Y') AND STR_TO_DATE(:endDate, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findCancelledFeeAggregatesForDateRange(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);

    @Query("SELECT DISTINCT f FROM FeeSubmission f LEFT JOIN FETCH f.createdBy u" +
            "       LEFT JOIN FETCH u.employee " +
            "       LEFT JOIN FETCH u.student " +
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

    // ── Self-service "My Collection" (FEE_REPORT_OWN_COLLECTION) — scoped to a single createdById ──

    @Query(
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Active' " +
                    "  AND DATE(fs.fee_submission_date) = STR_TO_DATE(:date, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "  AND fs.created_by = :createdById " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findOwnFeeSubmissionAggregatesForCurrentDate(
            @Param("date") String date,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("createdById") Long createdById);

    @Query(
            value = "SELECT SUM(fs.paid_amount) AS totalPaid, COUNT(fs.id) AS totalCount, fs.created_by as createdBy, u.username AS createdByUser " +
                    "FROM fee_submission fs INNER JOIN users u ON u.id = fs.created_by " +
                    "WHERE fs.status = 'Active' " +
                    "  AND DATE(fs.fee_submission_date) BETWEEN STR_TO_DATE(:startDate, '%d/%b/%Y') AND STR_TO_DATE(:endDate, '%d/%b/%Y') " +
                    "  AND fs.school_id = :schoolId " +
                    "  AND fs.academic_year_id = :academicYearId " +
                    "  AND fs.created_by = :createdById " +
                    "GROUP BY fs.created_by ",
            nativeQuery = true)
    List<Object[]> findOwnFeeSubmissionAggregatesForDateRange(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("createdById") Long createdById);

    @Query("SELECT DISTINCT f FROM FeeSubmission f LEFT JOIN FETCH f.createdBy u" +
            "       LEFT JOIN FETCH u.employee " +
            "       LEFT JOIN FETCH u.student " +
            "WHERE f.status = :status " +
            "  AND f.school.id = :schoolId " +
            "  AND f.academicYear.id = :academicYearId " +
            "  AND f.createdBy.id = :createdById " +
            "  AND ((:startDate IS NOT NULL AND :endDate IS NOT NULL " +
            "        AND function('DATE', f.feeSubmissionDate) BETWEEN function('STR_TO_DATE', :startDate, '%d/%b/%Y') " +
            "                                                   AND function('STR_TO_DATE', :endDate, '%d/%b/%Y')) " +
            "       OR (:startDate IS NULL AND :endDate IS NULL " +
            "           AND function('DATE', f.feeSubmissionDate) = function('STR_TO_DATE', :feeDate, '%d/%b/%Y')))")
    List<FeeSubmission> findAllFeeDetailsByUserAndCreatedBy(
            @Param("status") String status,
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("feeDate") String feeDate,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("createdById") Long createdById);

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


    @Query("SELECT DISTINCT f FROM FeeSubmission f LEFT JOIN FETCH f.createdBy u LEFT JOIN FETCH u.employee LEFT JOIN FETCH u.student WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.medium.id = :medium")
    List<FeeSubmission> findAllFeeSubmittedDetails(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium);

    @Query("SELECT DISTINCT f FROM FeeSubmission f LEFT JOIN FETCH f.createdBy u LEFT JOIN FETCH u.employee LEFT JOIN FETCH u.student WHERE f.school.id = :schoolId AND f.academicYear.id = :academicYearId AND f.academicStudent.medium.id = :medium AND f.academicStudent.grade.id = :grade AND f.academicStudent.section.id = :section")
    List<FeeSubmission> findAllFeeSubmittedDetailsGradeWise(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("medium") Long medium,
            @Param("grade") Long grade, @Param("section") Long section);

    @Query(value="""
            SELECT COALESCE(SUM(f.paid_amount), 0) FROM Fee_Submission f 
            WHERE DATE(f.fee_submission_date) = CURDATE() AND f.school_id = :school AND f.academic_Year_id = :academic and LOWER(f.status) = 'active'
            """,nativeQuery = true)
    BigDecimal getTodayTotalFeeSubmission(@Param("school") Long school,
                                          @Param("academic") Long academic);

    @Query("SELECT SUM(f.amount), f.grade.id, f.grade.gradeName FROM FeeClassMap f where f.school.id = :school AND f.academicYear.id = :academic AND f.grade.id in(:gradeIds) GROUP BY f.grade.id, f.grade.gradeName")
    List<Object[]> getGradewiseTutionFees(@Param("school") Long school,
                                      @Param("academic") Long academic, @Param("gradeIds") List<Long> gradeIds);

    @Query(value = """
            SELECT SUM(fcm.amount) AS monthlyFee, fcm.grade_id, g.grade_name
            FROM fee_class_map fcm
            JOIN fee_month_map fmm
                ON fcm.feehead_id = fmm.feehead_id
                AND fcm.school_id = fmm.school_id
                AND fcm.academic_year_id = fmm.academic_year_id
            JOIN month_master mm ON mm.id = fmm.month_master_id
            JOIN grade g ON g.id = fcm.grade_id
            JOIN feehead fh ON fh.id = fcm.feehead_id
            WHERE fcm.school_id = :school
                AND fcm.academic_year_id = :academic
                AND fcm.grade_id IN (:gradeIds)
                AND LOWER(mm.month_name) = LOWER(MONTHNAME(CURDATE()))
                AND fmm.is_applicable = true
                AND LOWER(fh.fee_head_name) = 'monthly fee'
            GROUP BY fcm.grade_id, g.grade_name
            """, nativeQuery = true)
    List<Object[]> getGradewiseTutionFeesCurrentMonth(@Param("school") Long school,
                                                      @Param("academic") Long academic,
                                                      @Param("gradeIds") List<Long> gradeIds);

    /*@Query("""
    SELECT COUNT(s.academicStudent.id) AS studentCount,
           dc.amount AS amount,
           (COUNT(s.academicStudent.id) * dc.amount) AS total,
           a.grade.id AS gradeName, a.section.id as sectionName
    FROM AcademicStudent  a left join StudentDiscount s ON a.id=s.academicStudent.id AND s.status = 'Active'
    JOIN DiscountClassMap dc
         ON dc.discounthead.id = s.discounthead.id
         AND dc.academicYear.id = s.academicYear.id
         AND dc.school.id = s.school.id
    JOIN DiscountMonthMap dm
         ON dm.school.id = s.school.id
         AND dm.academicYear.id = s.academicYear.id
         AND dm.discounthead.id = s.discounthead.id
         AND dc.discounthead.id = dm.discounthead.id
    WHERE 
      s.academicYear.id = :academicYearId
      AND s.school.id = :schoolId
      AND dm.monthMaster.id = FUNCTION('MONTH', CURRENT_DATE) AND a.grade.gradeName = :grade AND a.section.sectionName = :section AND dc.grade.id = a.grade.id 
    GROUP BY a.grade.id, a.section.id, dc.amount
""")
    List<Object[]> getStudentDiscountSummary(@Param("academicYearId") Long academicYearId,
                                             @Param("schoolId") Long schoolId, @Param("grade") String grade, @Param("section") String section);*/

    @Query(value = """
    SELECT 
        COUNT(s.academic_student_id) AS student_count,
        IFNULL(dc.amount, 0) AS amount,
        (COUNT(s.academic_student_id) * IFNULL(dc.amount, 0)) AS total,
        g.grade_name AS gradeName,
        sec.section_name AS sectionName
    FROM academic_students a
    JOIN student_discount s 
        ON a.id = s.academic_student_id
    JOIN discount_class_map dc 
        ON dc.discounthead_id = s.discount_head_id
        AND s.academic_year_id = dc.academic_year_id
        AND s.school_id = dc.school_id
    JOIN discount_month_map dm 
        ON s.school_id = dm.school_id
        AND s.academic_year_id = dm.academic_year_id
        AND s.discount_head_id = dm.discounthead_id
    JOIN grade g 
        ON a.grade_id = g.id
    JOIN section sec 
        ON a.section_id = sec.id
    WHERE (LOWER(s.status) = 'active')
      AND s.academic_year_id = :academicYearId  
      AND s.school_id = :schoolId                
      AND dm.month_master_id = MONTH(curdate())
      AND g.grade_name = :gradeName
      AND sec.section_name = :sectionName
      AND dm.is_applicable = true
      AND (a.grade_id = dc.grade_id OR dc.grade_id IS NULL)
    GROUP BY a.grade_id, a.section_id, dc.amount
""", nativeQuery = true)
    List<Object[]> getStudentDiscountSummary(
            @Param("academicYearId") Long academicYearId,
            @Param("schoolId") Long schoolId,
            @Param("gradeName") String gradeName,
            @Param("sectionName") String sectionName
    );

    @Query(value = """
    SELECT MAX(id)
    FROM fee_submission
    WHERE academic_student_id = :studentId and status = :status and academic_year_id = :academicId
    """, nativeQuery = true)
    Long findLatestSubmissionId(@Param("studentId") Long studentId,
                                @Param("status") String status,
                                @Param("academicId") Long academicId);

    /**
     * Pending Fee Summary Report — aggregate query.
     * Returns [academic_student_id, submitted_month_count] for students who have submitted
     * at least one of the given months. Students absent from result have submitted 0 months.
     */
    @Query(value = """
        SELECT fs.academic_student_id, COUNT(DISTINCT fsm.month_master_id) AS submitted_count
        FROM fee_submission fs
        JOIN fee_submission_months fsm ON fsm.fee_submission_id = fs.id
        WHERE fs.school_id          = :schoolId
          AND fs.academic_year_id   = :academicYearId
          AND fs.academic_student_id IN (:studentIds)
          AND fsm.month_master_id   IN (:monthIds)
          AND LOWER(fs.status)      = 'active'
        GROUP BY fs.academic_student_id
    """, nativeQuery = true)
    List<Object[]> getSubmittedMonthCountsForStudents(
            @Param("schoolId")       Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentIds")     List<Long> studentIds,
            @Param("monthIds")       List<Long> monthIds
    );

    /**
     * Pending Fee Summary Report — returns WHICH specific months each student has paid.
     * Result: [academic_student_id, month_master_id]
     */
    @Query(value = """
        SELECT DISTINCT fs.academic_student_id, fsm.month_master_id
        FROM fee_submission fs
        JOIN fee_submission_months fsm ON fsm.fee_submission_id = fs.id
        WHERE fs.school_id          = :schoolId
          AND fs.academic_year_id   = :academicYearId
          AND fs.academic_student_id IN (:studentIds)
          AND fsm.month_master_id   IN (:monthIds)
          AND LOWER(fs.status)      = 'active'
    """, nativeQuery = true)
    List<Object[]> getSubmittedMonthsForStudents(
            @Param("schoolId")       Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentIds")     List<Long> studentIds,
            @Param("monthIds")       List<Long> monthIds
    );


    /**
     * Pending Fee Summary Report — returns the latest carry-forward balance per student.
     * Only includes students who have a non-zero balance on their last active submission.
     * Result: [academic_student_id, balance_amount]
     */
    @Query(value = """
        SELECT fs.academic_student_id, fsb.balance_amount
        FROM fee_submission fs
        JOIN fee_submission_balance fsb ON fsb.fee_submission_id = fs.id
        JOIN (
            SELECT academic_student_id, MAX(id) AS max_id
            FROM fee_submission
            WHERE school_id = :schoolId
              AND academic_year_id = :academicYearId
              AND academic_student_id IN (:studentIds)
              AND LOWER(status) = 'active'
            GROUP BY academic_student_id
        ) latest ON latest.academic_student_id = fs.academic_student_id AND latest.max_id = fs.id
        WHERE LOWER(fsb.status) = 'active'
          AND fsb.balance_amount > 0
    """, nativeQuery = true)
    List<Object[]> getLatestBalanceAmountsForStudents(
            @Param("schoolId")       Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("studentIds")     List<Long> studentIds
    );

    @Query(value = """
        select h.fee_head_name, sum(fs.amount) as AMT
       from fee_submission f, fee_submission_sub fs, feehead h
       where f.id=fs.fee_submission_id and fs.fee_head_id=h.id and DATE(f.fee_submission_date) = STR_TO_DATE(:date, '%d/%b/%Y') and f.school_id=:schoolId and f.academic_year_id=:academicYearId and f.status='Active'
       group by  fs.fee_head_id
    """, nativeQuery = true)
    List<Object[]> getFeeSubmissionHeadWiseToday(@Param("date") String date,
                                                 @Param("schoolId") Long schoolId,
                                                 @Param("academicYearId") Long academicYearId);


    @Query(value = """
        select h.fee_head_name, sum(fs.amount) as AMT
       from fee_submission f, fee_submission_sub fs, feehead h
       where f.id=fs.fee_submission_id and fs.fee_head_id=h.id and DATE(fee_submission_date) BETWEEN STR_TO_DATE(:startDate, '%d/%b/%Y') AND STR_TO_DATE(:endDate, '%d/%b/%Y') and f.school_id=:schoolId and f.academic_year_id=:academicYearId and f.status='Active'
       group by  fs.fee_head_id
    """, nativeQuery = true)
    List<Object[]> getFeeSubmissionHeadWiseAggregatesForDateRange( @Param("startDate") String startDate,
                                                                   @Param("endDate") String endDate,
                                                                   @Param("schoolId") Long schoolId,
                                                                   @Param("academicYearId") Long academicYearId);


    @Query("""
    SELECT fb.feehead.feeHeadName,sum(fb.amount) as Total FROM FeeSubmission fs join fs.feeSubmissionSub fb
    LEFT JOIN fs.academicStudent ac
    LEFT JOIN ac.medium med
    LEFT JOIN ac.grade gr
    LEFT JOIN ac.section sec
    WHERE fs.school.id = :schoolId
    AND fs.academicYear.id = :academicYearId
    AND (:mediumId  IS NULL OR med.id  = :mediumId)
    AND (:gradeId   IS NULL OR gr.id   = :gradeId)
    AND (:sectionId IS NULL OR sec.id  = :sectionId) AND fs.status='Active' group by fb.feehead.feeHeadName
""")
    List<Object[]> findByGradeWiseFilters(
            @Param("schoolId")      Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("mediumId")      Long mediumId,
            @Param("gradeId")       Long gradeId,
            @Param("sectionId")     Long sectionId
    );

    @Query("""
    SELECT fb.feehead.feeHeadName,sum(fb.amount) as Total FROM FeeSubmission fs join fs.feeSubmissionSub fb
    LEFT JOIN fs.academicStudent ac
    LEFT JOIN ac.medium med
    LEFT JOIN ac.grade gr
    LEFT JOIN ac.section sec
    WHERE fs.school.id = :schoolId
    AND fs.academicYear.id = :academicYearId
    AND (:mediumId  IS NULL OR med.id  = :mediumId)
    AND (:gradeId   IS NULL OR gr.id   = :gradeId)
    AND (:sectionId IS NULL OR sec.id  = :sectionId) AND fs.status='Active'
    AND FUNCTION('MONTHNAME', fs.feeSubmissionDate) = :monthName
     group by fb.feehead.feeHeadName
""")
    List<Object[]> findByGradeWiseFiltersWithMonths(
            @Param("schoolId")      Long schoolId,
            @Param("academicYearId") Long academicYearId,
            @Param("mediumId")      Long mediumId,
            @Param("gradeId")       Long gradeId,
            @Param("sectionId")     Long sectionId,
            @Param("monthName") String monthName
    );

}
