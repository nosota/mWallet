package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
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
     * Retrieves all transactions associated with a specific wallet ID and transaction status.
     *
     * @param walletId The ID of the wallet to filter transactions by.
     * @param status The status of the transactions to retrieve.
     * @return A list of transactions matching the specified wallet ID and transaction status.
     */
    List<Transaction> findAllByWalletIdAndStatus(Integer walletId, TransactionStatus status);

    /**
     * Retrieves all transactions associated with a specific wallet ID and a set of reference IDs.
     *
     * @param walletId The ID of the wallet to filter transactions by.
     * @param referenceIds The set of reference IDs to filter the transactions by.
     * @return A list of transactions matching the specified wallet ID and contained within the set of reference IDs.
     */
    List<Transaction> findAllByWalletIdAndReferenceIdIn(Integer walletId, List<UUID> referenceIds);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'CREDIT' AND t.status = 'CONFIRMED' " +
            "AND t.confirmRejectTimestamp BETWEEN :startOfDay AND :date")
    List<Transaction> findDailyCreditOperations(@Param("walletId") Integer walletId,
                                                @Param("startOfDay") LocalDateTime startOfDay,
                                                @Param("date") LocalDateTime date);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'DEBIT' AND t.status = 'CONFIRMED' " +
            "AND t.confirmRejectTimestamp BETWEEN :startOfDay AND :date")
    List<Transaction> findDailyDebitOperations(@Param("walletId") Integer walletId,
                                               @Param("startOfDay") LocalDateTime startOfDay,
                                               @Param("date") LocalDateTime date);
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'CREDIT' AND t.status = 'CONFIRMED' " +
            "AND t.confirmRejectTimestamp BETWEEN :fromDate AND :toDate")
    List<Transaction> findCreditOperationsInRange(@Param("walletId") Integer walletId,
                                                  @Param("fromDate") LocalDateTime fromDate,
                                                  @Param("toDate") LocalDateTime toDate);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.type = 'DEBIT' AND t.status = 'CONFIRMED' " +
            "AND t.confirmRejectTimestamp BETWEEN :fromDate AND :toDate")
    List<Transaction> findDebitOperationsInRange(@Param("walletId") Integer walletId,
                                                 @Param("fromDate") LocalDateTime fromDate,
                                                 @Param("toDate") LocalDateTime toDate);
}
