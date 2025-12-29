package com.nosota.mwallet.model;

import com.nosota.mwallet.api.model.InitiatorType;
import com.nosota.mwallet.api.model.SettlementStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Settlement entity - represents a merchant payout operation.
 *
 * <p>Settlement is the process of transferring accumulated funds from ESCROW
 * to a MERCHANT wallet, minus platform commission fees.
 *
 * <p>Each settlement groups multiple transaction groups together and creates
 * the necessary ledger entries to complete the transfer.
 *
 * <p>Example:
 * <pre>
 * Settlement for Merchant #123:
 *   - Total amount: 10000 cents (from 3 transaction groups)
 *   - Fee: 300 cents (3%)
 *   - Net amount: 9700 cents (transferred to merchant)
 * </pre>
 */
@Entity
@Table(name = "settlement")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * ID of the merchant receiving the settlement.
     * References the merchant in the external merchant service.
     */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /**
     * Total amount from all transaction groups before fees.
     * Sum of all HOLD CREDIT transactions on ESCROW for this merchant.
     */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /**
     * Platform commission fee deducted from total amount.
     * Calculated as: totalAmount * commissionRate
     */
    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    /**
     * Net amount transferred to merchant after fees.
     * Calculated as: totalAmount - feeAmount
     */
    @Column(name = "net_amount", nullable = false)
    private Long netAmount;

    /**
     * Commission rate applied to this settlement (for audit purposes).
     * Stored as decimal (e.g., 0.03 for 3%).
     * Captured at settlement time in case rate changes in the future.
     */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    /**
     * Number of transaction groups included in this settlement.
     */
    @Column(name = "group_count", nullable = false)
    private Integer groupCount;

    /**
     * Current status of the settlement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    /**
     * Timestamp when the settlement was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the settlement was successfully completed.
     * Null if status is PENDING or FAILED.
     */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /**
     * ID of the transaction group created for the settlement ledger entries.
     * Links this settlement to the actual ledger transactions.
     */
    @Column(name = "settlement_transaction_group_id")
    private UUID settlementTransactionGroupId;

    /**
     * Currency of the settlement (ISO 4217 code: USD, EUR, RUB, etc.).
     * <p>
     * All transaction groups included in this settlement must use the same currency.
     * Settlement operations are performed per-currency basis.
     * </p>
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Idempotency key for preventing duplicate settlements.
     * <p>
     * Format: "merchant_{merchantId}_settlement_{date}"
     * Example: "merchant_123_settlement_2024-03-15"
     * </p>
     * <p>
     * If a settlement with the same idempotency key already exists,
     * return the existing settlement instead of creating a new one.
     * </p>
     */
    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    /**
     * ID of the user who triggered this settlement.
     * <p>
     * For ADMIN-triggered: the admin user ID
     * For SYSTEM-triggered (scheduled): null
     * </p>
     */
    @Column(name = "triggered_by_user_id")
    private Long triggeredByUserId;

    /**
     * Type of entity that triggered this settlement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by_type", length = 10)
    private InitiatorType triggeredByType;

    /**
     * IP address from which the settlement was triggered.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string from the client that triggered the settlement.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
