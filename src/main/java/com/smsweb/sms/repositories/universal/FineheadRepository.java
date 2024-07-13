package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Finehead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FineheadRepository extends JpaRepository<Finehead, Long> {
    Finehead findByFineHeadName(String fineHeadName);
}
