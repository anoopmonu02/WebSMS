package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.SiblingDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiblingDiscountRepository extends JpaRepository<SiblingDiscount, Long> {
    List<SiblingDiscount> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school_id, Long academic_id, String status);
}
