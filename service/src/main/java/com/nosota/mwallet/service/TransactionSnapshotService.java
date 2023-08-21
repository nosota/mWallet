package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionSnapshot;
import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionType;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class TransactionSnapshotService {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionSnapshotRepository transactionSnapshotRepository;

    /**
     * Captures daily transaction snapshots for a specified wallet.
     *
     * <p>This method fetches all the CONFIRMED transactions associated with the specified wallet, creates
     * snapshots of these transactions, and then deletes the original transactions. This is intended to
     * maintain a daily snapshot history and reduce the number of transaction records in the primary transaction
     * table.</p>
     *
     * @param walletId The ID of the wallet for which the daily snapshot should be captured.
     */
    @Transactional
    public void captureDailySnapshotForWallet(Integer walletId) {
        // Validate walletId
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID must not be null.");
        }

        // 1. Fetch all the CONFIRMED transactions for the specified wallet
        List<Transaction> confirmedTransactionsForWallet = transactionRepository.findAllByWalletIdAndStatus(walletId, TransactionStatus.CONFIRMED);

        // 2. Extract the reference IDs from these transactions
        Set<UUID> referenceIdsToSnapshot = confirmedTransactionsForWallet.stream()
                .map(Transaction::getReferenceId)
                .collect(Collectors.toSet());

        // 3. Fetch all transactions (HOLD, RESERVE, CONFIRMED) for the specified wallet with the extracted reference IDs
        List<UUID> referenceIdList = new ArrayList<>(referenceIdsToSnapshot);
        List<Transaction> allRelatedTransactionsForWallet = transactionRepository.findAllByWalletIdAndReferenceIdIn(walletId, referenceIdList);

        // 4. Convert these transactions to wallet snapshots
        List<TransactionSnapshot> snapshots = allRelatedTransactionsForWallet.stream()
                .map(transaction -> new TransactionSnapshot(
                        transaction.getWalletId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getStatus(),
                        transaction.getHoldTimestamp(),
                        transaction.getConfirmRejectTimestamp(),
                        transaction.getReferenceId()
                ))
                .collect(Collectors.toList());

        // 5. Save the snapshots
        transactionSnapshotRepository.saveAll(snapshots);

        // 6. Delete the transactions from the transactions table
        transactionRepository.deleteAll(allRelatedTransactionsForWallet);
    }

    /**
     * In the TransactionSnapshotService.archiveOldSnapshots method, the logic was designed to create
     * a ledger entry summarizing the balance from old non-ledger entries (is_ledger_entry = FALSE)
     * and then archive (remove) those old snapshots. But it doesn't touch or archive the ledger
     * entries themselves.
     * <p>
     * Given this, there are a few implications:
     * <p>
     * 1. Over time, ledger entries will accumulate in the transaction_snapshot table. These ledger entries serve as
     * "checkpoints" of the balance at specific times.
     * 2. As older non-ledger entries are archived, these ledger entries become the historical records of the balance
     * in the transaction_snapshot table.
     * 3. If you ever need to review the balance evolution over time, these ledger entries will be your go-to records.
     *
     * @param walletId
     * @param olderThan
     */
    @Transactional
    public void archiveOldSnapshots(Integer walletId, LocalDateTime olderThan) {
        // 1. Calculate the cumulative balance of old snapshots for the given walletId that will be archived
        String cumulativeBalanceSql = """
                    SELECT SUM(amount)
                        FROM transaction_snapshot
                        WHERE wallet_id = :walletId AND snapshot_date < :olderThan AND is_ledger_entry = FALSE AND status = 'CONFIRMED'
                """;

        Query cumulativeBalanceQuery = entityManager.createNativeQuery(cumulativeBalanceSql);
        cumulativeBalanceQuery.setParameter("walletId", walletId);
        cumulativeBalanceQuery.setParameter("olderThan", olderThan);

        BigDecimal cumulativeBalance = (BigDecimal) cumulativeBalanceQuery.getSingleResult();
        if (cumulativeBalance == null) {
            cumulativeBalance = BigDecimal.ZERO;
        }

        // 2. Insert the new ledger entry
        String insertLedgerSql = """
                    INSERT INTO transaction_snapshot(wallet_id, amount, status, type, snapshot_date, is_ledger_entry)
                        VALUES(:walletId, :cumulativeBalance, :status, :type, :olderThan, TRUE)
                """;

        Query insertLedgerQuery = entityManager.createNativeQuery(insertLedgerSql);
        insertLedgerQuery.setParameter("walletId", walletId);
        insertLedgerQuery.setParameter("cumulativeBalance", cumulativeBalance);
        insertLedgerQuery.setParameter("olderThan", olderThan);
        insertLedgerQuery.setParameter("status", TransactionStatus.CONFIRMED.name());
        insertLedgerQuery.setParameter("type", TransactionType.LEDGER.name());

        insertLedgerQuery.executeUpdate();

        // 3. Insert old snapshots into transaction_snapshot_archive table
        String insertIntoArchiveSql = """
            INSERT INTO transaction_snapshot_archive
                SELECT id, wallet_id, type, amount, status, hold_timestamp, confirm_reject_timestamp, snapshot_date, reference_id FROM transaction_snapshot
                    WHERE wallet_id = :walletId AND snapshot_date < :olderThan AND is_ledger_entry = FALSE
        """;

        Query insertIntoArchiveQuery = entityManager.createNativeQuery(insertIntoArchiveSql);
        insertIntoArchiveQuery.setParameter("walletId", walletId);
        insertIntoArchiveQuery.setParameter("olderThan", olderThan);

        insertIntoArchiveQuery.executeUpdate();

        // 4. Delete the old snapshots from transaction_snapshot table
        String deleteOldSnapshotsSql = """
                    DELETE FROM transaction_snapshot
                        WHERE wallet_id = :walletId AND snapshot_date < :olderThan AND is_ledger_entry = FALSE
                """;

        Query deleteOldSnapshotsQuery = entityManager.createNativeQuery(deleteOldSnapshotsSql);
        deleteOldSnapshotsQuery.setParameter("walletId", walletId);
        deleteOldSnapshotsQuery.setParameter("olderThan", olderThan);

        deleteOldSnapshotsQuery.executeUpdate();
    }
}
