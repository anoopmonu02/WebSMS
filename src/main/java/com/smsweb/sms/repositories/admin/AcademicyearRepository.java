package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicyearRepository extends JpaRepository<AcademicYear, Long> {
    AcademicYear findTopByStatusOrderByIdDesc(String status);
    AcademicYear findTopByStatusAndSchool_IdOrderByIdDesc(String status,Long school_id);
    List<AcademicYear> findAllBySchoolIdOrderByIdDesc(Long schoolid);
}
