package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.StudentHealthInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * NEW, isolated repository for the new student_health_info table. Does not
 * touch AcademicStudentRepository or any existing repository.
 */
public interface StudentHealthInfoRepository extends JpaRepository<StudentHealthInfo, Long> {

    Optional<StudentHealthInfo> findByAcademicStudent_Id(Long academicStudentId);

    boolean existsByAcademicStudent_Id(Long academicStudentId);

    /**
     * Batch fetch for the grade-wise Student Health Report — one query for the
     * whole class instead of N single-row lookups (findByAcademicStudent_Id)
     * per student, which would otherwise be an N+1 query per report load.
     */
    List<StudentHealthInfo> findAllByAcademicStudent_IdIn(Collection<Long> academicStudentIds);
}
