package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.DiscountClassMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountclassmapRepository extends JpaRepository<DiscountClassMap, Long> {
    List<DiscountClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    /**
     * Medium-aware lookup for the Discount Class Mapping admin screen. (Discount-medium
     * migration: the previous grade-only findAllByGrade_IdAndSchool_IdAndAcademicYear_Id was
     * removed once GlobalController's discount-class endpoints switched over — medium is now
     * mandatory on DiscountClassMap, so a grade alone no longer identifies one set of rows.
     * Mirrors FeeclassmapRepository.findAllByGrade_IdAndMedium_IdAndSchool_IdAndAcademicYear_Id.)
     */
    List<DiscountClassMap> findAllByGrade_IdAndMedium_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long medium_id, Long school_id, Long academic_id);

    @Query(value = "SELECT SUM(fcm.amount) as amt, fh.discount_name as DiscountName, count(fmm.month_master_id) as qty, fcm.discounthead_id, fcm.amount as SAmount " +
            "FROM discount_class_map fcm " +
            "JOIN discount_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id " +
            "AND fcm.discounthead_id = fmm.discounthead_id " +
            "JOIN discounthead fh ON fh.id = fcm.discounthead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fmm.academic_year_id = :academicYearId " +
            "AND fmm.school_id = :schoolId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId AND fh.id = :discountId " +
            "GROUP BY fh.discount_name, fcm.discounthead_id", nativeQuery = true)
    List<Object[]> findAmountAndDiscountHeadNames(@Param("academicYearId") Long academicYearId,
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("monthMasterIds") List<Long> monthMasterIds,
                                                  @Param("gradeId") Long gradeId,
                                                  @Param("discountId") Long discountId);

    /**
     * Medium-aware overload of findAmountAndDiscountHeadNames above, used everywhere a
     * student's medium is resolvable (Discount-medium migration). The 5-arg overload above is
     * intentionally kept as a fallback for the one call site where a student's medium can't be
     * resolved (getDiscountDetailsBasedOnMonth) — not dead code.
     */
    @Query(value = "SELECT SUM(fcm.amount) as amt, fh.discount_name as DiscountName, count(fmm.month_master_id) as qty, fcm.discounthead_id, fcm.amount as SAmount " +
            "FROM discount_class_map fcm " +
            "JOIN discount_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id " +
            "AND fcm.discounthead_id = fmm.discounthead_id " +
            "JOIN discounthead fh ON fh.id = fcm.discounthead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fmm.academic_year_id = :academicYearId " +
            "AND fmm.school_id = :schoolId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId AND fh.id = :discountId " +
            "AND fcm.medium_id = :mediumId " +
            "GROUP BY fh.discount_name, fcm.discounthead_id", nativeQuery = true)
    List<Object[]> findAmountAndDiscountHeadNames(@Param("academicYearId") Long academicYearId,
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("monthMasterIds") List<Long> monthMasterIds,
                                                  @Param("gradeId") Long gradeId,
                                                  @Param("discountId") Long discountId,
                                                  @Param("mediumId") Long mediumId);

    /**
     * Per-month discount breakdown — same shape as
     * FeeclassmapRepository.findFeeDetailsPerMonth (one row per applicable
     * month, not aggregated) so the two can be joined by month_master_id.
     * Returns [amount, discountName, monthMasterId].
     */
    @Query(value = "SELECT fcm.amount as amt, fh.discount_name as DiscountName, fmm.month_master_id " +
            "FROM discount_class_map fcm " +
            "JOIN discount_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id AND fcm.discounthead_id = fmm.discounthead_id " +
            "JOIN discounthead fh ON fh.id = fcm.discounthead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId AND fh.id = :discountId", nativeQuery = true)
    List<Object[]> findDiscountDetailsPerMonth(@Param("academicYearId") Long academicYearId,
                                               @Param("schoolId") Long schoolId,
                                               @Param("monthMasterIds") List<Long> monthMasterIds,
                                               @Param("gradeId") Long gradeId,
                                               @Param("discountId") Long discountId);

    /**
     * Medium-aware overload of findDiscountDetailsPerMonth above (Discount-medium migration).
     * The 5-arg overload above is intentionally kept — getMonthlyFeeTable falls back to it
     * only when a student's medium can't be resolved, same fallback pattern as
     * FeeclassmapRepository.findFeeDetailsPerMonth.
     */
    @Query(value = "SELECT fcm.amount as amt, fh.discount_name as DiscountName, fmm.month_master_id " +
            "FROM discount_class_map fcm " +
            "JOIN discount_month_map fmm ON fcm.academic_year_id = fmm.academic_year_id " +
            "AND fcm.school_id = fmm.school_id AND fcm.discounthead_id = fmm.discounthead_id " +
            "JOIN discounthead fh ON fh.id = fcm.discounthead_id " +
            "WHERE fcm.academic_year_id = :academicYearId " +
            "AND fcm.school_id = :schoolId " +
            "AND fmm.month_master_id IN (:monthMasterIds) " +
            "AND fmm.is_applicable = true " +
            "AND fcm.grade_id = :gradeId AND fh.id = :discountId " +
            "AND fcm.medium_id = :mediumId", nativeQuery = true)
    List<Object[]> findDiscountDetailsPerMonth(@Param("academicYearId") Long academicYearId,
                                               @Param("schoolId") Long schoolId,
                                               @Param("monthMasterIds") List<Long> monthMasterIds,
                                               @Param("gradeId") Long gradeId,
                                               @Param("discountId") Long discountId,
                                               @Param("mediumId") Long mediumId);

    Optional<DiscountClassMap> findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_Id(String discountName, Long academic_year, Long school, Long grade);

    /**
     * Medium-aware overload of the sibling-discount lookup above (Discount-medium migration).
     * Both call sites (SiblingDiscountController, SiblingDiscountService) resolve an
     * AcademicStudent first and always have its medium available, so they use this one; the
     * grade-only overload above is kept only in case a future caller genuinely can't resolve
     * medium (mirrors the fallback pattern used elsewhere in this migration).
     */
    Optional<DiscountClassMap> findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_IdAndMedium_Id(String discountName, Long academic_year, Long school, Long grade, Long medium);
}
