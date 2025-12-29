package com.nosota.mwallet.api.model;

/**
 * Type of entity that initiated an operation.
 *
 * <p>Used for audit trail to track who triggered transactions, settlements, refunds, etc.
 */
public enum InitiatorType {
    /**
     * Operation initiated by a regular user (buyer).
     */
    USER,

    /**
     * Operation initiated by a merchant.
     */
    MERCHANT,

    /**
     * Operation initiated by a platform administrator.
     */
    ADMIN,

    /**
     * Operation initiated automatically by the system (e.g., scheduled jobs, webhooks).
     */
    SYSTEM
}
