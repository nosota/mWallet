package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletBalanceRepository;
import com.nosota.mwallet.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    // ... credit and debit methods ...

    public Double getCurrentBalance(Long walletId) {
        WalletBalance latestSnapshot = walletBalanceRepository.findTopByWalletIdOrderBySnapshotDateDesc(walletId);

        Double recentTransactionsSum;
        if (latestSnapshot != null) {
            recentTransactionsSum = transactionRepository.sumByWalletIdAndTimestampAfter(walletId, latestSnapshot.getSnapshotDate());
            return latestSnapshot.getBalance() + (recentTransactionsSum != null ? recentTransactionsSum : 0);
        } else {
            return transactionRepository.sumByWalletId(walletId);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")  // This runs the method at midnight every day.
    public void captureDailySnapshot() {
        List<Wallet> allWallets = walletRepository.findAll();
        for (Wallet wallet : allWallets) {
            Double currentBalance = getCurrentBalance(wallet.getId());
            WalletBalance snapshot = new WalletBalance();
            snapshot.setWallet(wallet);
            snapshot.setBalance(currentBalance);
            snapshot.setSnapshotDate(LocalDateTime.now());
            walletBalanceRepository.save(snapshot);
        }
    }
}
