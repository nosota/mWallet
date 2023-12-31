package com.nosota.mwallet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public Integer getWalletId() {
        return walletId;
    }

    public void setWalletId(Integer walletId) {
        this.walletId = walletId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
