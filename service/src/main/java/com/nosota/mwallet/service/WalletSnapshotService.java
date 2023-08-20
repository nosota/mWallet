package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.WalletSnapshot;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletSnapshotRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class WalletSnapshotService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletSnapshotRepository walletSnapshotRepository;

    @Transactional
    public void captureDailySnapshot() {
        // 1. Fetch all the CONFIRMED transactions
        List<Transaction> confirmedTransactions = transactionRepository.findAllByStatus(TransactionStatus.CONFIRMED);

        // 2. Extract the reference IDs from these transactions
        Set<UUID> referenceIdsToSnapshot = confirmedTransactions.stream()
                .map(Transaction::getReferenceId)
                .collect(Collectors.toSet());

        // 3. Fetch all transactions (HOLD, RESERVE, CONFIRMED) with the extracted reference IDs
        List<UUID> referenceIdList = new ArrayList<>(referenceIdsToSnapshot);
        List<Transaction> allRelatedTransactions = transactionRepository.findAllByReferenceIdIn(referenceIdList);

        // 4. Convert these transactions to wallet snapshots
        List<WalletSnapshot> snapshots = allRelatedTransactions.stream()
                .map(transaction -> new WalletSnapshot(
                        transaction.getWalletId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getStatus(),
                        transaction.getHoldTimestamp(),
                        transaction.getConfirmRejectTimestamp()
                ))
                .collect(Collectors.toList());

        // 5. Save the snapshots
        walletSnapshotRepository.saveAll(snapshots);

        // 6. Delete the transactions from the transactions table
        transactionRepository.deleteAll(allRelatedTransactions);
    }
}
