package com.nosota.mwallet.repository;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Integer> {
    WalletBalance findTopByWalletIdOrderBySnapshotDateDesc(Integer walletId);

    @Transactional
    Double findBalanceByWallet(Wallet wallet);

    default void setWallet(WalletBalance walletBalance, Wallet wallet) {
        walletBalance.setWallet(wallet);
        save(walletBalance);
    }

    default void setBalance(WalletBalance walletBalance, Long balance) {
        walletBalance.setBalance(balance);
        save(walletBalance);
    }

    default void setSnapshotDate(WalletBalance walletBalance, LocalDateTime date) {
        walletBalance.setSnapshotDate(date);
        save(walletBalance);
    }

    @Query("SELECT wb.balance FROM WalletBalance wb WHERE wb.id = ?1")
    Double getBalance(Integer walletBalanceId);
}
