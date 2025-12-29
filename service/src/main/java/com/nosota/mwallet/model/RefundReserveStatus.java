package com.nosota.mwallet.model;

/**
 * Status of a refund reserve.
 *
 * <p><b>Lifecycle:</b>
 * <pre>
 * ACTIVE → RELEASED (expired or manually released)
 * ACTIVE → PARTIALLY_USED (some refunds consumed part of reserve)
 * PARTIALLY_USED → FULLY_USED (all reserve consumed by refunds)
 * PARTIALLY_USED → RELEASED (remaining amount released after partial usage)
 * </pre>
 */
public enum RefundReserveStatus {
    /**
     * Reserve is active, funds are held.
     * Available for refunds, not yet expired.
     */
    ACTIVE,

    /**
     * Reserve partially used for refunds.
     * Some amount consumed, some still available.
     */
    PARTIALLY_USED,

    /**
     * Reserve fully consumed by refunds.
     * usedAmount == reservedAmount, no funds left.
     */
    FULLY_USED,

    /**
     * Reserve released to merchant.
     * Either expired or manually released by admin.
     * HOLD transactions settled, funds transferred to merchant.
     */
    RELEASED
}
