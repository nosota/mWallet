package com.nosota.mwallet.model;

/**
 * Transaction group status in the ledger system.
 * Represents the overall state of a group of related transactions (typically debit + credit).
 * Follows banking ledger standards with two-phase commit pattern.
 */
public enum TransactionGroupStatus {
    /**
     * IN_PROGRESS: Transaction group is active, transactions are in HOLD state.
     * Funds are blocked but not yet finalized.
     * This is the initial state for all transaction groups.
     */
    IN_PROGRESS,

    /**
     * SETTLED: Transaction group completed successfully.
     * All transactions in the group have been finalized with SETTLED status.
     * Funds have been successfully transferred.
     * This is a final state - no further changes possible due to immutability.
     */
    SETTLED,

    /**
     * RELEASED: Transaction group reversed after dispute resolution.
     * All transactions in the group have been reversed with RELEASED status.
     * Funds have been returned to original accounts.
     * This is a final state - no further changes possible due to immutability.
     */
    RELEASED,

    /**
     * CANCELLED: Transaction group cancelled before completion.
     * All transactions in the group have been cancelled with CANCELLED status.
     * Funds have been released back to original accounts.
     * This is a final state - no further changes possible due to immutability.
     */
    CANCELLED
}
