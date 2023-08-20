package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByWalletIdAndStatus(Integer walletId, TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.referenceId = :referenceId AND (t.status = :status1 OR t.status = :status2)")
    Optional<Transaction> findByWalletIdAndReferenceIdAndStatuses(@Param("walletId") Integer walletId, @Param("referenceId") UUID referenceId, @Param("status1") TransactionStatus status1, @Param("status2") TransactionStatus status2);}
