package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.FullPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FullpaymentRepository extends JpaRepository<FullPayment, Long> {

    List<FullPayment> findAllBySchool_IdAndAcademicYear_Id(Long school_id, Long academic_id);

}
