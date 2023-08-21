package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Retrieves the sum of all RESERVED transaction amounts for the specified wallet.
     *
     * @param walletId The unique identifier (ID) of the wallet.
     * @return The total of RESERVED amounts. If no transactions are found, returns 0.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionSnapshot t WHERE t.walletId = :walletId AND t.status = 'RESERVED'")
    Long getReservedBalanceForWallet(@Param("walletId") Integer walletId);
}
