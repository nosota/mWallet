package com.nosota.mwallet.api.model;

/**
 * Transaction group status.
 * Represents the overall state of a transaction group.
 */
public enum TransactionGroupStatus {
    /**
     * IN_PROGRESS: Transaction group is still being built.
     * Individual transactions can still be added.
     */
    IN_PROGRESS,

    /**
     * SETTLED: Transaction group has been successfully finalized.
     * All transactions in the group have been settled.
     */
    SETTLED,

    /**
     * RELEASED: Transaction group has been released after dispute.
     * All funds returned to original senders.
     */
    RELEASED,

    /**
     * CANCELLED: Transaction group has been cancelled.
     * All holds have been reversed.
     */
    CANCELLED
}
