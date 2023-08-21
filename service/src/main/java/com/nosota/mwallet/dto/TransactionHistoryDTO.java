package com.nosota.mwallet.dto;

import java.sql.Timestamp;

public class TransactionHistoryDTO {
    private Integer id; // transaction ID or snapshot ID (just primary key) and they ar not equal to each other even for the same transaction.
    private Integer walletId;
    private String type; // e.g., 'CREDIT', 'DEBIT'
    private Long amount;
    private String status; // e.g., 'HOLD', 'CONFIRMED', 'REJECTED'
    private Timestamp timestamp; // this could be either hold_timestamp or confirm_reject_timestamp

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
        if (o == null || getClass() != o.getClass()) return false;

        TransactionHistoryDTO that = (TransactionHistoryDTO) o;

        if (!walletId.equals(that.walletId)) return false;
        if (!type.equals(that.type)) return false;
        if (!amount.equals(that.amount)) return false;
        if (!status.equals(that.status)) return false;
        return timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = walletId.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}
