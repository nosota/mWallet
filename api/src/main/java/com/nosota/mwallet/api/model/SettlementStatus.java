package com.nosota.mwallet.api.model;

/**
 * Settlement status in the ledger system.
 * Tracks the lifecycle of merchant settlement operations.
 */
public enum SettlementStatus {
    /**
     * PENDING: Settlement has been created but not yet executed.
     * Funds are still being prepared for transfer.
     */
    PENDING,

    /**
     * COMPLETED: Settlement has been successfully executed.
     * Funds have been transferred to merchant and system accounts.
     * This is a final state.
     */
    COMPLETED,

    /**
     * FAILED: Settlement execution failed due to an error.
     * Funds remain in their original location.
     * This is a final state - a new settlement must be created to retry.
     */
    FAILED
}
