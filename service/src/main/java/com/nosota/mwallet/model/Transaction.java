package com.nosota.mwallet.model;

import com.nosota.mwallet.api.model.InitiatorType;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction entity - represents an IMMUTABLE ledger entry.
 *
 * <p>Following banking ledger standards:
 * <ul>
 *   <li>Records are append-only (no updates or deletes)</li>
 *   <li>Each entry is permanent and auditable</li>
 *   <li>Corrections are made via offsetting entries (reversal)</li>
 * </ul>
 *
 * <p>WARNING: Currently uses @Setter for convenience during construction.
 * In production ledger system, should use Builder pattern or immutable constructor
 * to prevent modifications after persistence.
 *
 * <p>TODO: Replace @Setter with @Builder and ensure no setters called after save()
 */
@Entity
@Getter
@Setter  // TODO: Remove and use @Builder for true immutability
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "wallet_id")
    private Integer walletId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    private TransactionType type; // CREDIT or DEBIT

    @Column(name = "hold_reserve_timestamp")
    private LocalDateTime holdReserveTimestamp;

    @Column(name = "confirm_reject_timestamp")
    private LocalDateTime confirmRejectTimestamp;

    private String description;

    /**
     * Currency of the transaction (ISO 4217 code: USD, EUR, RUB, etc.).
     * <p>
     * Must match the wallet currency. Inherited from wallet at creation time.
     * Cross-currency transactions are forbidden.
     * </p>
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * ID of the user who initiated this transaction.
     * <p>
     * For USER-initiated transactions: the user ID
     * For MERCHANT-initiated: the merchant user ID
     * For ADMIN-initiated: the admin user ID
     * For SYSTEM-initiated: null
     * </p>
     */
    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    /**
     * Type of entity that initiated this transaction.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_type", length = 10)
    private InitiatorType initiatorType;

    /**
     * IP address from which the transaction was initiated.
     * <p>
     * Used for fraud detection and audit trail.
     * </p>
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string from the client that initiated the transaction.
     * <p>
     * Used for audit trail and debugging.
     * </p>
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

}
