package com.nosota.mwallet.service;

import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Validated
@AllArgsConstructor
public class TransactionSnapshotService {
    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionRepository transactionRepository;

    private final TransactionSnapshotRepository transactionSnapshotRepository;

    private final TransactionGroupRepository transactionGroupRepository;

    /**
     * Captures a daily snapshot for a specified wallet, transferring relevant transactions to the snapshot storage.
     *
     * <p>
     * This method aims to reduce the load on the active transaction storage by periodically moving processed transactions
     * to a dedicated snapshot storage.
     * </p>
     *
     * <p>
     * Implementation Details:
     * 1. The method first fetches all the CONFIRMED transactions for the given wallet ID.
     * 2. Extracts the reference IDs from these transactions to identify the associated transaction groups.
     * 3. Only transactions that belong to CONFIRMED or REJECTED transaction groups are considered for snapshot capture.
     * Transactions belonging to IN_PROGRESS transaction groups are ignored, ensuring that ongoing transactions
     * are not prematurely archived.
     * 4. Transactions that meet the criteria are then converted into transaction snapshots.
     * 5. These snapshots are saved to the snapshot storage.
     * 6. Upon successful transfer to the snapshot storage, the original transactions are deleted from the active storage.
     * </p>
     *
     * <p>
     * Note: Running this method periodically, such as at the end of the day, ensures the active transaction storage remains
     * optimized for performance and that historical data is preserved.
     * </p>
     *
     * @param walletId The ID of the wallet for which the snapshot needs to be captured.
     * @throws IllegalArgumentException if the wallet ID is null.
     */
    @Transactional
    public void captureDailySnapshotForWallet(@NotNull Integer walletId) {
        // 1. Fetch all transactions that meet the criteria using a JOIN
        List<Transaction> transactionsToSnapshot = entityManager.createQuery(
                        """
                        SELECT t
                        FROM Transaction t
                        JOIN TransactionGroup tg ON t.referenceId = tg.id
                        WHERE t.walletId = :walletId
                          AND tg.status IN (:statuses)
                        """, Transaction.class)
                .setParameter("walletId", walletId)
                .setParameter("statuses", Arrays.asList(TransactionGroupStatus.CONFIRMED, TransactionGroupStatus.REJECTED))
                .getResultList();

        if (transactionsToSnapshot.isEmpty()) {
            // No transactions to snapshot
            return;
        }

        // 2. Convert transactions to snapshots
        List<TransactionSnapshot> snapshots = transactionsToSnapshot.stream()
                .map(transaction -> new TransactionSnapshot(
                        transaction.getId(),
                        transaction.getWalletId(),
                        transaction.getType(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getHoldReserveTimestamp(),
                        transaction.getConfirmRejectTimestamp(),
                        LocalDateTime.now(), // snapshot creation timestamp
                        transaction.getReferenceId(),
                        transaction.getDescription()
                ))
                .toList();

        // 3. Save snapshots in a batch
        transactionSnapshotRepository.saveAll(snapshots);

        // 4. Delete transactions in a batch, ensuring all snapshots were saved
        long savedSnapshotsCount = transactionSnapshotRepository.countByIdIn(
                snapshots.stream().map(TransactionSnapshot::getId).toList());

        if (savedSnapshotsCount == snapshots.size()) {
            // All snapshots were saved successfully, proceed to delete transactions
            transactionRepository.deleteAll(transactionsToSnapshot);
        } else {
            // Log an error or throw an exception to prevent data loss
            throw new IllegalStateException("Failed to save all snapshots. Transaction deletion aborted.");
        }
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
     * <p>
     * Note: It is recommended to regularly run this method, e.g., as a part of nightly batch jobs, to ensure optimal performance of the active storage.
     * </p>
     *
     * @param walletId
     * @param olderThan
     */
    @Transactional
    public void archiveOldSnapshots(@NotNull Integer walletId, @NotNull LocalDateTime olderThan) {
        // IMPORTANT: Transaction snapshot HAS NO any IN_PROGRESS transaction group.
        // So it is safe to just move transaction to archive and don't care about
        // transactions that are currently going. All of them keep their state in transaction table.

        // 1. Calculate the cumulative balance of old snapshots for the given walletId that will be archived.
        // SELECT SUM(amount)
        //    FROM transaction_snapshot
        //        WHERE wallet_id = 2 AND snapshot_date < TO_TIMESTAMP('2023-08-22 17:32:40', 'YYYY-MM-DD HH24:MI:SS') AND is_ledger_entry = FALSE AND status = 'CONFIRMED';
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

        if(cumulativeBalance.longValue() == 0) {
            // Nothing to archive.
            return;
        }

        // 2. Insert the new ledger entry and get its ID
        String insertLedgerSql = """
                    INSERT INTO transaction_snapshot(wallet_id, amount, status, type, snapshot_date, is_ledger_entry)
                        VALUES(:walletId, :cumulativeBalance, :status, :type, :olderThan, TRUE)
                            RETURNING id;
                """;

        Query insertLedgerQuery = entityManager.createNativeQuery(insertLedgerSql);
        insertLedgerQuery.setParameter("walletId", walletId);
        insertLedgerQuery.setParameter("cumulativeBalance", cumulativeBalance);
        insertLedgerQuery.setParameter("olderThan", olderThan);
        insertLedgerQuery.setParameter("status", TransactionStatus.CONFIRMED.name());
        insertLedgerQuery.setParameter("type", TransactionType.LEDGER.name());

        Integer ledgerEntryId = (Integer) insertLedgerQuery.getSingleResult();

        // 3. Query unique list of transaction groups associated to new ledger entry.
        String uniqueListOfRefIdsSql = """
                    SELECT DISTINCT reference_id
                        FROM transaction_snapshot
                        WHERE wallet_id = :walletId AND snapshot_date < :olderThan AND is_ledger_entry = FALSE AND status = 'CONFIRMED'
                """;

        Query uniqueListOfRefIdsQuery = entityManager.createNativeQuery(uniqueListOfRefIdsSql);
        uniqueListOfRefIdsQuery.setParameter("walletId", walletId);
        uniqueListOfRefIdsQuery.setParameter("olderThan", olderThan);

        List<UUID> referenceIdList = uniqueListOfRefIdsQuery.getResultList();
        if(referenceIdList.size() == 0) {
            // Unreachable statement
            throw new UnsupportedOperationException("Unreachable statement, cumulativeBalance must not be positive without transaction groups.");
        }

        // 4. Save ledgerEntryId and the corresponding reference Ids to ledger_entries_tracking table.
        String insertLederTrackingEntriesSql = """
            INSERT INTO ledger_entries_tracking (ledeger_entry_id, reference_id)
            VALUES \n
        """;

        StringBuffer values = new StringBuffer();
        if(referenceIdList != null && referenceIdList.size() > 0) {
            referenceIdList.forEach(referenceId -> {
                values.append("(")
                        .append(ledgerEntryId)
                        .append(",")
                        .append("'")
                        .append(referenceId)
                        .append("'")
                        .append(")")
                        .append(",\n");
            });
        }
        int lastComma = values.length() - 2;
        values.replace(lastComma, lastComma + 1, ";");

        insertLederTrackingEntriesSql = insertLederTrackingEntriesSql + values.toString();
        Query insertLederTrackingEntriesQuery = entityManager.createNativeQuery(insertLederTrackingEntriesSql);
        insertLederTrackingEntriesQuery.executeUpdate();

        // 5. Insert old snapshots into transaction_snapshot_archive table
        String insertIntoArchiveSql = """
                    INSERT INTO transaction_snapshot_archive
                        SELECT id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, snapshot_date, reference_id, description FROM transaction_snapshot
                            WHERE wallet_id = :walletId AND snapshot_date < :olderThan AND is_ledger_entry = FALSE
                """;

        Query insertIntoArchiveQuery = entityManager.createNativeQuery(insertIntoArchiveSql);
        insertIntoArchiveQuery.setParameter("walletId", walletId);
        insertIntoArchiveQuery.setParameter("olderThan", olderThan);

        insertIntoArchiveQuery.executeUpdate();

        // 6. Delete the old snapshots from transaction_snapshot table
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
