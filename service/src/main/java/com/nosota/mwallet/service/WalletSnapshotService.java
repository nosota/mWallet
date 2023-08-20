package com.nosota.mwallet.service;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletBalanceRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WalletSnapshotService {

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // Snapshot Balance =
    //    Latest Snapshot Balance
    //  + Confirmed Transactions since last Snapshot
    //  + Rejected Transactions since last Snapshot
    @Transactional
    public WalletBalance captureSnapshotForWallet(Wallet wallet) {
        WalletBalance lastSnapshot = walletBalanceRepository.findLatestSnapshotByWallet(wallet);
        Long lastSnapshotBalance = (lastSnapshot != null) ? lastSnapshot.getBalance() : 0L;

        // Calculate sum of confirmed transactions after the last snapshot
        LocalDateTime lastSnapshotDate = (lastSnapshot != null) ? lastSnapshot.getSnapshotDate() : null;
        Long postSnapshotConfirmedAmount = transactionRepository.findTotalAmountByWalletAndStatusAndDateAfter(wallet.getId(), TransactionStatus.CONFIRMED, lastSnapshotDate);
        Long postSnapshotRejectedAmount = transactionRepository.findTotalAmountByWalletAndStatusAndDateAfter(wallet.getId(), TransactionStatus.REJECTED, lastSnapshotDate);

        // Calculate new snapshot balance
        Long newSnapshotBalance = lastSnapshotBalance
                + (postSnapshotConfirmedAmount != null ? postSnapshotConfirmedAmount : 0L)
                + (postSnapshotRejectedAmount != null ? postSnapshotRejectedAmount : 0L);

        WalletBalance snapshot = new WalletBalance();
        snapshot.setWallet(wallet);
        snapshot.setBalance(newSnapshotBalance);
        snapshot.setSnapshotDate(LocalDateTime.now());

        return snapshot;
    }
}
