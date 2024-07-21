package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeClassMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeclassmapRepository extends JpaRepository<FeeClassMap, Long> {

    List<FeeClassMap> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

    List<FeeClassMap> findAllByGrade_IdAndSchool_IdAndAcademicYear_Id(Long grade_id, Long school_id, Long academic_id);
}
