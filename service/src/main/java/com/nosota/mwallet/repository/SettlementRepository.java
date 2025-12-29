package com.nosota.mwallet.repository;

import com.nosota.mwallet.api.model.SettlementStatus;
import com.nosota.mwallet.model.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Settlement} entity operations.
 *
 * <p>Provides data access methods for settlement operations including:
 * <ul>
 *   <li>Finding settlements by merchant</li>
 *   <li>Finding settlements by status</li>
 *   <li>Paginated history queries</li>
 *   <li>Idempotency support via idempotency key lookup</li>
 * </ul>
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /**
     * Finds all settlements for a specific merchant, ordered by creation time descending.
     *
     * @param merchantId The merchant ID
     * @param pageable   Pagination information
     * @return Page of settlements
     */
    Page<Settlement> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    /**
     * Finds all settlements for a specific merchant with a specific status.
     *
     * @param merchantId The merchant ID
     * @param status     The settlement status
     * @return List of settlements
     */
    List<Settlement> findByMerchantIdAndStatus(Long merchantId, SettlementStatus status);

    /**
     * Finds all settlements within a date range for a specific merchant.
     *
     * @param merchantId The merchant ID
     * @param startDate  Start of the date range
     * @param endDate    End of the date range
     * @param pageable   Pagination information
     * @return Page of settlements
     */
    Page<Settlement> findByMerchantIdAndSettledAtBetweenOrderBySettledAtDesc(
            Long merchantId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Counts settlements for a specific merchant with a specific status.
     *
     * @param merchantId The merchant ID
     * @param status     The settlement status
     * @return Count of settlements
     */
    long countByMerchantIdAndStatus(Long merchantId, SettlementStatus status);

    /**
     * Finds a settlement by its idempotency key.
     * <p>
     * Used for preventing duplicate settlement execution.
     * If a settlement with this key already exists, it should be returned
     * instead of creating a new one.
     * </p>
     *
     * @param idempotencyKey The idempotency key to search for
     * @return Optional containing the settlement if found
     */
    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);
}
