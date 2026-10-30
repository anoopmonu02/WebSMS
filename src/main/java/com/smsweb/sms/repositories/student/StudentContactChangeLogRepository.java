package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.StudentContactChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentContactChangeLogRepository extends JpaRepository<StudentContactChangeLog, Long> {
}
