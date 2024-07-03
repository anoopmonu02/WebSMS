package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, Long> {
}
