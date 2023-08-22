package com.nosota.mwallet;

import com.nosota.mwallet.dto.PagedResponse;
import com.nosota.mwallet.dto.TransactionHistoryDTO;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionType;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.service.*;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestAsyncConfig.class)
public class TransactionHistoryTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionSnapshotService transactionSnapshotService;

    @Autowired
    private WalletBalanceService walletBalanceService;

    @Autowired
    private WalletManagementService walletManagementService;

    @Autowired
    private TransactionHistoryService transactionHistoryService;

    @Autowired
    private TransferMoneyAsyncService transferMoneyAsyncService;

    @Test
    public void testPaginatedHistory() throws Exception {
        // 1. Create wallets
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "testPaginatedHistory", 100L);
        Integer wallet2Id = walletManagementService.createNewWalletWithBalance(WalletType.SYSTEM, "testPaginatedHistory", 0L);

        // 2. Perform money transfer
        perform10transactions(wallet1Id, wallet2Id);

        // 3. Check balances
        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 90L);
        assertEquals(balance2, 10L);

        // 4. Move transactions for snapshot, balances must stay the same.
        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 90L);
        assertEquals(balance2, 10L);

        // 5. Perform next 10 transactions and check balances,
        // they should include balances from transaction and snapshot tables.
        perform10transactions(wallet1Id, wallet2Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 80L);
        assertEquals(balance2, 20L);

        // 6. Move transactions from snapshot to archive.
        // It should create ledger entries in snapshot table and balances stay the same.
        // We move 40 entries to archive + one more additional transaction if a wallet was created with initial balance.
        LocalDateTime olderThan = LocalDateTime.now();
        transactionSnapshotService.archiveOldSnapshots(wallet1Id, olderThan);
        transactionSnapshotService.archiveOldSnapshots(wallet2Id, olderThan);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 80L);
        assertEquals(balance2, 20L);

        // 7. Let's perform more 10 transactions.
        perform10transactions(wallet1Id, wallet2Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 70L);
        assertEquals(balance2, 30L);

        // 8. Move transactions for snapshot.
        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 70L);
        assertEquals(balance2, 30L);

        // 9. Perform more 10 transactions.
        perform10transactions(wallet1Id, wallet2Id);

        // 10. Now we have entries in transaction table, in snapshot table (with ledger entries) and in archive.
        // Let's check if balances are calculated correctly.
        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(balance1, 60L);
        assertEquals(balance2, 40L);

        // 11. We made 40 transactions.
        // Let's request a full history first.
        // It queries history from transaction table, snapshot and archive.
        // The history must not include any LEDGER records.
        List<TransactionHistoryDTO> fullHistory1 = transactionHistoryService.getFullTransactionHistory(wallet1Id);
        // 81 - 40 DEBITs consists of two history lines HOLD and CONFIRMED,
        //      and 1 CREDIT when create wallet.
        assertEquals(fullHistory1.size(), 81L);
        System.out.println("-- Full History Wallet 1 --");
        printAndValidateFullHistory(fullHistory1, wallet1Id, TransactionType.DEBIT);

        List<TransactionHistoryDTO> fullHistory2 = transactionHistoryService.getFullTransactionHistory(wallet2Id);
        // 81 - 40 CREDITs consists of two history lines RESERVE and CONFIRMED,
        //      and 1 CREDIT when create wallet.
        assertEquals(fullHistory1.size(), 81L);
        System.out.println("-- Full History Wallet 2 --");
        printAndValidateFullHistory(fullHistory2, wallet2Id, TransactionType.CREDIT);

        // 12. Test paginated history that returns recent history records excluding archived transactions.
        int pageNumber1 = 1;
        int pageSize1 = 2;
        PagedResponse<TransactionHistoryDTO> pages1 = transactionHistoryService.getPaginatedTransactionHistory(wallet1Id, pageNumber1, pageSize1);

        // We made 40 transactions that consist of 2 entries each for each wallet.
        // So each wallet has 80 entries in history.
        // 20 were moved to archive for each wallet.
        // Did we lose 1 CREDIT transaction that was used for initial balance of the first wallet? No, we didn't.
        // The balance was taken into account in ledger entry, but we don't see it in transaction list since the API doesn't use archive table.
        assertEquals(pages1.getTotalRecords(), 60L);

        System.out.println("-- Paginated History Wallet 1 --");
        printHistoryPage(pages1.getData()); // page #1
        for(int i = 2; i < pages1.getTotalPages(); ++i) {  // starts from page #2
            PagedResponse<TransactionHistoryDTO> pages1Next = transactionHistoryService.getPaginatedTransactionHistory(wallet1Id, i, pageSize1);
            printHistoryPage(pages1Next.getData());
        }

        int pageNumber2 = 1;
        int pageSize2 = 2;
        PagedResponse<TransactionHistoryDTO> pages2 = transactionHistoryService.getPaginatedTransactionHistory(wallet2Id, pageNumber2, pageSize2);

        // We made 40 transactions that consist of 2 entries each for each wallet.
        // So each wallet has 80 entries in history.
        // 20 were moved to archive for each wallet.
        assertEquals(pages2.getTotalRecords(), 60L);

        System.out.println("-- Paginated History Wallet 2 --");
        printHistoryPage(pages2.getData()); // page #1
        for(int i = 2; i < pages2.getTotalPages(); ++i) {  // starts from page #2
            PagedResponse<TransactionHistoryDTO> pages2Next = transactionHistoryService.getPaginatedTransactionHistory(wallet2Id, i, pageSize2);
            printHistoryPage(pages2Next.getData());
        }

    }

    private void perform10transactions(Integer wallet1Id, Integer wallet2Id) {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 1
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 2
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 3
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 4
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 5
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 6
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 7
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 8
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L), // 9
                transferMoneyAsyncService.asyncTestMethodtransferBetweenTwoWallets(wallet1Id, wallet2Id, 1L)  // 10
        );
        allOf.join(); // This will block until all futures are complete
    }

    private void printHistoryPage(List<TransactionHistoryDTO> page) {
        for (int i = 0; i < page.size(); ++i) {
            TransactionHistoryDTO item = page.get(i);

            System.out.println(
                    MessageFormat.format("{6, time, dd/MM/yyyy HH:mm:ss.SSS} -- Wallet: {0}, Transaction: {2}, Status: {4}, ReferenceId: {3}, Amount: {1} ",
                            item.getWalletId(),
                            item.getAmount(),
                            item.getType(),
                            item.getReferenceId(),
                            item.getStatus(),
                            item.getType(),
                            item.getTimestamp())
            );
        }
    }

    private void printAndValidateFullHistory(List<TransactionHistoryDTO> fullHistory, Integer walletId, TransactionType transactionType) {
        for (int i = 0; i < fullHistory.size(); ++i) {
            TransactionHistoryDTO item = fullHistory.get(i);

            assertEquals(item.getWalletId(), walletId);

            if (i == fullHistory.size() - 1) {
                // The first operation might be always CREDIT, when I create wallet with balance.
            } else {
                assertEquals(item.getType(), transactionType.name());
            }

            System.out.println(
                    MessageFormat.format("{6, time, dd/MM/yyyy HH:mm:ss.SSS} -- Wallet: {0}, Transaction: {2}, Status: {4}, ReferenceId: {3}, Amount: {1} ",
                            item.getWalletId(),
                            item.getAmount(),
                            item.getType(),
                            item.getReferenceId(),
                            item.getStatus(),
                            item.getType(),
                            item.getTimestamp())
            );
        }
    }
}
