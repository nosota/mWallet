package com.nosota.mwallet;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletManagementService;
import com.nosota.mwallet.service.TransactionService;
import com.nosota.mwallet.service.TransactionSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MwalletApplicationTests {

    @Autowired
    private WalletManagementService walletManagementService;

    @Autowired
    private WalletBalanceService walletBalanceService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionSnapshotService transactionSnapshotService;

    @Test
    void contextLoads() {
    }

    @Test
    void createWallets() {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER);

        Long balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        Long balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
    }

    @Test
    void transferMoney() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER);

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
    void transferMoneyAndSnapshot() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER);

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

        transactionSnapshotService.captureDailySnapshot();

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

//    @Test
//    void investigation () {
//        walletSnapshotService.captureDailySnapshot();
//        Long balance1 = walletBalanceService.getAvailableBalance(1);
//        Long balance2 = walletBalanceService.getAvailableBalance(2);
//
//        assertThat(balance1).isEqualTo(0L);
//        assertThat(balance2).isEqualTo(10L);
//    }
}
