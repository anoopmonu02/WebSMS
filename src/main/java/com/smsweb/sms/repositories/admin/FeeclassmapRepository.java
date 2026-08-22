package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeClassMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeeclassmapRepository extends JpaRepository<FeeClassMap, Long> {

    /**
     * Medium-aware lookup used by calculateFullPaymentForDiscountedStudent. (Fee-medium
     * migration: the previous grade-only findByAcademicYear_IdAndSchool_IdAndGrade_IdAndFeehead_Id
     * was removed once its single caller switched over — a grade alone can no longer identify
     * one FeeClassMap row now that medium is part of the key.)
     */
    Optional<FeeClassMap> findByAcademicYear_IdAndSchool_IdAndGrade_IdAndMedium_IdAndFeehead_Id(
            Long academicYearId, Long schoolId, Long gradeId, Long mediumId, Long feeheadId);

    List<FeeClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    /**
     * Medium-aware lookup for the Fee Class Mapping admin screen. (Fee-medium migration: the
     * previous grade-only findAllByGrade_IdAndSchool_IdAndAcademicYear_Id was removed once
     * GlobalController's fee-class endpoints switched over — medium is now mandatory on
     * FeeClassMap, so a grade alone no longer identifies one set of rows.)
     */
    List<FeeClassMap> findAllByGrade_IdAndMedium_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long medium_id, Long school_id, Long academic_id);

    /**
     * Per-month fee breakdown for summary report.
     * Returns [amount, feeHeadName, monthMasterId] — one row per fee head per month.
     * Unlike findAmountAndFeeHeadNames this does NOT aggregate across months.
     */
    @Query(value = "SELECT fcm.amount as amt, fh.fee_head_name as FeeName, fmm.month_master_id " +
            "FROM fee_class_map fcm " +
            "JOIN fee_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id AND fcm.feehead_id = fmm.feehead_id " +
            "JOIN feehead fh ON fh.id = fcm.feehead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId", nativeQuery = true)
    List<Object[]> findFeeDetailsPerMonth(@Param("academicYearId") Long academicYearId,
                                          @Param("schoolId") Long schoolId,
                                          @Param("monthMasterIds") List<Long> monthMasterIds,
                                          @Param("gradeId") Long gradeId);

    /**
     * Medium-aware overload of findFeeDetailsPerMonth above, used everywhere a student's medium
     * is resolvable. The 4-arg overload above is intentionally kept — getMonthlyFeeTable falls
     * back to it only when a student's medium can't be resolved (shouldn't happen in practice,
     * but that's the one caller keeping it alive; not dead code).
     */
    @Query(value = "SELECT fcm.amount as amt, fh.fee_head_name as FeeName, fmm.month_master_id " +
            "FROM fee_class_map fcm " +
            "JOIN fee_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id AND fcm.feehead_id = fmm.feehead_id " +
            "JOIN feehead fh ON fh.id = fcm.feehead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId " +
            "AND fcm.medium_id = :mediumId", nativeQuery = true)
    List<Object[]> findFeeDetailsPerMonth(@Param("academicYearId") Long academicYearId,
                                          @Param("schoolId") Long schoolId,
                                          @Param("monthMasterIds") List<Long> monthMasterIds,
                                          @Param("gradeId") Long gradeId,
                                          @Param("mediumId") Long mediumId);

    /**
     * Medium-aware amount lookup — this is the one the actual fee calculation in
     * FeeSubmissionService resolves amounts through, so it's the highest-traffic change in this
     * migration. (Fee-medium migration: the previous grade-only 4-arg findAmountAndFeeHeadNames
     * was removed once every caller switched to passing mediumId.)
     */
    @Query(value = "SELECT SUM(fcm.amount) as amt, fh.fee_head_name as FeeName, count(fmm.month_master_id) as qty, fcm.feehead_id " +
            "FROM fee_class_map fcm " +
            "JOIN fee_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id " +
            "AND fcm.feehead_id = fmm.feehead_id " +
            "JOIN feehead fh ON fh.id = fcm.feehead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fmm.academic_year_id = :academicYearId " +
            "AND fmm.school_id = :schoolId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId " +
            "AND fcm.medium_id = :mediumId " +
            "GROUP BY fh.fee_head_name, fcm.feehead_id", nativeQuery = true)
    List<Object[]> findAmountAndFeeHeadNames(@Param("academicYearId") Long academicYearId,
                                             @Param("schoolId") Long schoolId,
                                             @Param("monthMasterIds") List<Long> monthMasterIds,
                                             @Param("gradeId") Long gradeId,
                                             @Param("mediumId") Long mediumId);
}
