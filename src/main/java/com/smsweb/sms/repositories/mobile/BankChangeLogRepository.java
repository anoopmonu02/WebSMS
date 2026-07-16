package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.mobile.BankChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * NEW, isolated repository for the append-only bank_change_log audit table.
 */
public interface BankChangeLogRepository extends JpaRepository<BankChangeLog, Long> {
}
