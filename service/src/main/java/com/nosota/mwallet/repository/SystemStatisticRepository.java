package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface SystemStatisticRepository extends JpaRepository<Transaction, Integer> {

    @Query(nativeQuery = true, value = """
        WITH ConfirmedGroups AS (
            SELECT reference_id FROM transaction WHERE status = 'CONFIRMED'
            UNION
            SELECT reference_id FROM transaction_snapshot WHERE status = 'CONFIRMED'
            UNION
            SELECT reference_id FROM transaction_snapshot_archive WHERE status = 'CONFIRMED'
        )
        SELECT SUM(amount)
        FROM (
            SELECT SUM(amount) as amount FROM transaction WHERE reference_id IN (SELECT reference_id FROM ConfirmedGroups) GROUP BY reference_id
            UNION ALL
            SELECT SUM(amount) as amount FROM transaction_snapshot WHERE reference_id IN (SELECT reference_id FROM ConfirmedGroups) GROUP BY reference_id
            UNION ALL
            SELECT SUM(amount) as amount FROM transaction_snapshot_archive WHERE reference_id IN (SELECT reference_id FROM ConfirmedGroups) GROUP BY reference_id
        ) AS GroupedTransactions
    """)
    BigDecimal calculateReconciliationBalanceOfAllConfirmedGroups();
}
