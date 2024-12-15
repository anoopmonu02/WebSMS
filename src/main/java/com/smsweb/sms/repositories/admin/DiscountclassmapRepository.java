package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.DiscountClassMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountclassmapRepository extends JpaRepository<DiscountClassMap, Long> {
    List<DiscountClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<DiscountClassMap> findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long school_id, Long academic_id);

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

    Optional<DiscountClassMap> findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_Id(String discountName, Long academic_year, Long school, Long grade);
}
