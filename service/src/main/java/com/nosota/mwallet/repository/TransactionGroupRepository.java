package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionGroupRepository extends JpaRepository<TransactionGroup, UUID> {
    List<TransactionGroup> findAllByStatusIn(List<TransactionGroupStatus> statuses);

    /**
     * Finds a transaction group by its idempotency key.
     * Used for duplicate detection during transaction group creation.
     *
     * @param idempotencyKey The idempotency key to search for
     * @return Optional containing the transaction group if found, empty otherwise
     */
    Optional<TransactionGroup> findByIdempotencyKey(String idempotencyKey);
}
