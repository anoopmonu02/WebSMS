package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.FcmDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FcmDeviceTokenRepository extends JpaRepository<FcmDeviceToken, Long> {

    Optional<FcmDeviceToken> findByToken(String token);

    List<FcmDeviceToken> findAllByAcademicStudent_Id(Long academicStudentId);

    void deleteByToken(String token);
}
