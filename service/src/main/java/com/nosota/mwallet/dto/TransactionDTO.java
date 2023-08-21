package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionType;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionDTO {
    private Integer id;
    private UUID referenceId;
    private Integer walletId;
    private Long amount;
    private TransactionStatus status;
    private TransactionType type;
    private LocalDateTime holdTimestamp;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
