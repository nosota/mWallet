package com.nosota.mwallet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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

    @Column(name = "hold_timestamp")
    private LocalDateTime holdTimestamp;

    @Column(name = "confirm_reject_timestamp")
    private LocalDateTime confirmRejectTimestamp;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate;

    public TransactionSnapshot() {
    }

    public TransactionSnapshot(Integer walletId, Long amount, TransactionType type,
                               TransactionStatus status, LocalDateTime holdTimestamp,
                               LocalDateTime confirmRejectTimestamp) {
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.holdTimestamp = holdTimestamp;
        this.confirmRejectTimestamp = confirmRejectTimestamp;
        this.snapshotDate = LocalDateTime.now();
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

    public LocalDateTime getHoldTimestamp() {
        return holdTimestamp;
    }

    public void setHoldTimestamp(LocalDateTime holdTimestamp) {
        this.holdTimestamp = holdTimestamp;
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
}
