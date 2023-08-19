package com.nosota.mwallet.repository;
import org.hibernate.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId")
    Double sumByWalletId(@Param("walletId") Long walletId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId AND t.timestamp > :afterDate")
    Double sumByWalletIdAndTimestampAfter(@Param("walletId") Long walletId, @Param("afterDate") LocalDateTime afterDate);
}
