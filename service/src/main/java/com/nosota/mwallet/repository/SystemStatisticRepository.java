package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface SystemStatisticRepository extends JpaRepository<Transaction, Integer> {

    /**
     * Calculates the reconciliation balance of all finalized transaction groups across the system.
     *
     * <p>This method sums all transactions in finalized groups (SETTLED, RELEASED, or CANCELLED status).
     * The result represents the cumulative initial balance of all wallets in the system.</p>
     *
     * <p>According to ledger principles:
     * - SETTLED groups should sum to zero (double-entry accounting)
     * - RELEASED groups should sum to zero (offsetting entries)
     * - CANCELLED groups should sum to zero (offsetting entries)
     * - Non-zero result indicates external funds (initial wallet balances)</p>
     *
     * <p>Note: This query processes all storage tiers (transaction, snapshot, archive) and may take time.</p>
     *
     * @return The reconciliation balance of all finalized groups, or null if no finalized transactions exist.
     */
    @Query(nativeQuery = true, value = """
        WITH FinalizedGroups AS (
            SELECT reference_id FROM transaction WHERE status IN ('SETTLED', 'RELEASED', 'CANCELLED')
            UNION
            SELECT reference_id FROM transaction_snapshot WHERE status IN ('SETTLED', 'RELEASED', 'CANCELLED')
            UNION
            SELECT reference_id FROM transaction_snapshot_archive WHERE status IN ('SETTLED', 'RELEASED', 'CANCELLED')
        )
        SELECT SUM(amount)
        FROM (
            SELECT SUM(amount) as amount FROM transaction WHERE reference_id IN (SELECT reference_id FROM FinalizedGroups) GROUP BY reference_id
            UNION ALL
            SELECT SUM(amount) as amount FROM transaction_snapshot WHERE reference_id IN (SELECT reference_id FROM FinalizedGroups) GROUP BY reference_id
            UNION ALL
            SELECT SUM(amount) as amount FROM transaction_snapshot_archive WHERE reference_id IN (SELECT reference_id FROM FinalizedGroups) GROUP BY reference_id
        ) AS GroupedTransactions
    """)
    BigDecimal calculateReconciliationBalanceOfAllFinalizedGroups();
}
