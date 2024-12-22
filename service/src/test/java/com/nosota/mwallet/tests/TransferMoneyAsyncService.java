package com.nosota.mwallet.tests;

import com.nosota.mwallet.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class TransferMoneyAsyncService {
    @Autowired
    private TransactionService transactionService;

    @Async("testTaskExecutor")
    public CompletableFuture<UUID> asyncTestMethodtransferBetweenTwoWallets(Integer wallet1Id, Integer wallet2Id, Long amount) {
        try {
            return CompletableFuture.completedFuture(
                    transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, amount));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
