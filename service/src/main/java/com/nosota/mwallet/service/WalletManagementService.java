package com.nosota.mwallet.service;

import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WalletManagementService {
    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionGroupRepository transactionGroupRepository;

    @Transactional
    public Integer createNewWallet(WalletType type) {
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        return walletRepository.save(newWallet).getId();
    }

    @Transactional
    public Integer createNewWalletWithBalance(WalletType type, Long initialBalance) {
        // Create a new wallet
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        newWallet = walletRepository.save(newWallet);

        // Create an initial transaction for the wallet
        Transaction initialTransaction = new Transaction();
        initialTransaction.setAmount(initialBalance);
        initialTransaction.setWalletId(newWallet.getId());
        initialTransaction.setStatus(TransactionStatus.CONFIRMED);
        initialTransaction.setType(TransactionType.CREDIT); // assuming it's a credit transaction for the initial balance
        initialTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
        initialTransaction.setDescription("New wallet with initial balance");

        // Generate a reference ID for the initial transaction
        TransactionGroup transactionGroup = new TransactionGroup();
        transactionGroup.setStatus(TransactionGroupStatus.CONFIRMED);
        transactionGroup = transactionGroupRepository.save(transactionGroup);
        initialTransaction.setReferenceId(transactionGroup.getId());

        transactionRepository.save(initialTransaction);

        return newWallet.getId();
    }
}
