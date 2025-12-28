package com.nosota.mwallet.api.model;

/**
 * Transaction status in the ledger system.
 * Follows banking ledger standards with two-phase transaction lifecycle.
 */
public enum TransactionStatus {
    /**
     * HOLD: Funds are blocked but not yet transferred.
     * Used for both debit (blocking sender's funds) and credit (preparing recipient's account).
     * This is the initial state for all transactions.
     */
    HOLD,

    /**
     * SETTLED: Final execution of the transaction in favor of the recipient.
     * Funds have been successfully transferred.
     * This is a final state - no further changes possible due to immutability.
     */
    SETTLED,

    /**
     * RELEASED: Blocked funds returned to sender after dispute resolution.
     * Used when conditions were met but transaction is reversed after investigation.
     * Creates opposite direction transactions (e.g., DEBIT becomes CREDIT).
     * This is a final state - no further changes possible due to immutability.
     */
    RELEASED,

    /**
     * CANCELLED: Transaction cancelled before execution conditions were met.
     * Used when transaction is aborted before settlement.
     * Creates opposite direction transactions (e.g., DEBIT becomes CREDIT).
     * This is a final state - no further changes possible due to immutability.
     */
    CANCELLED
}
