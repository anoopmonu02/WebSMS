package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FeeDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedateRepository extends JpaRepository<FeeDate, Long> {
    List<FeeDate> findAllByAcademicYear_IdAndSchool_IdOrderByIdDesc(Long academic, Long school);

    Optional<FeeDate> findByAcademicYear_IdAndSchool_IdAndMonthMaster_Id(Long academic, Long school, Long monthId);
}
