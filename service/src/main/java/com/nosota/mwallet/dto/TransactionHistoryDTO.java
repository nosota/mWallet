package com.nosota.mwallet.dto;

import java.sql.Timestamp;

public class TransactionHistoryDTO {
    private Integer id; // transaction ID or snapshot ID
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
}
