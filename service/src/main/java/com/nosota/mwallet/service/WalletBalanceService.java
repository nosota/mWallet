package com.nosota.mwallet.service;

import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletBalanceService {
    private final EntityManager entityManager;

    private final TransactionSnapshotRepository transactionSnapshotRepository;

    /**
     * Retrieves the available balance of a wallet with the given ID.
     *
     * <p>
     * The method calculates the available balance by considering both confirmed transactions and amounts
     * currently held for transactions that are not yet completed. The confirmed transactions include those
     * from both the transaction and transaction_snapshot tables with a status of 'CONFIRMED'.
     * </p>
     *
     * <p>
     * To calculate the available balance:
     * 1. Sum up the amounts of all 'CONFIRMED' transactions from both transaction and transaction_snapshot tables.
     * 2. Subtract the sum of amounts of 'HOLD' transactions that are part of transaction groups that are still in progress.
     * 3. Reserved amounts for ongoing transactions are ignored in this calculation as they do not affect the available balance.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet whose available balance is to be retrieved.
     *                 Must not be {@code null}.
     *
     * @return The available balance of the wallet. It is the difference between the confirmed balance
     *         and the amount on hold for incomplete transactions.
     *
     * @throws IllegalArgumentException If {@code walletId} is {@code null}.
     */
    @Transactional
    public Long getAvailableBalance(@NotNull Integer walletId) {
        // 1. Get confirmed balance.
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
        Long confirmedBalance = 0L;
        if (result != null) {
            confirmedBalance = result.longValueExact();
        }

        // 2. Get HOLD balance of currently running transactions. The money is not available, they are held.
        Long ongoingTransactionBalance = getHoldAmountForIncompleteTransactionGroups(walletId);

        // 3. RESERVED amounts of currently running transactions are ignored.

        // 4. Calculate currently available balance.
        Long availableBalance = confirmedBalance - ongoingTransactionBalance;

        return availableBalance;
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

    protected Long getHoldAmountForIncompleteTransactionGroups(@NotNull Integer walletId) {
        String sql = """
            SELECT
                SUM(t.amount)
            FROM 
                transaction t
            JOIN 
                transaction_group tg ON t.reference_id = tg.id
            WHERE 
                t.wallet_id = :walletId AND 
                t.status = 'HOLD' AND
                tg.status = 'IN_PROGRESS'
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("walletId", walletId);

        BigDecimal result = (BigDecimal) query.getSingleResult();
        if (result != null) {
            return - result.longValueExact(); // make the value positive
        } else {
            return 0L;
        }
    }
}
