package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.MonthMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthmappingRepository extends JpaRepository<MonthMapping, Long> {
    List<MonthMapping> findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(Long id, Long schoolid);
}
