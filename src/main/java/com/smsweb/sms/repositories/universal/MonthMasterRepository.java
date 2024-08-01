package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.MonthMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthMasterRepository extends JpaRepository<MonthMaster, Long> {
    MonthMaster findByMonthName(String monName);
}
