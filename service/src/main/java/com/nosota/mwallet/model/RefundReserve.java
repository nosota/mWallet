package com.nosota.mwallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund reserve entity - tracks reserved funds for potential refunds.
 *
 * <p>When a settlement is executed, a percentage of the merchant's net amount
 * is reserved (held) for a configurable period to ensure funds are available
 * for potential refunds.
 *
 * <p><b>Lifecycle:</b>
 * <pre>
 * 1. ACTIVE: Reserve created during settlement, funds are HOLD
 * 2. RELEASED: Reserve expired or manually released, funds transferred to merchant
 * 3. USED: Reserve used for refund (partially or fully)
 * </pre>
 *
 * <p><b>Implementation (жесткий подход):</b>
 * Reserve is implemented as actual HOLD transactions:
 * <ul>
 *   <li>ESCROW → RESERVE_WALLET: debit + credit with status=HOLD</li>
 *   <li>When released: settle the transaction group</li>
 *   <li>When used for refund: cancel original HOLD, create refund transactions</li>
 * </ul>
 */
@Entity
@Table(name = "refund_reserve")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefundReserve {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Settlement ID that created this reserve.
     * Links reserve to the original settlement operation.
     */
    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    /**
     * Merchant ID whose funds are reserved.
     */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /**
     * Merchant wallet ID (for reference).
     */
    @Column(name = "merchant_wallet_id", nullable = false)
    private Integer merchantWalletId;

    /**
     * Reserved amount in cents.
     * Calculated as: settlement.netAmount * reserveRate
     */
    @Column(name = "reserved_amount", nullable = false)
    private Long reservedAmount;

    /**
     * Amount already used for refunds in cents.
     * When usedAmount == reservedAmount, reserve is fully consumed.
     */
    @Column(name = "used_amount", nullable = false)
    private Long usedAmount = 0L;

    /**
     * Remaining available amount for refunds in cents.
     * Calculated as: reservedAmount - usedAmount
     */
    @Column(name = "available_amount", nullable = false)
    private Long availableAmount;

    /**
     * Transaction group ID for the reserve HOLD transactions.
     * Links to the actual ledger entries: ESCROW → RESERVE_WALLET (HOLD)
     */
    @Column(name = "reserve_transaction_group_id", nullable = false)
    private UUID reserveTransactionGroupId;

    /**
     * Current status of the reserve.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundReserveStatus status;

    /**
     * Timestamp when reserve was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when reserve expires and should be released to merchant.
     * After this time, a scheduled job releases the reserve (settles HOLD).
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Timestamp when reserve was released or fully used.
     * Null if status is still ACTIVE.
     */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /**
     * Currency of the reserved amount (ISO 4217 code).
     * Must match the settlement currency.
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
}
