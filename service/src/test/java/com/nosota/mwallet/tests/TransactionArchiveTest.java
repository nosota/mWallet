package com.nosota.mwallet.tests;

import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.dto.TransactionHistoryDTO;
import com.nosota.mwallet.model.WalletType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionArchiveTest extends TestBase {
    private Integer wallet1Id;
    private Integer wallet2Id;

    private static Long INITIAL_BALANCE = 10000L;

    public void setup() throws Exception {
        // Create initial wallets
        wallet1Id = createUserWalletWithBalance("TransactionArchiveTest.setup", INITIAL_BALANCE);
        wallet2Id = createSystemWalletWithBalance("TransactionArchiveTest.setup", INITIAL_BALANCE);

        transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 100L);
        transactionService.transferBetweenTwoWallets(wallet2Id, wallet1Id, 100L);

        transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 200L);
        transactionService.transferBetweenTwoWallets(wallet2Id, wallet1Id, 200L);

        transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 300L);
        transactionService.transferBetweenTwoWallets(wallet2Id, wallet1Id, 300L);

        transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 400L);
        transactionService.transferBetweenTwoWallets(wallet2Id, wallet1Id, 400L);

        transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 500L);
        transactionService.transferBetweenTwoWallets(wallet2Id, wallet1Id, 500L);
    }

    @Test
    public void testCaptureDailySnapshotForWallet() throws Exception {
        setup();

        // Record initial balances and transaction histories
        Long initialWallet1Balance = walletBalanceService.getAvailableBalance(wallet1Id);
        Long initialWallet2Balance = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(initialWallet1Balance, INITIAL_BALANCE);
        assertEquals(initialWallet2Balance, INITIAL_BALANCE);

        List<TransactionHistoryDTO> initialWallet1History = transactionHistoryService.getFullTransactionHistory(wallet1Id);
        List<TransactionHistoryDTO> initialWallet2History = transactionHistoryService.getFullTransactionHistory(wallet2Id);

        // Capture the snapshot
        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);

        // Archive transactions
        LocalDateTime olderThan = LocalDateTime.now();
        transactionSnapshotService.archiveOldSnapshots(wallet1Id, olderThan);
        transactionSnapshotService.archiveOldSnapshots(wallet2Id, olderThan);

        // Verify that the balances remain the same after the snapshot
        Long afterSnapshotWallet1Balance = walletBalanceService.getAvailableBalance(wallet1Id);
        Long afterSnapshotWallet2Balance = walletBalanceService.getAvailableBalance(wallet2Id);

        assertEquals(afterSnapshotWallet1Balance, INITIAL_BALANCE);
        assertEquals(afterSnapshotWallet2Balance, INITIAL_BALANCE);

        assertEquals(initialWallet1Balance, afterSnapshotWallet1Balance);
        assertEquals(initialWallet2Balance, afterSnapshotWallet2Balance);

        // Verify that the transaction histories remain the same after the snapshot
        List<TransactionHistoryDTO> afterSnapshotWallet1History = transactionHistoryService.getFullTransactionHistory(wallet1Id);
        List<TransactionHistoryDTO> afterSnapshotWallet2History = transactionHistoryService.getFullTransactionHistory(wallet2Id);

        assertTrue(areTransactionHistoriesEqual(initialWallet1History, afterSnapshotWallet1History));
        assertTrue(areTransactionHistoriesEqual(initialWallet2History, afterSnapshotWallet2History));
    }

    private boolean areTransactionHistoriesEqual(List<TransactionHistoryDTO> history1, List<TransactionHistoryDTO> history2) {
        if (history1.size() != history2.size()) {
            return false;
        }

        for (int i = 0; i < history1.size(); i++) {
            TransactionHistoryDTO trans1 = history1.get(i);
            TransactionHistoryDTO trans2 = history2.get(i);

            if (!trans1.equals(trans2)) {
                return false;
            }
        }

        return true;
    }
}
