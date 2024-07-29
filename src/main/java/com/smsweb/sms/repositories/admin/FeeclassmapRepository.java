package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeClassMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeeclassmapRepository extends JpaRepository<FeeClassMap, Long> {

    List<FeeClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<FeeClassMap> findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long school_id, Long academic_id);

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
            "GROUP BY fh.fee_head_name, fcm.feehead_id", nativeQuery = true)
    List<Object[]> findAmountAndFeeHeadNames(@Param("academicYearId") Long academicYearId,
                                             @Param("schoolId") Long schoolId,
                                             @Param("monthMasterIds") List<Long> monthMasterIds,
                                             @Param("gradeId") Long gradeId);
}
