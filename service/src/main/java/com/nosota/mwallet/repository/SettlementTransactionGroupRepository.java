package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.SettlementTransactionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SettlementTransactionGroup} entity operations.
 *
 * <p>Provides data access methods for managing the M:N relationship
 * between settlements and transaction groups.
 */
@Repository
public interface SettlementTransactionGroupRepository extends JpaRepository<SettlementTransactionGroup, Long> {

    /**
     * Finds all transaction groups included in a specific settlement.
     *
     * @param settlementId The settlement ID
     * @return List of settlement-transaction group links
     */
    List<SettlementTransactionGroup> findBySettlementId(UUID settlementId);

    /**
     * Checks if a transaction group has already been included in any settlement.
     *
     * @param transactionGroupId The transaction group ID
     * @return true if the group is already settled, false otherwise
     */
    boolean existsByTransactionGroupId(UUID transactionGroupId);

    /**
     * Finds the settlement that includes a specific transaction group.
     *
     * @param transactionGroupId The transaction group ID
     * @return Optional containing the settlement-transaction group link if found
     */
    Optional<SettlementTransactionGroup> findByTransactionGroupId(UUID transactionGroupId);

    /**
     * Gets the list of transaction group IDs that have NOT been settled yet
     * for a specific merchant.
     *
     * @param merchantId The merchant ID
     * @return List of unsettled transaction group IDs
     */
    @Query("""
            SELECT tg.id
            FROM TransactionGroup tg
            WHERE tg.merchantId = :merchantId
              AND tg.status = 'SETTLED'
              AND tg.id NOT IN (
                SELECT stg.transactionGroupId
                FROM SettlementTransactionGroup stg
              )
            ORDER BY tg.id ASC
            """)
    List<UUID> findUnsettledTransactionGroups(@Param("merchantId") Long merchantId);

    /**
     * Counts the number of transaction groups in a specific settlement.
     *
     * @param settlementId The settlement ID
     * @return Count of transaction groups
     */
    long countBySettlementId(UUID settlementId);
}
