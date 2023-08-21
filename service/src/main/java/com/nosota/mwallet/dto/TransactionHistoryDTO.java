package com.nosota.mwallet.dto;

import java.sql.Timestamp;
import java.util.UUID;

public class TransactionHistoryDTO {
    private UUID referenceId;
    private Integer walletId;
    private String type; // e.g., 'CREDIT', 'DEBIT'
    private Long amount;
    private String status; // e.g., 'HOLD', 'CONFIRMED', 'REJECTED'
    private Timestamp timestamp; // this could be either hold_reserve_timestamp or confirm_reject_timestamp

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionHistoryDTO that)) return false;

        if (!referenceId.equals(that.referenceId)) return false;
        if (!walletId.equals(that.walletId)) return false;
        if (!type.equals(that.type)) return false;
        if (!amount.equals(that.amount)) return false;
        if (!status.equals(that.status)) return false;
        return timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = referenceId.hashCode();
        result = 31 * result + walletId.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}
