package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findAllByAcademicYear_IdAndSchool_IdOrderByIdAsc(Long academic_id, Long school_id);
}
