package com.nosota.mwallet.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class WalletBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    private Long balance;

    @Column(name = "snapshot_date")
    private LocalDateTime snapshotDate;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    // Setter for Wallet
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    // Setter for Balance
    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Long getBalance() {
        return this.balance;
    }

    public LocalDateTime getSnapshotDate() {
        return snapshotDate;
    }

    // Setter for SnapshotDate
    public void setSnapshotDate(LocalDateTime snapshotDate) {
        this.snapshotDate = snapshotDate;
    }
}
