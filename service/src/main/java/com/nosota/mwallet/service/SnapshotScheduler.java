package com.nosota.mwallet.service;

import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SnapshotScheduler {
    private static Logger LOG = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private WalletSnapshotService walletSnapshotService;

    @Autowired
    private WalletRepository walletRepository;

    // @Scheduled(cron = "0 0 0 * * ?")
    public void runSnapshotJob() {
        // TODO: Very basic implementation just for testing.
        List<Wallet> allWallets = walletRepository.findAll();
        for (Wallet wallet : allWallets) {
            try {
                walletSnapshotService.captureSnapshotForWallet(wallet);
            } catch (Exception e) {
                LOG.error("Error capturing snapshot for wallet: " + e.getMessage(), e);
            }
        }
    }
}
