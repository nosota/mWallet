package com.nosota.mwallet.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class WalletBalanceService {
    @Autowired
    private EntityManager entityManager;

    public Long getAvailableBalance(Integer walletId) {
        String sql = """
            SELECT 
                SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) - 
                SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) 
            FROM (
                SELECT amount, type FROM transactions WHERE wallet_id = :walletId AND status = 'CONFIRMED'
                UNION ALL
                SELECT amount, type FROM wallet_snapshots WHERE wallet_id = :walletId AND status = 'CONFIRMED'
            ) AS combined_data
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("walletId", walletId);

        Long result = (Long) query.getSingleResult();
        return result != null ? result : 0L;
    }
}
