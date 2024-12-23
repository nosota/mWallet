package com.nosota.mwallet.service;

import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.LedgerTrackingRepository;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Validated
@AllArgsConstructor
@Slf4j
public class TransactionSnapshotService {
    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionRepository transactionRepository;

    private final TransactionSnapshotRepository transactionSnapshotRepository;

    private final TransactionGroupRepository transactionGroupRepository;

    private final LedgerTrackingRepository ledgerTrackingRepository;

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
                        null, // will be generated automatically
                        transaction.getWalletId(),
                        transaction.getType(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getHoldReserveTimestamp(),
                        transaction.getConfirmRejectTimestamp(),
                        LocalDateTime.now(), // snapshot creation timestamp
                        transaction.getReferenceId(),
                        transaction.getDescription(),
                        false // it is regular not ledger record
                ))
                .toList();

        // 3. Save snapshots in a batch
        transactionSnapshotRepository.saveAll(snapshots);

        // 4. Delete transactions in a batch, ensuring all snapshots were saved
        long savedSnapshotsCount = transactionSnapshotRepository.countByWalletIdAndReferenceIds(
                walletId,
                snapshots.stream().map(TransactionSnapshot::getReferenceId).toList()
        );

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
        // Step 1: Calculate the cumulative balance of snapshots to be archived.
        Long cumulativeBalance = transactionSnapshotRepository.calculateCumulativeBalance(walletId, olderThan);
        if (cumulativeBalance == 0L) {
            log.debug("No snapshots to archive for walletId={} olderThan={}", walletId, olderThan);
            return;
        }

        log.debug("Cumulative balance to archive: {} cents for walletId={}", cumulativeBalance, walletId);

        // Step 2: Create a new ledger entry in the transaction_snapshot table.
        TransactionSnapshot ledgerEntry = new TransactionSnapshot();
        // id will be automatically generated
        ledgerEntry.setWalletId(walletId);
        ledgerEntry.setAmount(cumulativeBalance);
        ledgerEntry.setType(TransactionType.LEDGER);
        ledgerEntry.setStatus(TransactionStatus.CONFIRMED);
        ledgerEntry.setSnapshotDate(LocalDateTime.now());
        ledgerEntry.setLedgerEntry(true);

        final TransactionSnapshot ledgerEntrySaved = transactionSnapshotRepository.save(ledgerEntry);
        log.debug("Ledger entry created with ID={} for walletId={}", ledgerEntrySaved.getId(), walletId);

        // Step 3: Retrieve reference IDs for the snapshots being archived.
        List<UUID> referenceIds = transactionSnapshotRepository.findDistinctReferenceIds(walletId, olderThan, TransactionStatus.CONFIRMED,false);
        if (referenceIds.isEmpty()) {
            log.error("Unexpected state: No reference IDs found despite a positive cumulative balance.");
            throw new IllegalStateException("Reference IDs not found for snapshots being archived.");
        }

        // Step 4: Save ledger entry tracking information.
        List<LedgerEntriesTracking> trackingEntries = referenceIds.stream()
                .map(referenceId -> new LedgerEntriesTracking(ledgerEntrySaved.getId(), referenceId))
                .collect(Collectors.toList());
        ledgerTrackingRepository.saveAll(trackingEntries);
        log.debug("Saved {} ledger tracking entries for ledgerId={}", trackingEntries.size(), ledgerEntrySaved.getId());

        // Step 5: Move old snapshots to the archive table.
        int archivedRows = transactionSnapshotRepository.archiveOldSnapshots(walletId, olderThan, false);
        log.debug("Archived {} snapshots for walletId={}", archivedRows, walletId);

        // Step 6: Delete old snapshots from the transaction_snapshot table.
        int deletedRows = transactionSnapshotRepository.deleteOldSnapshots(walletId, olderThan, false);
        if (archivedRows != deletedRows) {
            throw new IllegalStateException(String.format(
                    "Mismatch between archived and deleted snapshot counts: archived=%d, deleted=%d for walletId=%d",
                    archivedRows, deletedRows, walletId));
        } else {
            log.debug("Successfully deleted {} snapshots for walletId={}", deletedRows, walletId);
        }
    }
}
