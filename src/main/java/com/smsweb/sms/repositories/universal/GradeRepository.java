package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findByGradeNameIgnoreCase(String gradeName);
}
