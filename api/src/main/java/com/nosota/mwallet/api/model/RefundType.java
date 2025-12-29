package com.nosota.mwallet.api.model;

/**
 * Type of refund operation.
 *
 * <p>Distinguishes between full and partial refunds:
 * <ul>
 *   <li>FULL: Complete refund of the entire order amount</li>
 *   <li>PARTIAL: Refund of a portion of the order amount</li>
 * </ul>
 *
 * <p>Used for:
 * <ul>
 *   <li>Business logic validation</li>
 *   <li>Idempotency: prevents duplicate full refunds for same order</li>
 *   <li>Reporting and analytics</li>
 * </ul>
 */
public enum RefundType {
    /**
     * Full refund - entire order amount is refunded.
     * <p>
     * Only one FULL refund is allowed per order (enforced by unique constraint).
     * </p>
     */
    FULL,

    /**
     * Partial refund - portion of order amount is refunded.
     * <p>
     * Multiple PARTIAL refunds are allowed for same order (sum ≤ net amount).
     * </p>
     */
    PARTIAL
}
