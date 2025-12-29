package com.nosota.mwallet.repository;

import com.nosota.mwallet.api.model.RefundStatus;
import com.nosota.mwallet.api.model.RefundType;
import com.nosota.mwallet.model.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Refund} entity operations.
 *
 * <p>Provides data access methods for refund operations including:
 * <ul>
 *   <li>Finding refunds by order (transaction group)</li>
 *   <li>Finding refunds by merchant</li>
 *   <li>Finding refunds by status</li>
 *   <li>Calculating total refunded amount for validation</li>
 *   <li>Idempotency support via idempotency key lookup</li>
 * </ul>
 */
@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    /**
     * Finds all refunds for a specific order (transaction group).
     *
     * @param transactionGroupId The transaction group ID
     * @return List of refunds for this order
     */
    List<Refund> findByTransactionGroupId(UUID transactionGroupId);

    /**
     * Finds all refunds for a specific merchant with pagination.
     *
     * @param merchantId The merchant ID
     * @param pageable   Pagination information
     * @return Page of refunds
     */
    Page<Refund> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    /**
     * Finds all refunds with a specific status.
     *
     * @param status The refund status
     * @return List of refunds with this status
     */
    List<Refund> findByStatus(RefundStatus status);

    /**
     * Finds all refunds with a specific status that are older than the given time.
     * Used for processing PENDING_FUNDS refunds or expiring old ones.
     *
     * @param status    The refund status
     * @param olderThan Timestamp threshold
     * @return List of refunds matching criteria
     */
    List<Refund> findByStatusAndCreatedAtBefore(RefundStatus status, LocalDateTime olderThan);

    /**
     * Finds all PENDING_FUNDS refunds that have expired.
     *
     * @param now Current timestamp
     * @return List of expired refunds
     */
    @Query("""
            SELECT r
            FROM Refund r
            WHERE r.status = 'PENDING_FUNDS'
              AND r.expiresAt IS NOT NULL
              AND r.expiresAt < :now
            """)
    List<Refund> findExpiredPendingFundsRefunds(@Param("now") LocalDateTime now);

    /**
     * Calculates total amount refunded for a specific order.
     * Used for validation (total refunds ≤ net amount).
     *
     * @param transactionGroupId The transaction group ID
     * @return Total amount refunded (or 0 if no refunds)
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM Refund r
            WHERE r.transactionGroupId = :transactionGroupId
              AND r.status IN ('PROCESSING', 'COMPLETED')
            """)
    Long calculateTotalRefundedForOrder(@Param("transactionGroupId") UUID transactionGroupId);

    /**
     * Checks if there are any pending (not completed/rejected/failed) refunds for an order.
     *
     * @param transactionGroupId The transaction group ID
     * @return true if there are pending refunds
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM Refund r
            WHERE r.transactionGroupId = :transactionGroupId
              AND r.status IN ('PENDING', 'PENDING_FUNDS', 'PROCESSING')
            """)
    boolean hasPendingRefunds(@Param("transactionGroupId") UUID transactionGroupId);

    /**
     * Finds a specific refund by transaction group and status.
     * Useful for checking if order already has a refund in specific status.
     *
     * @param transactionGroupId The transaction group ID
     * @param status             The refund status
     * @return Optional containing refund if found
     */
    Optional<Refund> findByTransactionGroupIdAndStatus(UUID transactionGroupId, RefundStatus status);

    /**
     * Counts refunds for a merchant by status.
     *
     * @param merchantId The merchant ID
     * @param status     The refund status
     * @return Count of refunds
     */
    long countByMerchantIdAndStatus(Long merchantId, RefundStatus status);

    /**
     * Finds all refunds within a date range for a merchant.
     *
     * @param merchantId The merchant ID
     * @param startDate  Start of the date range
     * @param endDate    End of the date range
     * @param pageable   Pagination information
     * @return Page of refunds
     */
    Page<Refund> findByMerchantIdAndProcessedAtBetweenOrderByProcessedAtDesc(
            Long merchantId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Finds a refund by idempotency key combination.
     * <p>
     * Used for preventing duplicate refund execution.
     * Composite lookup: (transaction_group_id, refund_type, idempotency_key)
     * </p>
     *
     * @param transactionGroupId The transaction group ID
     * @param refundType         The refund type (FULL or PARTIAL)
     * @param idempotencyKey     The idempotency key
     * @return Optional containing the refund if found
     */
    Optional<Refund> findByTransactionGroupIdAndRefundTypeAndIdempotencyKey(
            UUID transactionGroupId,
            RefundType refundType,
            String idempotencyKey
    );
}
