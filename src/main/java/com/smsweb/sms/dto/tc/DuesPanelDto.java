package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** The dues block of the exit detail page: what is fixed, what is settled, what is left, and the receipts. */
@Getter
@Setter
public class DuesPanelDto {

    private BigDecimal frozenAmount = BigDecimal.ZERO;
    /** Sum of paid plus waived on VALID receipts. */
    private BigDecimal settledAmount = BigDecimal.ZERO;
    private BigDecimal pendingAmount = BigDecimal.ZERO;
    /** Dues are fixed and nothing is pending. */
    private boolean cleared;
    /** true: the exit's session is earlier than the current one, so dues are cleared here (ledger). */
    private boolean ledgerMode;
    /** Money can be recorded now (APPROVED, something pending, ledger mode). */
    private boolean canClear;
    /** Same-session exit with dues pending: they are collected on the Fee Submission screen. */
    private boolean awaitingFeeScreen;
    /** Same-session exit: months counted at strike-off that are not paid yet, comma separated (empty when none). */
    private String outstandingMonths = "";
    /** Fee submissions made for this exit after approval that have no receipt yet (normally 0). */
    private int missingReceipts;
    private List<ReceiptRowDto> receipts;
}
