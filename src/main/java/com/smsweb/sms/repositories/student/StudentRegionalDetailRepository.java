package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.StudentRegionalDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRegionalDetailRepository extends JpaRepository<StudentRegionalDetail, Long> {

    Optional<StudentRegionalDetail> findByStudent_Id(Long studentId);

    Optional<StudentRegionalDetail> findByStudent_Uuid(UUID studentUuid);

    List<StudentRegionalDetail> findAllByStudent_IdIn(List<Long> studentIds);
}
