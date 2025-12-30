package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.api.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    // This method fetches all transactions with a given status
    List<Transaction> findAllByStatus(TransactionStatus status);

    // This method fetches all transactions that have a referenceId in the given list of reference IDs
    List<Transaction> findAllByReferenceIdIn(List<UUID> referenceIds);

    List<Transaction> findByReferenceId(UUID referenceId);

    List<Transaction> findByReferenceIdOrderByIdAsc(UUID referenceId);

    List<Transaction> findByReferenceIdOrderByIdDesc(UUID referenceId);

    List<Transaction> findByReferenceIdAndWalletIdOrderByIdDesc(UUID referenceId, Integer walletId);

    /**
     * Retrieves a transaction based on the provided wallet ID, reference ID, and one of the two specified statuses.
     *
     * <p>This method is specifically designed to fetch a transaction that matches the given wallet ID and reference ID,
     * and whose status is either the first or the second status provided. It's particularly useful when you
     * want to narrow down your search criteria based on multiple potential statuses.</p>
     *
     * @param walletId The unique identifier of the wallet associated with the transaction.
     * @param referenceId The unique identifier used to group or reference the transaction.
     * @param status1 The first potential status the transaction could have.
     * @param status2 The second potential status the transaction could have.
     * @return An Optional containing the transaction if found; otherwise, an empty Optional.
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.referenceId = :referenceId AND (t.status = :status1 OR t.status = :status2)")
    Optional<Transaction> findByWalletIdAndReferenceIdAndStatuses(@Param("walletId") Integer walletId, @Param("referenceId") UUID referenceId, @Param("status1") TransactionStatus status1, @Param("status2") TransactionStatus status2);

    /**
     * Retrieves a transaction based on the provided wallet ID, reference ID, and a single specified status.
     *
     * <p>This method is designed to fetch a transaction that matches the given wallet ID and reference ID,
     * and has the specified status. Used by the ledger service to find HOLD transactions.</p>
     *
     * @param walletId The unique identifier of the wallet associated with the transaction.
     * @param referenceId The unique identifier used to group or reference the transaction.
     * @param status The status the transaction must have.
     * @return An Optional containing the transaction if found; otherwise, an empty Optional.
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.referenceId = :referenceId AND t.status = :status")
    Optional<Transaction> findByWalletIdAndReferenceIdAndStatuses(@Param("walletId") Integer walletId, @Param("referenceId") UUID referenceId, @Param("status") TransactionStatus status);

    /**
     * Retrieves all transactions associated with a specific wallet ID and transaction status.
     *
     * @param walletId The ID of the wallet to filter transactions by.
     * @param status The status of the transactions to retrieve.
     * @return A list of transactions matching the specified wallet ID and transaction status.
     */
    List<Transaction> findAllByWalletIdAndStatus(Integer walletId, TransactionStatus status);

    /**
     * Retrieves all transactions for a specific wallet, reference ID, and status.
     *
     * @param walletId The ID of the wallet to filter transactions by.
     * @param referenceId The reference ID (transaction group) to filter by.
     * @param status The status of the transactions to retrieve.
     * @return A list of transactions matching the criteria.
     */
    List<Transaction> findAllByWalletIdAndReferenceIdAndStatus(Integer walletId, UUID referenceId, TransactionStatus status);

    /**
     * Retrieves all transactions associated with a specific wallet ID and a set of reference IDs.
     *
     * @param walletId The ID of the wallet to filter transactions by.
     * @param referenceIds The set of reference IDs to filter the transactions by.
     * @return A list of transactions matching the specified wallet ID and contained within the set of reference IDs.
     */
    List<Transaction> findAllByWalletIdAndReferenceIdIn(Integer walletId, List<UUID> referenceIds);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'CREDIT' AND t.status = 'SETTLED' " +
            "AND t.confirmRejectTimestamp BETWEEN :startOfDay AND :date ORDER BY t.id ASC")
    List<Transaction> findDailyCreditOperations(@Param("walletId") Integer walletId,
                                                @Param("startOfDay") LocalDateTime startOfDay,
                                                @Param("date") LocalDateTime date);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'DEBIT' AND t.status = 'SETTLED' " +
            "AND t.confirmRejectTimestamp BETWEEN :startOfDay AND :date ORDER BY t.id ASC")
    List<Transaction> findDailyDebitOperations(@Param("walletId") Integer walletId,
                                               @Param("startOfDay") LocalDateTime startOfDay,
                                               @Param("date") LocalDateTime date);
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'CREDIT' AND t.status = 'SETTLED' " +
            "AND t.confirmRejectTimestamp BETWEEN :fromDate AND :toDate ORDER BY t.id ASC")
    List<Transaction> findCreditOperationsInRange(@Param("walletId") Integer walletId,
                                                  @Param("fromDate") LocalDateTime fromDate,
                                                  @Param("toDate") LocalDateTime toDate);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'DEBIT' AND t.status = 'SETTLED' " +
            "AND t.confirmRejectTimestamp BETWEEN :fromDate AND :toDate ORDER BY t.id ASC")
    List<Transaction> findDebitOperationsInRange(@Param("walletId") Integer walletId,
                                                 @Param("fromDate") LocalDateTime fromDate,
                                                 @Param("toDate") LocalDateTime toDate);


    /**
     * Calculates the total reconciliation amount for a transaction group consisting of HOLD transactions.
     *
     * <p>According to double-entry accounting principles, all HOLD transactions in a group must sum to zero
     * before the group can be finalized (settled, released, or cancelled).</p>
     *
     * <p>Example: For a transfer of $100 from wallet A to wallet B:
     * <ul>
     *   <li>Wallet A: -100 (DEBIT, HOLD)</li>
     *   <li>Wallet B: +100 (CREDIT, HOLD)</li>
     *   <li>Sum: 0 ✓ (valid for finalization)</li>
     * </ul>
     *
     * @param groupId The unique identifier (UUID) of the transaction group for
     *                which the reconciliation amount needs to be calculated.
     * @return The total reconciliation amount for the transaction group. Must be 0 for valid group.
     */
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.referenceId = :groupId AND t.status = 'HOLD'")
    Long getReconciliationAmountByGroupId(@Param("groupId") UUID groupId);

    /**
     * Calculates the total sum of all transactions in the system.
     * According to double-entry accounting, this must always equal 0.
     *
     * @return Total sum of all transactions (should be 0)
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t")
    Long getTotalSum();

    /**
     * Calculates the sum of transactions by status.
     *
     * @param status Transaction status to filter by
     * @return Sum of transactions with the given status
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.status = :status")
    Long getSumByStatus(@Param("status") TransactionStatus status);
}
