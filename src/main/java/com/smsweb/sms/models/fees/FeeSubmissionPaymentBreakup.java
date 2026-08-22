package com.smsweb.sms.models.fees;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Per-mode amount breakup for a single FeeSubmission. A parent can pay part in
 * Cash and part Online in the same submission ("Both") - this table records
 * exactly how much went through each mode, so collection reports/ledgers can
 * reconcile against the real split instead of guessing from a single
 * Cash/Online/Both label on FeeSubmission.paymentType.
 *
 * Cardinality by design: exactly 1 row for a Cash-only or Online-only
 * submission (the full paidAmount, mode matching FeeSubmission.paymentType),
 * exactly 2 rows for "Both" (one Cash, one Online, summing to paidAmount).
 * Never 0 - every submission gets at least one row going forward. Existing
 * historical FeeSubmission rows saved before this table existed simply have
 * no matching rows here; readers fall back to FeeSubmission.paymentType for
 * those (see FeeSubmissionService#getFeeReceiptData).
 *
 * No independent status field - a breakup row's lifecycle is entirely tied to
 * its parent FeeSubmission (cascade ALL, same as FeeSubmissionSub /
 * FeeSubmissionMonths); if the parent submission is ever cancelled, the
 * parent's own status="Inactive" is still the single source of truth readers
 * already check, exactly as today.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class FeeSubmissionPaymentBreakup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fee_submission_id", nullable = false)
    @JsonBackReference
    private FeeSubmission feeSubmission;

    /**
     * "Cash" or "Online" - deliberately the same plain-String convention as
     * FeeSubmission.paymentType (not an enum) for consistency with the rest of
     * this module.
     */
    @NotNull(message = "Payment mode should be available")
    @Column(name = "payment_mode", length = 20, nullable = false)
    private String paymentMode;

    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    @Column(name = "updation_date")
    private Date updationDate;
}
