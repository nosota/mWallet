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
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Validated
public class TransactionSnapshotService {
    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionRepository transactionRepository;

    private final TransactionSnapshotRepository transactionSnapshotRepository;

    private final TransactionGroupRepository transactionGroupRepository;

    public TransactionSnapshotService(TransactionRepository transactionRepository,
                                      TransactionSnapshotRepository transactionSnapshotRepository,
                                      TransactionGroupRepository transactionGroupRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionSnapshotRepository = transactionSnapshotRepository;
        this.transactionGroupRepository = transactionGroupRepository;
    }

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
        // 1. Fetch all the CONFIRMED and REJECTED transaction groups for the specified wallet
        List<TransactionGroup> relevantGroups = transactionGroupRepository.findAllByStatusIn (
                Arrays.asList(TransactionGroupStatus.CONFIRMED, TransactionGroupStatus.REJECTED));

        // 2. Extract the reference IDs from these groups
        Set<UUID> referenceIdsToSnapshot = relevantGroups.stream()
                .map(TransactionGroup::getId)
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
                        transaction.getHoldReserveTimestamp(),
                        transaction.getConfirmRejectTimestamp(),
                        transaction.getReferenceId(),
                        transaction.getDescription()
                )).toList();

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
                        SELECT id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, snapshot_date, reference_id, description FROM transaction_snapshot
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
