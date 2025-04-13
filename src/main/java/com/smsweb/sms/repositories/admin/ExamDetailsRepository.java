package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.admin.Examination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamDetailsRepository extends JpaRepository<ExamDetails, Long> {
    List<ExamDetails> findAllByAcademicYear_IdAndSchool_Id(Long academic, Long school);

    Optional<ExamDetails> findByUuid(UUID uuid);
}
