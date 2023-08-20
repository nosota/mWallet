package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class WalletBalanceRepository{
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    public Long getConfirmedBalance(Wallet wallet) {
        return getBalanceForStatus(wallet, TransactionStatus.CONFIRMED);
    }

    public Long getHoldBalance(Wallet wallet) {
        return getBalanceForStatus(wallet, TransactionStatus.HOLD);
    }

    public Long getRejectedBalance(Wallet wallet) {
        return getBalanceForStatus(wallet, TransactionStatus.REJECTED);
    }

    public void save(WalletBalance walletBalance) {
        if (walletBalance.getId() == null) {
            // New entity
            entityManager.persist(walletBalance);
        } else {
            // Existing entity
            entityManager.merge(walletBalance);
        }
    }

    // Available Balance =
    //    Latest Snapshot Balance
    //  + Confirmed Transactions
    //  − Hold Transactions
    //  + Rejected Transactions
    public Long getAvailableBalance(Wallet wallet) {
        // Fetch the latest snapshot
        WalletBalance latestSnapshot = findLatestSnapshotByWallet(wallet);
        Long snapshotBalance = latestSnapshot != null ? latestSnapshot.getBalance() : 0L;

        // Get the sum of confirmed transactions after the snapshot date
        LocalDateTime snapshotDate = latestSnapshot != null ? latestSnapshot.getSnapshotDate() : null;
        Long postSnapshotConfirmedAmount = transactionRepository.findTotalAmountByWalletAndStatusAndDateAfter(wallet, TransactionStatus.CONFIRMED, snapshotDate);
        Long postSnapshotHeldAmount = transactionRepository.findTotalAmountByWalletAndStatusAndDateAfter(wallet, TransactionStatus.HOLD, snapshotDate);
        Long postSnapshotRejectedAmount = transactionRepository.findTotalAmountByWalletAndStatusAndDateAfter(wallet, TransactionStatus.REJECTED, snapshotDate);

        // Calculate the available balance
        return snapshotBalance
                + (postSnapshotConfirmedAmount != null ? postSnapshotConfirmedAmount : 0L)
                - (postSnapshotHeldAmount != null ? postSnapshotHeldAmount : 0L)
                + (postSnapshotRejectedAmount != null ? postSnapshotRejectedAmount : 0L);
    }

    public WalletBalance findLatestSnapshotByWallet(Wallet wallet) {
        String query = "SELECT wb FROM WalletBalance wb WHERE wb.wallet = :wallet ORDER BY wb.snapshotDate DESC";
        return entityManager.createQuery(query, WalletBalance.class)
                .setParameter("wallet", wallet)
                .setMaxResults(1)
                .getSingleResult();
    }

    private Long getBalanceForStatus(Wallet wallet, TransactionStatus status) {
        String query = "SELECT SUM(amount) FROM Transaction WHERE wallet = :wallet AND status = :status";
        Long result = (Long) entityManager.createQuery(query)
                .setParameter("wallet", wallet)
                .setParameter("status", status)
                .getSingleResult();

        return Optional.ofNullable(result).orElse(0L);
    }
}
