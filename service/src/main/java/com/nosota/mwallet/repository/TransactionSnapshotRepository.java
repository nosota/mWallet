package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionSnapshot;
import com.nosota.mwallet.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionSnapshotRepository extends JpaRepository<TransactionSnapshot, Integer> {
    /**
     * Retrieves the sum of all HOLD transaction amounts for the specified wallet.
     *
     * @param walletId The unique identifier (ID) of the wallet.
     * @return The total of HOLD amounts. If no transactions are found, returns 0.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionSnapshot t WHERE t.walletId = :walletId AND t.status = 'HOLD'")
    Long getHoldBalanceForWallet(@Param("walletId") Integer walletId);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'CREDIT' AND ts.status = 'SETTLED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :startOfDay AND :date ORDER BY ts.id ASC")
    List<TransactionSnapshot> findDailyCreditSnapshotOperations(@Param("walletId") Integer walletId,
                                                                @Param("startOfDay") LocalDateTime startOfDay,
                                                                @Param("date") LocalDateTime date);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'DEBIT' AND ts.status = 'SETTLED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :startOfDay AND :date ORDER BY ts.id ASC")
    List<TransactionSnapshot> findDailyDebitSnapshotOperations(@Param("walletId") Integer walletId,
                                                               @Param("startOfDay") LocalDateTime startOfDay,
                                                               @Param("date") LocalDateTime date);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'CREDIT' AND ts.status = 'SETTLED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :fromDate AND :toDate ORDER BY ts.id ASC")
    List<TransactionSnapshot> findCreditSnapshotOperationsInRange(@Param("walletId") Integer walletId,
                                                                  @Param("fromDate") LocalDateTime fromDate,
                                                                  @Param("toDate") LocalDateTime toDate);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'DEBIT' AND ts.status = 'SETTLED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :fromDate AND :toDate ORDER BY ts.id ASC")
    List<TransactionSnapshot> findDebitSnapshotOperationsInRange(@Param("walletId") Integer walletId,
                                                                 @Param("fromDate") LocalDateTime fromDate,
                                                                 @Param("toDate") LocalDateTime toDate);

    long countByIdIn(List<Integer> ids);

    @Query("SELECT COUNT(ts) FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.referenceId IN :referenceIds")
    long countByWalletIdAndReferenceIds(@Param("walletId") Integer walletId, @Param("referenceIds") List<UUID> referenceIds);

    @Query("SELECT COUNT(ts) FROM TransactionSnapshot ts WHERE ts.referenceId IN :referenceIds")
    long countByReferenceIdIn(@Param("referenceIds") List<UUID> referenceIds);

    @Query("""
    SELECT COALESCE(SUM(t.amount), 0) 
    FROM TransactionSnapshot t 
    WHERE t.walletId = :walletId 
      AND t.snapshotDate < :olderThan 
      AND t.status = :status 
      AND t.isLedgerEntry = :isLedgerEntry
    """)
    Long findCumulativeBalance(@Param("walletId") Integer walletId,
                               @Param("olderThan") LocalDateTime olderThan,
                               @Param("status") String status,
                               @Param("isLedgerEntry") Boolean isLedgerEntry);

    @Query("""
    SELECT DISTINCT t.referenceId 
    FROM TransactionSnapshot t 
    WHERE t.walletId = :walletId 
      AND t.snapshotDate < :olderThan 
      AND t.status = :status 
      AND t.isLedgerEntry = :isLedgerEntry
    """)
    List<UUID> findDistinctReferenceIds(@Param("walletId") Integer walletId,
                                        @Param("olderThan") LocalDateTime olderThan,
                                        @Param("status") TransactionStatus status,
                                        @Param("isLedgerEntry") Boolean isLedgerEntry);

//    @Modifying
//    @Query("""
//    INSERT INTO transaction_snapshot_archive
//    SELECT t FROM TransactionSnapshot t
//    WHERE t.walletId = :walletId
//      AND t.snapshotDate < :olderThan
//      AND t.isLedgerEntry = :isLedgerEntry
//    """)
//    int archiveOldSnapshots(@Param("walletId") Integer walletId,
//                            @Param("olderThan") LocalDateTime olderThan,
//                            @Param("isLedgerEntry") Boolean isLedgerEntry);
    @Modifying
    @Query(value = """
    INSERT INTO transaction_snapshot_archive (id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, snapshot_date, reference_id, description, is_ledger_entry)
    SELECT t.id, t.wallet_id, t.type, t.amount, t.status, t.hold_reserve_timestamp, t.confirm_reject_timestamp, t.snapshot_date, t.reference_id, t.description, t.is_ledger_entry
    FROM transaction_snapshot t
    WHERE t.wallet_id = :walletId AND t.snapshot_date < :olderThan AND t.is_ledger_entry = :isLedgerEntry
    """, nativeQuery = true)
    int archiveOldSnapshots(@Param("walletId") Integer walletId, @Param("olderThan") LocalDateTime olderThan, @Param("isLedgerEntry") Boolean isLedgerEntry);

    @Modifying
    @Query("""
    DELETE FROM TransactionSnapshot t 
    WHERE t.walletId = :walletId 
      AND t.snapshotDate < :olderThan 
      AND t.isLedgerEntry = :isLedgerEntry
    """)
    int deleteOldSnapshots(@Param("walletId") Integer walletId,
                           @Param("olderThan") LocalDateTime olderThan,
                           @Param("isLedgerEntry") Boolean isLedgerEntry);

    // Uses COALESCE to return 0 if there are no matching rows (avoids null values).
    @Query("SELECT COALESCE(SUM(t.amount), 0) " +
            "FROM TransactionSnapshot t " +
            "WHERE t.walletId = :walletId " +
            "AND t.snapshotDate < :olderThan " +
            "AND t.isLedgerEntry = FALSE " +
            "AND t.status = 'SETTLED'")
    Long calculateCumulativeBalance(@Param("walletId") Integer walletId,
                                    @Param("olderThan") LocalDateTime olderThan);
}
