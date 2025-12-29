package com.nosota.mwallet.api.model;

/**
 * Refund initiator type.
 * Identifies who requested the refund operation.
 */
public enum RefundInitiator {
    /**
     * SYSTEM: Refund initiated by internal system logic.
     * Automated refunds or system-level decisions.
     */
    SYSTEM,

    /**
     * MERCHANT: Refund initiated by merchant through API.
     * Merchant responding to customer request.
     * Typically requires system approval.
     */
    MERCHANT,

    /**
     * ADMIN: Refund initiated by platform admin/support.
     * Manual intervention for dispute resolution.
     * Bypasses normal approval flow.
     */
    ADMIN
}
