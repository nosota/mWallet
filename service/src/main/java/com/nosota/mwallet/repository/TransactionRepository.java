package com.nosota.mwallet.repository;
import com.nosota.mwallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId")
    Long sumByWalletId(@Param("walletId") Integer walletId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.wallet.id = :walletId AND t.transactionDate > :afterDate")
    Long sumByWalletIdAndTimestampAfter(@Param("walletId") Integer walletId, @Param("afterDate") LocalDateTime afterDate);
}
