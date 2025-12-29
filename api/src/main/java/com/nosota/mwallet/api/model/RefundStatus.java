package com.nosota.mwallet.api.model;

/**
 * Refund status in the ledger system.
 * Tracks the lifecycle of refund operations (returns after settlement).
 */
public enum RefundStatus {
    /**
     * PENDING: Refund has been requested but not yet approved.
     * Used in two-level approval flow (merchant requests, system approves).
     * Waiting for system validation and approval.
     */
    PENDING,

    /**
     * PENDING_FUNDS: Refund is approved but merchant has insufficient balance.
     * Waiting for merchant account to be funded.
     * Can be auto-executed when balance becomes sufficient.
     */
    PENDING_FUNDS,

    /**
     * PROCESSING: Refund is currently being executed.
     * Transactions are being created (MERCHANT → BUYER).
     * Temporary state during transaction processing.
     */
    PROCESSING,

    /**
     * COMPLETED: Refund has been successfully executed.
     * Funds have been transferred from merchant to buyer.
     * This is a final state.
     */
    COMPLETED,

    /**
     * REJECTED: Refund request was rejected.
     * Could be rejected due to policy violation, invalid amount, etc.
     * This is a final state.
     */
    REJECTED,

    /**
     * FAILED: Refund execution failed due to technical error.
     * Transaction creation or settlement failed.
     * Can be retried manually.
     * This is a final state (requires manual intervention).
     */
    FAILED,

    /**
     * EXPIRED: Refund waiting period has expired.
     * Used when PENDING_FUNDS status lasted too long without merchant funding.
     * Requires manual escalation to support.
     * This is a final state.
     */
    EXPIRED
}
