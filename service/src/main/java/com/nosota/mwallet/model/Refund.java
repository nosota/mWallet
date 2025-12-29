package com.nosota.mwallet.model;

import com.nosota.mwallet.api.model.InitiatorType;
import com.nosota.mwallet.api.model.RefundInitiator;
import com.nosota.mwallet.api.model.RefundStatus;
import com.nosota.mwallet.api.model.RefundType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund entity - represents a return of funds to buyer after settlement.
 *
 * <p>Refund is different from RELEASE/CANCEL operations:
 * <ul>
 *   <li>RELEASE/CANCEL: Return funds BEFORE settlement (from ESCROW)</li>
 *   <li>REFUND: Return funds AFTER settlement (from MERCHANT wallet)</li>
 * </ul>
 *
 * <p>Refund workflow:
 * <pre>
 * 1. Order completed → Settlement → Merchant received funds
 * 2. Customer requests return → Refund created (PENDING/PENDING_FUNDS)
 * 3. System validates → Refund approved (PROCESSING)
 * 4. Transactions created: MERCHANT → BUYER
 * 5. Refund completed (COMPLETED)
 * </pre>
 *
 * <p>Key features:
 * <ul>
 *   <li>Full or partial refunds supported</li>
 *   <li>Multiple refunds per order allowed (sum ≤ original net amount)</li>
 *   <li>Platform commission typically NOT refunded</li>
 *   <li>Strict merchant balance check (no negative balance by default)</li>
 * </ul>
 */
@Entity
@Table(name = "refund")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Transaction group ID (order ID) being refunded.
     * Links this refund to the original order.
     */
    @Column(name = "transaction_group_id", nullable = false)
    private UUID transactionGroupId;

    /**
     * Settlement ID that paid out this order.
     * Used for audit trail and statistics.
     * Can be null if order was never settled (edge case).
     */
    @Column(name = "settlement_id")
    private UUID settlementId;

    /**
     * ID of the merchant returning funds.
     * Merchant wallet will be debited.
     */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /**
     * Wallet ID of the merchant.
     */
    @Column(name = "merchant_wallet_id", nullable = false)
    private Integer merchantWalletId;

    /**
     * ID of the buyer receiving refund.
     */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /**
     * Wallet ID of the buyer.
     */
    @Column(name = "buyer_wallet_id", nullable = false)
    private Integer buyerWalletId;

    /**
     * Refund amount (in cents).
     * Can be full amount or partial.
     * Must be ≤ original net amount (what merchant received).
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * Original order amount (before commission).
     * Stored for reference and validation.
     */
    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    /**
     * Reason for refund (provided by initiator).
     * Examples: "defective product", "wrong size", "customer cancellation"
     */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /**
     * Current status of the refund.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    /**
     * Who initiated this refund.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "initiator", nullable = false)
    private RefundInitiator initiator;

    /**
     * Transaction group ID created for refund ledger entries.
     * Links to the transactions: MERCHANT → BUYER.
     * Null if refund not yet executed.
     */
    @Column(name = "refund_transaction_group_id")
    private UUID refundTransactionGroupId;

    /**
     * Timestamp when refund was created/requested.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when refund was successfully processed.
     * Null if status is not COMPLETED.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Timestamp when refund was last updated.
     * Used for tracking status changes.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Expiration timestamp for PENDING_FUNDS status.
     * If refund stays in PENDING_FUNDS past this time, it becomes EXPIRED.
     * Null if not applicable.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Additional notes or error messages.
     * Used for failure reasons, admin comments, etc.
     */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Currency of the refund (ISO 4217 code: USD, EUR, RUB, etc.).
     * <p>
     * Must match the original transaction currency.
     * Cross-currency refunds are forbidden.
     * </p>
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Type of refund: FULL or PARTIAL.
     * <p>
     * FULL: Entire order amount is refunded (only one FULL refund allowed per order)
     * PARTIAL: Portion of order amount is refunded (multiple PARTIAL refunds allowed)
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", nullable = false, length = 10)
    private RefundType refundType;

    /**
     * Idempotency key for preventing duplicate refunds.
     * <p>
     * Optional. If provided, prevents duplicate refund execution for same order + type combination.
     * </p>
     * <p>
     * Composite unique constraint: (transaction_group_id, refund_type, idempotency_key)
     * This ensures:
     * - Only one FULL refund per order
     * - Partial refunds with same idempotency key are deduplicated
     * </p>
     */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    /**
     * ID of the user who initiated this refund.
     * <p>
     * For MERCHANT-initiated: the merchant user ID
     * For ADMIN-initiated: the admin user ID
     * For SYSTEM-initiated: null
     * </p>
     */
    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    /**
     * Type of entity that initiated this refund.
     * <p>
     * Note: This is separate from the 'initiator' field (RefundInitiator enum)
     * which provides business-level context, while this provides technical audit info.
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_type", length = 10)
    private InitiatorType initiatorType;

    /**
     * IP address from which the refund was initiated.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string from the client that initiated the refund.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
