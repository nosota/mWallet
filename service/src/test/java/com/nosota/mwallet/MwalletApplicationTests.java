package com.nosota.mwallet;
import static org.assertj.core.api.Assertions.*;
import java.text.MessageFormat;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletManagementService;
import com.nosota.mwallet.service.WalletService;
import com.nosota.mwallet.service.WalletTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

@SpringBootTest
class MwalletApplicationTests {

    @Autowired
    private WalletManagementService walletManagementService;

    @Autowired
    private WalletBalanceService walletBalanceService;

    @Autowired
    private WalletTransactionService walletTransactionService;

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

        UUID refId = walletTransactionService.transferBetweenTwoWallets(wallet1Id, wallet2Id, 10L);

        balance1 = walletBalanceService.getAvailableBalance(wallet1Id);
        balance2 = walletBalanceService.getAvailableBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        TransactionGroupStatus trxStatus = walletTransactionService.getStatusForReferenceId(refId);
        assertThat(trxStatus).isEqualTo(TransactionGroupStatus.CONFIRMED);

        List<Transaction> transactionList = walletTransactionService.getTransactionsByReferenceId(refId);
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
//        Long balance1 = walletBalanceService.getAvailableBalance(1);
//        assertThat(balance1).isEqualTo(0L);
//
//        Long balance2 = walletBalanceService.getAvailableBalance(2);
//        assertThat(balance2).isEqualTo(10L);
//    }
}
