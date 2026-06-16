package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicyearRepository extends JpaRepository<AcademicYear, Long> {
    AcademicYear findTopByStatusOrderByIdDesc(String status);
    AcademicYear findTopByStatusAndSchool_IdOrderByIdDesc(String status, Long school_id);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM AcademicYear a WHERE LOWER(a.status) = 'active' AND a.school.id = :schoolId ORDER BY a.id DESC")
    AcademicYear findActiveBySchoolId(@org.springframework.data.repository.query.Param("schoolId") Long schoolId);
    List<AcademicYear> findAllBySchoolIdOrderByIdDesc(Long schoolid);
    List<AcademicYear> findAll();
}
