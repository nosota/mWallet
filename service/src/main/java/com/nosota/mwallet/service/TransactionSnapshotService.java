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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
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
        List<TransactionSnapshot> snapshots = allRelatedTransactions.stream()
                .map(transaction -> new TransactionSnapshot(
                        transaction.getWalletId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getStatus(),
                        transaction.getHoldTimestamp(),
                        transaction.getConfirmRejectTimestamp()
                ))
                .collect(Collectors.toList());

        // 5. Save the snapshots
        transactionSnapshotRepository.saveAll(snapshots);

        // 6. Delete the transactions from the transactions table
        transactionRepository.deleteAll(allRelatedTransactions);
    }

    /**
     * In the TransactionSnapshotService.archiveOldSnapshots method, the logic was designed to create
     * a ledger entry summarizing the balance from old non-ledger entries (is_ledger_entry = FALSE)
     * and then archive (remove) those old snapshots. But it doesn't touch or archive the ledger
     * entries themselves.
     *
     * Given this, there are a few implications:
     *
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
        insertLedgerQuery.setParameter("status", TransactionStatus.CONFIRMED);
        insertLedgerQuery.setParameter("type", TransactionType.LEDGER);

        insertLedgerQuery.executeUpdate();

        // 3. Delete the old snapshots
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
