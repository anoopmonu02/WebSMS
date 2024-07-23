package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.AcademicStudent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicStudentRepository extends JpaRepository<AcademicStudent, Long> {
}
