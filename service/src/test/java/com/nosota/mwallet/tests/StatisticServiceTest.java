package com.nosota.mwallet.tests;

import com.nosota.mwallet.MwalletApplication;
import com.nosota.mwallet.container.PostgresContainer;
import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.service.TransactionService;
import com.nosota.mwallet.service.TransactionStatisticService;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletManagementService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = MwalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.discovery.enabled=false"}
)
@Testcontainers
@ActiveProfiles({"test"})
public class StatisticServiceTest {
    @Autowired
    private WalletBalanceService walletBalanceService;

    @Autowired
    private WalletManagementService walletManagementService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionStatisticService transactionStatisticService;

    private static Long INITIAL_BALANCE = 10000L;

    @BeforeAll
    public static void startPostgresContainer() {
        PostgresContainer.getInstance();
    }

    @Test
    public void dailyStat() throws Exception {
        // Create initial wallets
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "dailyStat", INITIAL_BALANCE);
        Integer wallet2Id = walletManagementService.createNewWalletWithBalance(WalletType.SYSTEM, "dailyStat", INITIAL_BALANCE);

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

        Long initialWallet1Balance = walletBalanceService.getAvailableBalance(wallet1Id);
        Long initialWallet2Balance = walletBalanceService.getAvailableBalance(wallet2Id);
        assertEquals(initialWallet1Balance, INITIAL_BALANCE);
        assertEquals(initialWallet2Balance, INITIAL_BALANCE);

        List<TransactionDTO> creditList1 = transactionStatisticService.getDailyCreditOperations(wallet1Id, LocalDateTime.now());
        assertEquals(creditList1.size(), 6); // +1 credit when the wallet was created
        for (int i = 1; i < creditList1.size(); ++i) { // check order of the transaction list
            Long expectedAmount = (i * 100L);
            Long realAmount = creditList1.get(i).getAmount();
            assertEquals(expectedAmount, realAmount);
        }

        List<TransactionDTO> debitList1 = transactionStatisticService.getDailyDebitOperations(wallet1Id, LocalDateTime.now());
        assertEquals(debitList1.size(), 5);
        for (int i = 0; i < debitList1.size(); ++i) { // check order of the transaction list
            Long expectedAmount = - ((i + 1) * 100L);
            Long realAmount = debitList1.get(i).getAmount();
            assertEquals(expectedAmount, realAmount);
        }

        List<TransactionDTO> creditList2 = transactionStatisticService.getDailyCreditOperations(wallet2Id, LocalDateTime.now());
        assertEquals(creditList2.size(), 6); // +1 credit when the wallet was created
        for (int i = 1; i < creditList2.size(); ++i) { // check order of the transaction list
            Long expectedAmount = (i * 100L);
            Long realAmount = creditList2.get(i).getAmount();
            assertEquals(expectedAmount, realAmount);
        }

        List<TransactionDTO> debitList2 = transactionStatisticService.getDailyDebitOperations(wallet2Id, LocalDateTime.now());
        assertEquals(debitList2.size(), 5);
        for (int i = 0; i < debitList2.size(); ++i) { // check order of the transaction list
            Long expectedAmount = - ((i + 1) * 100L);
            Long realAmount = debitList2.get(i).getAmount();
            assertEquals(expectedAmount, realAmount);
        }
    }
}
