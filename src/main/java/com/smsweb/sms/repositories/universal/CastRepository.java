package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Cast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CastRepository extends JpaRepository<Cast, Long> {
    Optional<Cast> findByCastNameIgnoreCase(String castName);
}
