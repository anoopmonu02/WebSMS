package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.Examination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExaminationRepository extends JpaRepository<Examination, Long> {
    Optional<Examination> findByUuid(UUID uuid);
}
