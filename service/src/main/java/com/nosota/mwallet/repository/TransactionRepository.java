package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    // This method fetches all transactions with a given status
    List<Transaction> findAllByStatus(TransactionStatus status);

    // This method fetches all transactions that have a referenceId in the given list of reference IDs
    List<Transaction> findAllByReferenceIdIn(List<UUID> referenceIds);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.referenceId = :referenceId AND (t.status = :status1 OR t.status = :status2)")
    Optional<Transaction> findByWalletIdAndReferenceIdAndStatuses(@Param("walletId") Integer walletId, @Param("referenceId") UUID referenceId, @Param("status1") TransactionStatus status1, @Param("status2") TransactionStatus status2);

    List<Transaction> findByReferenceId(UUID referenceId);
}
