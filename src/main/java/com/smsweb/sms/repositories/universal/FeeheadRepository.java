package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Feehead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeheadRepository extends JpaRepository<Feehead, Long> {

    Feehead findByFeeHeadName(String feeHeadName);
}
