package com.nosota.mwallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_snapshot")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionSnapshot {

    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "wallet_id")
    private Integer walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;  // Enum of DEBIT or CREDIT

    @Column(precision = 20, scale = 2, nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;  // Enum of HOLD, CONFIRMED, REJECTED

    @Column(name = "hold_reserve_timestamp")
    private LocalDateTime holdReserveTimestamp;

    @Column(name = "confirm_reject_timestamp")
    private LocalDateTime confirmRejectTimestamp;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "description")
    private String description;
}
