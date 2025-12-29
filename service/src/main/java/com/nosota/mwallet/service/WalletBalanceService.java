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
     * The method calculates the available balance following banking ledger standards:
     * Available Balance = Sum(SETTLED and REFUNDED transactions) - Sum(HOLD transactions for IN_PROGRESS groups)
     * </p>
     *
     * <p>
     * To calculate the available balance:
     * 1. Sum up the amounts of all SETTLED and REFUNDED transactions from both transaction and transaction_snapshot tables.
     * 2. Subtract the sum of amounts of HOLD transactions that are part of transaction groups with IN_PROGRESS status.
     * 3. RELEASED and CANCELLED transactions net to zero (opposite direction) and don't affect balance.
     * </p>
     *
     * <p>
     * Note: SETTLED and REFUNDED are the final statuses that affect balance. RELEASED and CANCELLED transactions
     * create offsetting entries that return funds to their original location, resulting in net zero effect.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet whose available balance is to be retrieved.
     *                 Must not be {@code null}.
     *
     * @return The available balance of the wallet. It is the difference between the settled/refunded balance
     *         and the amount on hold for incomplete transaction groups.
     *
     * @throws IllegalArgumentException If {@code walletId} is {@code null}.
     */
    @Transactional
    public Long getAvailableBalance(@NotNull Integer walletId) {
        // 1. Get settled balance (SETTLED and REFUNDED transactions affect balance).
        String sql = """
            SELECT
                COALESCE(SUM(amount), 0)
            FROM (
                SELECT amount FROM transaction WHERE wallet_id = :walletId AND (status = 'SETTLED' OR status = 'REFUNDED')
                UNION ALL
                SELECT amount FROM transaction_snapshot WHERE wallet_id = :walletId AND (status = 'SETTLED' OR status = 'REFUNDED')
            ) AS combined_data
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("walletId", walletId);

        BigDecimal result = (BigDecimal) query.getSingleResult();
        Long settledBalance = 0L;
        if (result != null) {
            settledBalance = result.longValueExact();
        }

        // 2. Get HOLD balance of currently IN_PROGRESS transaction groups.
        // These funds are blocked and not yet available.
        Long ongoingTransactionBalance = getHoldAmountForIncompleteTransactionGroups(walletId);

        // 3. Calculate currently available balance.
        Long availableBalance = settledBalance - ongoingTransactionBalance;

        log.info("Balance calculation for wallet {}: settled={}, hold={}, available={}",
                walletId, settledBalance, ongoingTransactionBalance, availableBalance);

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

    protected Long getHoldAmountForIncompleteTransactionGroups(@NotNull Integer walletId) {
        // Only DEBIT HOLD transactions reduce available balance
        // CREDIT HOLD transactions don't affect available balance until settlement
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
                t.type = 'DEBIT' AND
                tg.status = 'IN_PROGRESS'
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("walletId", walletId);

        BigDecimal result = (BigDecimal) query.getSingleResult();
        if (result != null) {
            return - result.longValueExact(); // DEBIT is negative, invert to positive for subtraction
        } else {
            return 0L;
        }
    }
}
