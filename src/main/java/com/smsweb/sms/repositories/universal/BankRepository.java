package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    Optional<Bank> findByBankNameIgnoreCase(String bankName);
}
