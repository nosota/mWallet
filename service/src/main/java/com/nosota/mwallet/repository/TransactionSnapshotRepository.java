package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionSnapshot t WHERE t.walletId = :walletId AND t.status = 'RESERVED'")
    Long getReservedBalanceForWallet(@Param("walletId") Integer walletId);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'CREDIT' AND ts.status = 'CONFIRMED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :startOfDay AND :date")
    List<TransactionSnapshot> findDailyCreditSnapshotOperations(@Param("walletId") Integer walletId,
                                                                @Param("startOfDay") LocalDateTime startOfDay,
                                                                @Param("date") LocalDateTime date);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'DEBIT' AND ts.status = 'CONFIRMED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :startOfDay AND :date")
    List<TransactionSnapshot> findDailyDebitSnapshotOperations(@Param("walletId") Integer walletId,
                                                               @Param("startOfDay") LocalDateTime startOfDay,
                                                               @Param("date") LocalDateTime date);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'CREDIT' AND ts.status = 'CONFIRMED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :fromDate AND :toDate")
    List<TransactionSnapshot> findCreditSnapshotOperationsInRange(@Param("walletId") Integer walletId,
                                                                  @Param("fromDate") LocalDateTime fromDate,
                                                                  @Param("toDate") LocalDateTime toDate);

    @Query("SELECT ts FROM TransactionSnapshot ts WHERE ts.walletId = :walletId AND ts.type = 'DEBIT' AND ts.status = 'CONFIRMED' " +
            "AND ts.confirmRejectTimestamp BETWEEN :fromDate AND :toDate")
    List<TransactionSnapshot> findDebitSnapshotOperationsInRange(@Param("walletId") Integer walletId,
                                                                 @Param("fromDate") LocalDateTime fromDate,
                                                                 @Param("toDate") LocalDateTime toDate);
}
