package com.nosota.mwallet.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class WalletBalanceService {
    @Autowired
    private EntityManager entityManager;

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
