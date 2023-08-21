package com.nosota.mwallet.service;

import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
public class WalletBalanceService {
    private final EntityManager entityManager;

    private final TransactionSnapshotRepository transactionSnapshotRepository;

    public WalletBalanceService(EntityManager entityManager, TransactionSnapshotRepository transactionSnapshotRepository) {
        this.entityManager = entityManager;
        this.transactionSnapshotRepository = transactionSnapshotRepository;
    }

    /**
     * Retrieves the available balance of the specified wallet by aggregating the confirmed transaction amounts
     * from both the main transaction table and the snapshot table.
     *
     * The available balance is calculated by summing up the amounts of all 'CONFIRMED' transactions
     * related to the wallet from both transaction sources.
     *
     * <p>
     * This method utilizes a native SQL query that unifies data from the main transaction table and the snapshot
     * table to efficiently compute the total confirmed amount. The result is then cast to a {@link BigDecimal}
     * and converted to a long value.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet for which the balance is being retrieved.
     * @return The available balance of the wallet. If no transactions or snapshots are found, returns 0.
     *
     */
    public Long getAvailableBalance(@NotNull Integer walletId) {
        String sql = """
            SELECT
                SUM(amount)
            FROM (
                SELECT amount FROM transaction WHERE wallet_id = :walletId AND status = 'CONFIRMED'
                UNION ALL
                SELECT amount FROM transaction_snapshot WHERE wallet_id = :walletId AND status = 'CONFIRMED'
            ) AS combined_data
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("walletId", walletId);

        BigDecimal result = (BigDecimal) query.getSingleResult();
        if (result != null) {
            return result.longValueExact();
        } else {
            return 0L;
        }
    }

    /**
     * Retrieves the sum of all HOLD transaction amounts for the specified wallet.
     *
     * @param walletId The unique identifier (ID) of the wallet.
     * @return The total of HOLD amounts. If no transactions are found, returns 0.
     */
    public Long getHoldBalanceForWallet(@NotNull Integer walletId) {
        return transactionSnapshotRepository.getHoldBalanceForWallet(walletId);
    }

    /**
     * Retrieves the sum of all RESERVED transaction amounts for the specified wallet.
     *
     * @param walletId The unique identifier (ID) of the wallet.
     * @return The total of RESERVED amounts. If no transactions are found, returns 0.
     */
    public Long getReservedBalanceForWallet(@NotNull Integer walletId) {
        return transactionSnapshotRepository.getReservedBalanceForWallet(walletId);
    }
}
