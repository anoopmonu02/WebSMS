package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.DueClearanceReceipt;
import com.smsweb.sms.models.tc.enums.ReceiptMode;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DueClearanceReceiptRepository
        extends JpaRepository<DueClearanceReceipt, Long>, JpaSpecificationExecutor<DueClearanceReceipt> {

    Optional<DueClearanceReceipt> findByUuidAndSchool_Id(UUID uuid, Long schoolId);

    /** One receipt per fee submission (idempotency check). */
    Optional<DueClearanceReceipt> findByFeeSubmission_Id(Long feeSubmissionId);

    Optional<DueClearanceReceipt> findBySubmissionToken(String submissionToken);

    List<DueClearanceReceipt> findByExit_IdOrderByIdAsc(Long exitId);

    boolean existsBySchool_Id(Long schoolId);

    /** Highest receipt sequence of the school, or null when none exists. Run under the school row lock. */
    @Query("SELECT MAX(r.receiptSeq) FROM DueClearanceReceipt r WHERE r.school.id = :schoolId")
    Long findMaxReceiptSeqBySchoolId(@Param("schoolId") Long schoolId);

    /** Sum of paid plus waived amounts of the exit's receipts with the given record status. Null when there are none. */
    @Query("SELECT SUM(r.amountPaid + r.discountAmount) FROM DueClearanceReceipt r "
            + "WHERE r.exit.id = :exitId AND r.recordStatus = :status")
    BigDecimal sumSettledByExit(@Param("exitId") Long exitId, @Param("status") RecordStatus status);

    /** Same as {@link #sumSettledByExit} but only receipts made in the given mode. */
    @Query("SELECT SUM(r.amountPaid + r.discountAmount) FROM DueClearanceReceipt r "
            + "WHERE r.exit.id = :exitId AND r.recordStatus = :status AND r.mode = :mode")
    BigDecimal sumSettledByExitAndMode(@Param("exitId") Long exitId, @Param("status") RecordStatus status,
                                       @Param("mode") ReceiptMode mode);
}
