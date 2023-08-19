package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletBalanceRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    // ... credit and debit methods ...

    public Long getCurrentBalance(Integer walletId) {
        WalletBalance latestSnapshot = walletBalanceRepository.findTopByWalletIdOrderBySnapshotDateDesc(walletId);

        Long recentTransactionsSum;
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
            Long currentBalance = getCurrentBalance(wallet.getId());
            WalletBalance snapshot = new WalletBalance();
            snapshot.setWallet(wallet);
            snapshot.setBalance(currentBalance);
            snapshot.setSnapshotDate(LocalDateTime.now());
            walletBalanceRepository.save(snapshot);
        }
    }

    @Transactional
    public void credit(Integer walletId, Long amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid wallet ID"));

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);  // positive amount for credit
        transaction.setTransactionDate(LocalDateTime.now());

        transactionRepository.save(transaction);
    }

    @Transactional
    public void debit(Integer walletId, Long amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid wallet ID"));

        // Make sure the wallet has enough balance before debiting
        Double currentBalance = walletBalanceRepository.getBalance(wallet.getId());
        if (currentBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(-amount);  // negative amount for debit
        transaction.setTransactionDate(LocalDateTime.now());

        transactionRepository.save(transaction);
    }

    @Transactional
    public void transferBetweenMultipleWallets(Map<Integer, Long> walletAmountMap) {
        for (Map.Entry<Integer, Long> entry : walletAmountMap.entrySet()) {
            Integer walletId = entry.getKey();
            Long amount = entry.getValue();

            if (amount > 0) {
                credit(walletId, amount);
            } else if (amount < 0) {
                debit(walletId, Math.abs(amount));
            }

            // You can insert your daily threshold check logic here.
            // If any of the checks fails, you can throw a runtime exception,
            // and Spring's @Transactional will ensure all operations are rolled back.
        }
    }
}
