package com.nosota.mwallet.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class WalletBalanceService {
    @Autowired
    private EntityManager entityManager;

    /**
     * In the WalletBalanceService.getAvailableBalance, the query is designed to sum the amounts
     * from both the transaction table and the transaction_snapshot table where the status is CONFIRMED.
     * This includes the ledger entries as well as regular transaction snapshots, ensuring that the
     * ledger entries contribute to the available balance.
     *
     * @param walletId
     * @return
     */
    public Long getAvailableBalance(Integer walletId) {
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
}
