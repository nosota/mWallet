package com.nosota.mwallet.model;

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

}
