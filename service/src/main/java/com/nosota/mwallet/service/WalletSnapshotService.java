package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.WalletSnapshot;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletSnapshotRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class WalletSnapshotService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletSnapshotRepository walletSnapshotRepository;

    @Transactional
    public void captureDailySnapshot() {
        // Fetch all transactions from the transactions table
        List<Transaction> transactions = transactionRepository.findAll();

        // Convert transactions into WalletSnapshot entities and set the snapshot date
        List<WalletSnapshot> snapshots = transactions.stream()
                .map(this::transactionToSnapshot)
                .collect(Collectors.toList());

        // Save the converted snapshots to the wallet_snapshots table
        walletSnapshotRepository.saveAll(snapshots);
    }

    private WalletSnapshot transactionToSnapshot(Transaction transaction) {
        WalletSnapshot snapshot = new WalletSnapshot();
        snapshot.setWalletId(transaction.getWalletId()); // Changed this line
        snapshot.setType(transaction.getType());
        snapshot.setAmount(transaction.getAmount());
        snapshot.setStatus(transaction.getStatus());
        snapshot.setHoldTimestamp(transaction.getHoldTimestamp());
        snapshot.setConfirmRejectTimestamp(transaction.getConfirmRejectTimestamp());
        snapshot.setSnapshotDate(LocalDateTime.now());

        return snapshot;
    }
}
