package com.nosota.mwallet.tests;

import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionGroupZeroingOutException;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.model.WalletType;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BasicTests extends TestBase {
    @Test
    void createWallets() {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "createWallets",10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "createWallets");

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
    }

    @Test
    void transferMoney2Positive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney2Positive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney2Positive");

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);

        UUID refId = transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 10L);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        TransactionGroupStatus trxStatus = transactionService.getStatusForReferenceId(refId);
        assertThat(trxStatus).isEqualTo(TransactionGroupStatus.CONFIRMED);

        List<TransactionDTO> transactionList = transactionService.getTransactionsByReferenceId(refId);
        transactionList.forEach(item -> {
            String formatted = MessageFormat.format("Wallet: {0}, Transaction: {2}, ReferenceId: {3}, Status: {4}, Amount: {1} ",
                    item.getWalletId(), item.getAmount(),
                    item.getType(), item.getReferenceId(),
                    item.getStatus());
            System.out.println(formatted);
        });
    }

    @Test
    void transferMoney3Negative() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Negative", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3Negative");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Negative", 1L);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        Long balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = transactionService.createTransactionGroup();

        try {
            walletService.hold(wallet1Id, 9L, referenceId);
            balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
            assertThat(balance1).isEqualTo(1L);

            walletService.reserve(wallet2Id, 4L, referenceId);
            balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
            assertThat(balance2).isEqualTo(0L);

            walletService.reserve(wallet3Id, 5L, referenceId);
            balance3 = walletBalanceService.getAvailableBalance(wallet3Id);
            assertThat(balance3).isEqualTo(1L);

            walletService.hold(wallet1Id, 2L, referenceId); // expected exception InsufficientFundsException
        } catch (InsufficientFundsException ex) {
            transactionService.rejectTransactionGroup(referenceId, ex.getMessage());
        }

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);
    }

    @Test
    void transferMoney3Positive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Positive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3Positive");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Positive", 1L);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        Long balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = transactionService.createTransactionGroup();

        walletService.hold(wallet1Id, 10L, referenceId);
        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        walletService.reserve(wallet2Id, 5L, referenceId);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        walletService.reserve(wallet3Id, 5L, referenceId);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        transactionService.confirmTransactionGroup(referenceId);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }

    @Test
    void transferMoney3ReconciliationError() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3ReconciliationError", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3ReconciliationError");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3ReconciliationError", 1L);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        Long balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = transactionService.createTransactionGroup();

        try {
            walletService.hold(wallet1Id, 10L, referenceId);
            balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
            assertThat(balance1).isEqualTo(0L);

            walletService.reserve(wallet2Id, 5L, referenceId);
            balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
            assertThat(balance2).isEqualTo(0L);

            walletService.reserve(wallet3Id, 2L, referenceId);
            balance3 = walletBalanceService.getAvailableBalance(wallet3Id);
            assertThat(balance3).isEqualTo(1L);

            transactionService.confirmTransactionGroup(referenceId);
        } catch (TransactionGroupZeroingOutException e) {
            transactionService.rejectTransactionGroup(referenceId, e.getMessage());
        }

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);
    }

    @Test
    void transferMoneyAndSnapshot() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoneyAndSnapshot", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoneyAndSnapshot");

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);

        UUID refId = transactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 10L);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        TransactionGroupStatus trxStatus = transactionService.getStatusForReferenceId(refId);
        assertThat(trxStatus).isEqualTo(TransactionGroupStatus.CONFIRMED);

        List<TransactionDTO> transactionList = transactionService.getTransactionsByReferenceId(refId);
        transactionList.forEach(item -> {
            String formatted = MessageFormat.format("Wallet: {0}, Transaction: {2}, ReferenceId: {3}, Status: {4}, Amount: {1} ",
                    item.getWalletId(), item.getAmount(),
                    item.getType(), item.getReferenceId(),
                    item.getStatus());
            System.out.println(formatted);
        });

        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        transactionList = transactionService.getTransactionsByReferenceId(refId);
        transactionList.forEach(item -> {
            String formatted = MessageFormat.format("Wallet: {0}, Transaction: {2}, ReferenceId: {3}, Status: {4}, Amount: {1} ",
                    item.getWalletId(), item.getAmount(),
                    item.getType(), item.getReferenceId(),
                    item.getStatus());
            System.out.println(formatted);
        });
    }

    @Test
    void transferMoney3PositiveAndSnapshot() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshot", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3PositiveAndSnapshot");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshot", 1L);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        Long balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = transactionService.createTransactionGroup();

        walletService.hold(wallet1Id, 10L, referenceId);
        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        walletService.reserve(wallet2Id, 5L, referenceId);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        walletService.reserve(wallet3Id, 5L, referenceId);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        transactionService.confirmTransactionGroup(referenceId);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet3Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }

    @Test
    void transferMoney3PositiveAndSnapshotAndArchive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive", 1L);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        Long balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = transactionService.createTransactionGroup();

        walletService.hold(wallet1Id, 10L, referenceId);
        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        walletService.reserve(wallet2Id, 5L, referenceId);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        walletService.reserve(wallet3Id, 5L, referenceId);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        transactionService.confirmTransactionGroup(referenceId);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet3Id);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.archiveOldSnapshots(wallet1Id, LocalDateTime.now());
        transactionSnapshotService.archiveOldSnapshots(wallet2Id, LocalDateTime.now());
        transactionSnapshotService.archiveOldSnapshots(wallet3Id, LocalDateTime.now());

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);
        balance3 = walletBalanceService.getAvailableBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }
}
