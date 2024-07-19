package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Fine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {
    List<Fine> findAllByAcademicYear_IdAndSchool_Id(Long academic_id, Long school_id);

}
