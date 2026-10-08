package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.tc.enums.ReceiptMode;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Receipt for exit dues. One row per payment event. SAME_SESSION rows are created after the
 * Fee Submission save and link to the existing fee_submission row (read-only link); LEDGER rows
 * are the payment record themselves. Plan section 5.4.
 *
 * <p>No update columns: a mistake is corrected by voiding (LEDGER only) and adding a new row.
 * {@code receiptNo} is frozen at creation, so a later prefix or padding change never alters it.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "due_clearance_receipt",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dcr_uuid", columnNames = {"uuid"}),
                @UniqueConstraint(name = "uk_dcr_school_seq", columnNames = {"school_id", "receipt_seq"}),
                @UniqueConstraint(name = "uk_dcr_fee_submission", columnNames = {"fee_submission_id"}),
                @UniqueConstraint(name = "uk_dcr_submission_token", columnNames = {"submission_token"})
        },
        indexes = {
                @Index(name = "idx_dcr_exit", columnList = "exit_id")
        })
public class DueClearanceReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exit_id", nullable = false, updatable = false)
    private StudentExit exit;

    /** Numeric sequence per school (plan section 6.10). */
    @Column(name = "receipt_seq", nullable = false, updatable = false)
    private Long receiptSeq;

    /** Formatted number, for example DCR-00001. */
    @Column(name = "receipt_no", nullable = false, updatable = false, length = 40)
    private String receiptNo;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, updatable = false, length = 15)
    private ReceiptMode mode;

    /** SAME_SESSION only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_submission_id", updatable = false)
    private FeeSubmission feeSubmission;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** Admin waiver. */
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "payment_mode", length = 30)
    private String paymentMode;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    /** JSON string of the months (and heads) printed. */
    @Column(name = "breakdown", columnDefinition = "TEXT")
    private String breakdown;

    @Column(name = "remark", length = 500)
    private String remark;

    /** Double-submit guard. */
    @Column(name = "submission_token", length = 64)
    private String submissionToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "record_status", nullable = false, length = 10)
    private RecordStatus recordStatus = RecordStatus.VALID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private UserEntity voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
