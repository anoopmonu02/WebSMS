package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.StudentHealthInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * NEW, isolated repository for the new student_health_info table. Does not
 * touch AcademicStudentRepository or any existing repository.
 */
public interface StudentHealthInfoRepository extends JpaRepository<StudentHealthInfo, Long> {

    Optional<StudentHealthInfo> findByAcademicStudent_Id(Long academicStudentId);

    boolean existsByAcademicStudent_Id(Long academicStudentId);
}
