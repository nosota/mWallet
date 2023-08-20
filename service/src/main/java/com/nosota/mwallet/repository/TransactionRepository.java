package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByWalletIdAndStatus(Integer walletId, TransactionStatus status);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.walletId = :walletId AND t.status = :status AND (t.transactionDate > :date OR :date IS NULL)")
    Long findTotalAmountByWalletAndStatusAndDateAfter(Integer walletId, TransactionStatus status, @Nullable LocalDateTime date);
}
