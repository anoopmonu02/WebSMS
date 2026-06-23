package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Medium;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MediumRepository extends JpaRepository<Medium, Long> {
    Optional<Medium> findByMediumNameIgnoreCase(String mediumName);
}
