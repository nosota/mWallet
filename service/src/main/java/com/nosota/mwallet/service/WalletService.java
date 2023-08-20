package com.nosota.mwallet.service;

import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletService {
    private static Logger LOG = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletBalanceService walletBalanceService;

    public Wallet createNewWallet(WalletType type) {
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        return walletRepository.save(newWallet);
    }

    @Transactional
    public Long getAvailableBalance(Integer walletId) {
        Wallet wallet = getWallet(walletId);
        Long availableBalance = walletBalanceService.getAvailableBalance(wallet.getId());
        return availableBalance;
    }

    @Transactional
    public Integer hold(Integer walletId, Long amount, UUID referenceId) throws WalletNotFoundException, InsufficientFundsException {
        Wallet senderWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Ensure the sender has enough balance
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException("Insufficient funds in wallet with ID " + walletId);
        }

        Transaction holdTransaction = new Transaction();
        holdTransaction.setWalletId(senderWallet.getId());
        holdTransaction.setAmount(-amount);
        holdTransaction.setStatus(TransactionStatus.HOLD);
        holdTransaction.setType(TransactionType.DEBIT);
        holdTransaction.setReferenceId(referenceId);
        holdTransaction.setHoldTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(holdTransaction);

        return savedTransaction.getId();
    }


    public Integer reserve(Integer walletId, Long amount, UUID referenceId) throws WalletNotFoundException {
        // Check if the wallet exists and if not, throw WalletNotFoundException
        Wallet senderWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        Transaction transaction = new Transaction();
        transaction.setWalletId(walletId);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.RESERVE);
        transaction.setReferenceId(referenceId);
        transaction.setType(TransactionType.CREDIT);

        transaction = transactionRepository.save(transaction);
        return transaction.getId();
    }

    @Transactional
    public Integer confirm(Integer walletId, UUID referenceId) throws TransactionNotFoundException {
        // Check if a HOLD transaction exists for the given referenceId
        Optional<Transaction> transactionOpt = transactionRepository.findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD, TransactionStatus.RESERVE);
        if (!transactionOpt.isPresent()) {
            throw new TransactionNotFoundException("Hold transaction not found for reference ID " + referenceId + " and wallet ID " + walletId);
        }

        Transaction holdTransaction = transactionOpt.get();

        // Create a new confirmed transaction
        Transaction confirmTransaction = new Transaction();
        confirmTransaction.setWalletId(holdTransaction.getWalletId());
        confirmTransaction.setAmount(holdTransaction.getAmount());
        confirmTransaction.setStatus(TransactionStatus.CONFIRMED);
        confirmTransaction.setType(holdTransaction.getType());
        confirmTransaction.setReferenceId(referenceId);
        confirmTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(confirmTransaction);

        return savedTransaction.getId();
    }

    @Transactional
    public Integer reject(Integer walletId, UUID referenceId) throws TransactionNotFoundException{
        // Check if a HOLD transaction exists for the given referenceId
        Optional<Transaction> transactionOpt = transactionRepository.findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD, TransactionStatus.RESERVE);
        if (!transactionOpt.isPresent()) {
            throw new TransactionNotFoundException("Hold transaction not found for reference ID " + referenceId + " and wallet ID " + walletId);
        }

        Transaction holdTransaction = transactionOpt.get();

        // Create a new rejected transaction
        Transaction rejectTransaction = new Transaction();
        rejectTransaction.setWalletId(holdTransaction.getWalletId());
        rejectTransaction.setAmount(holdTransaction.getAmount());
        rejectTransaction.setStatus(TransactionStatus.REJECTED);
        rejectTransaction.setType(holdTransaction.getType());
        rejectTransaction.setReferenceId(referenceId);
        rejectTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(rejectTransaction);

        return savedTransaction.getId();
    }

    private Wallet getWallet(Integer walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid wallet ID"));
    }
}
