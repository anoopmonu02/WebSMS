package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.DiscountMonthMap;
import com.smsweb.sms.models.admin.FeeMonthMap;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountmonthmapRepository extends JpaRepository<DiscountMonthMap, Long> {
    List<DiscountMonthMap> findAllBySchool_IdAndAcademicYear_IdOrderByDiscountheadAscMonthMasterAsc(Long school_id, Long academic_id, Sort sort);

    List<DiscountMonthMap> findAllBySchool_IdAndAcademicYear_IdAndDiscounthead_Id(Long school_id, Long academic_id, Long discounthead_id);
}
