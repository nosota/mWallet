package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.RefundReserve;
import com.nosota.mwallet.model.RefundReserveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundReserveRepository extends JpaRepository<RefundReserve, UUID> {

    /**
     * Finds reserve by settlement ID.
     * Each settlement has at most one reserve.
     *
     * @param settlementId The settlement ID
     * @return Optional containing the reserve if found
     */
    Optional<RefundReserve> findBySettlementId(UUID settlementId);

    /**
     * Finds all active reserves for a merchant.
     * Active means status in (ACTIVE, PARTIALLY_USED).
     *
     * @param merchantId The merchant ID
     * @return List of active reserves
     */
    @Query("SELECT r FROM RefundReserve r " +
           "WHERE r.merchantId = :merchantId " +
           "AND r.status IN ('ACTIVE', 'PARTIALLY_USED') " +
           "ORDER BY r.expiresAt ASC")
    List<RefundReserve> findActiveReservesByMerchantId(@Param("merchantId") Long merchantId);

    /**
     * Finds all expired reserves that need to be released.
     * Expired means: status in (ACTIVE, PARTIALLY_USED) and expiresAt < now.
     *
     * @param now Current timestamp
     * @return List of expired reserves
     */
    @Query("SELECT r FROM RefundReserve r " +
           "WHERE r.status IN ('ACTIVE', 'PARTIALLY_USED') " +
           "AND r.expiresAt < :now " +
           "ORDER BY r.expiresAt ASC")
    List<RefundReserve> findExpiredReserves(@Param("now") LocalDateTime now);

    /**
     * Calculates total available reserve amount for a merchant.
     * Sum of availableAmount for all active reserves.
     *
     * @param merchantId The merchant ID
     * @return Total available amount in cents (0 if no active reserves)
     */
    @Query("SELECT COALESCE(SUM(r.availableAmount), 0) " +
           "FROM RefundReserve r " +
           "WHERE r.merchantId = :merchantId " +
           "AND r.status IN ('ACTIVE', 'PARTIALLY_USED')")
    Long calculateTotalAvailableReserve(@Param("merchantId") Long merchantId);

    /**
     * Finds reserves by status.
     *
     * @param status The reserve status
     * @return List of reserves with given status
     */
    List<RefundReserve> findByStatus(RefundReserveStatus status);
}
