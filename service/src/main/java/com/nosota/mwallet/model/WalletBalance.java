package com.nosota.mwallet.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Entity
public class WalletBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    private Double balance;

    @Column(name = "snapshot_date")
    private LocalDateTime snapshotDate;

    // Setter for Wallet
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    // Setter for Balance
    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Double getBalance() {
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
