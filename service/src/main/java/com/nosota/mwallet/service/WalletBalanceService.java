package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletBalance;
import com.nosota.mwallet.repository.WalletBalanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class WalletBalanceService {
    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    public void updateWalletBalance(WalletBalance walletBalance, Wallet wallet, Long balance, LocalDateTime snapshotDate) {
        walletBalance.setWallet(wallet);
        walletBalance.setBalance(balance);
        walletBalance.setSnapshotDate(snapshotDate);
        walletBalanceRepository.save(walletBalance);
    }
}
