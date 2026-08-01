package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.ExamResultCorrectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamResultCorrectionLogRepository extends JpaRepository<ExamResultCorrectionLog, Long> {
}
