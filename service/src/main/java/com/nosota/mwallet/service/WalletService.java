package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletBalanceRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WalletService {
    private static Logger LOG = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Transactional
    public Long getAvailableBalance(Integer walletId) {
        Wallet wallet = getWallet(walletId);
        Long availableBalance = walletBalanceRepository.getAvailableBalance(wallet);
        return availableBalance;
    }


    @Transactional
    public Integer hold(Integer walletId, Long amount) {
        Wallet wallet = getWallet(walletId);
        Long availableBalance = walletBalanceRepository.getAvailableBalance(wallet);
        if (availableBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        Transaction transaction = new Transaction();
        transaction.setWalletId(wallet.getId());
        transaction.setAmount(-amount); // still a debit, but in HOLD status
        transaction.setHoldTimestamp(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.HOLD);

        return transactionRepository.save(transaction).getId();  // Return the transaction ID for later reference
    }

    @Transactional
    public void confirm(Integer transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid transaction ID"));

        if (transaction.getStatus() != TransactionStatus.HOLD) {
            throw new IllegalStateException("Transaction not in HOLD status");
        }

        transaction.setConfirmRejectTimestamp(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void reject(Integer transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid transaction ID"));

        if (transaction.getStatus() != TransactionStatus.HOLD) {
            throw new IllegalStateException("Transaction not in HOLD status");
        }

        transaction.setStatus(TransactionStatus.REJECTED);
        transactionRepository.save(transaction);

        // Reverse the amount to the wallet
        Transaction reversalTransaction = new Transaction();
        reversalTransaction.setWalletId(transaction.getWalletId());
        reversalTransaction.setAmount(-transaction.getAmount()); // Reverse the transaction amount
        transaction.setConfirmRejectTimestamp(LocalDateTime.now());
        reversalTransaction.setStatus(TransactionStatus.CONFIRMED); // This is a confirmed transaction

        transactionRepository.save(reversalTransaction);
    }

    private Wallet getWallet(Integer walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid wallet ID"));
    }
}
