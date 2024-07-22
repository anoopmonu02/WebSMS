package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.DiscountClassMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountclassmapRepository extends JpaRepository<DiscountClassMap, Long> {
    List<DiscountClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<DiscountClassMap> findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long school_id, Long academic_id);
}
