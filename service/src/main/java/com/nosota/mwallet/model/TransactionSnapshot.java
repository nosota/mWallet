package com.nosota.mwallet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_snapshot")
public class TransactionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    public TransactionSnapshot() {
    }

    public TransactionSnapshot(Integer walletId, Long amount, TransactionType type,
                               TransactionStatus status, LocalDateTime holdReserveTimestamp,
                               LocalDateTime confirmRejectTimestamp, UUID referenceId) {
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.holdReserveTimestamp = holdReserveTimestamp;
        this.confirmRejectTimestamp = confirmRejectTimestamp;
        this.snapshotDate = LocalDateTime.now();
        this.referenceId = referenceId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getWalletId() {
        return walletId;
    }

    public void setWalletId(Integer walletId) {
        this.walletId = walletId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getHoldReserveTimestamp() {
        return holdReserveTimestamp;
    }

    public void setHoldReserveTimestamp(LocalDateTime holdTimestamp) {
        this.holdReserveTimestamp = holdTimestamp;
    }

    public LocalDateTime getConfirmRejectTimestamp() {
        return confirmRejectTimestamp;
    }

    public void setConfirmRejectTimestamp(LocalDateTime confirmRejectTimestamp) {
        this.confirmRejectTimestamp = confirmRejectTimestamp;
    }

    public LocalDateTime getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDateTime snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }
}
