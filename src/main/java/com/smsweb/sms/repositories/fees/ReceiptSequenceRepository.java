package com.smsweb.sms.repositories.fees;

import com.smsweb.sms.models.fees.ReceiptSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, Long> {
    Optional<ReceiptSequence> findByBranchCodeAndYear(String branchCode, Integer year);
}
