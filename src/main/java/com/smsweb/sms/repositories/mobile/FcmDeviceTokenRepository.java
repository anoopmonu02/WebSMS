package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.FcmDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FcmDeviceTokenRepository extends JpaRepository<FcmDeviceToken, Long> {

    // One token can now have several rows (one per family/sibling student) —
    // see FcmDeviceToken's class doc. findByToken (singular) is gone on
    // purpose; use one of these instead depending on what you need.

    Optional<FcmDeviceToken> findByTokenAndAcademicStudent_Id(String token, Long academicStudentId);

    boolean existsByTokenAndAcademicStudent_Id(String token, Long academicStudentId);

    List<FcmDeviceToken> findAllByToken(String token);

    List<FcmDeviceToken> findAllByAcademicStudent_Id(Long academicStudentId);

    void deleteByToken(String token);
}
