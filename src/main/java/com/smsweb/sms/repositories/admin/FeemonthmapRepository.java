package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeMonthMap;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeemonthmapRepository extends JpaRepository<FeeMonthMap, Long> {
    List<FeeMonthMap> findAllBySchool_IdAndAcademicYear_IdOrderByFeeheadAscMonthMasterAsc(Long school_id, Long academic_id, Sort sort);

    List<FeeMonthMap> findAllBySchool_IdAndAcademicYear_IdAndFeehead_Id(Long school_id, Long academic_id, Long feehead_id);

}
