package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
}
