package com.nosota.mwallet.tests;

import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import com.nosota.mwallet.api.request.DepositRequest;
import com.nosota.mwallet.api.request.WithdrawalRequest;
import com.nosota.mwallet.api.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Payment operations.
 *
 * <p>Tests verify payment scenarios:
 * <ul>
 *   <li>PAY-001: Simple transfer between wallets</li>
 *   <li>PAY-002: Hold → Settlement (two-phase payment)</li>
 *   <li>PAY-003: Hold → Cancel (cancellation before settlement)</li>
 *   <li>PAY-004: Hold → Release (dispute resolution)</li>
 *   <li>PAY-005: Deposit (external funds entering system)</li>
 *   <li>PAY-006: Withdrawal (funds leaving system)</li>
 * </ul>
 */
@DisplayName("5. Payment Tests")
public class PaymentTest extends TestBase {

    @Test
    @DisplayName("PAY-001: Простой перевод между кошельками")
    void testSimpleTransfer() throws Exception {
        // Arrange: Create BUYER_1 with 100,000 and MERCHANT_1 with 0
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        // Act: Transfer 25,000 from BUYER_1 to MERCHANT_1
        MvcResult transferResult = mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyerWalletId.toString())
                        .param("recipientId", merchantWalletId.toString())
                        .param("amount", "25000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String transferResponseJson = transferResult.getResponse().getContentAsString();
        TransferResponse transferResponse = objectMapper.readValue(transferResponseJson, TransferResponse.class);
        UUID referenceId = transferResponse.referenceId();

        // Assert: Verify balances
        Long buyerBalance = walletBalanceService.getAvailableBalance(buyerWalletId);
        Long merchantBalance = walletBalanceService.getAvailableBalance(merchantWalletId);

        assertThat(buyerBalance).isEqualTo(75000L);
        assertThat(merchantBalance).isEqualTo(25000L);

        // Assert: Verify transaction group status
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String statusJson = statusResult.getResponse().getContentAsString();
        GroupStatusResponse statusResponse = objectMapper.readValue(statusJson, GroupStatusResponse.class);

        assertThat(statusResponse.status()).isEqualTo("SETTLED");

        // Assert: Verify zero-sum
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationJson = reconciliationResult.getResponse().getContentAsString();
        ReconciliationResponse reconciliation = objectMapper.readValue(reconciliationJson, ReconciliationResponse.class);

        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }

    @Test
    @DisplayName("PAY-002: Hold → Settlement")
    void testHoldAndSettlement() throws Exception {
        // Arrange: Create BUYER_1 with 100,000, MERCHANT_1 with 0
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        // Step 1: Create transaction group
        MvcResult groupResult = mockMvc.perform(post("/api/v1/ledger/groups"))
                .andExpect(status().isCreated())
                .andReturn();

        String groupJson = groupResult.getResponse().getContentAsString();
        TransactionGroupResponse groupResponse = objectMapper.readValue(groupJson, TransactionGroupResponse.class);
        UUID referenceId = groupResponse.referenceId();

        // Step 2: Hold 20,000 from BUYER_1 (hold debit)
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "20000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        // Step 3: Hold credit to MERCHANT_1
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", merchantWalletId)
                        .param("amount", "20000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        // Assert: After Hold - check balances
        Long buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        Long merchantAvailable = walletBalanceService.getAvailableBalance(merchantWalletId);

        assertThat(buyerAvailable).isEqualTo(80000L); // Available decreased (hold blocks 20000)
        assertThat(merchantAvailable).isEqualTo(0L); // Merchant still has 0 (hold not settled yet)

        // Step 4: Settle the transaction group
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/settle", referenceId))
                .andExpect(status().isOk());

        // Assert: After Settle - check balances
        buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        merchantAvailable = walletBalanceService.getAvailableBalance(merchantWalletId);

        assertThat(buyerAvailable).isEqualTo(80000L); // Buyer lost 20000
        assertThat(merchantAvailable).isEqualTo(20000L); // Merchant received 20000

        // Assert: Verify transaction group status
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String statusJson = statusResult.getResponse().getContentAsString();
        GroupStatusResponse statusResponse = objectMapper.readValue(statusJson, GroupStatusResponse.class);

        assertThat(statusResponse.status()).isEqualTo("SETTLED");

        // Assert: Verify zero-sum
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Verify transactions (2 HOLD + 2 SETTLED)
        MvcResult txResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();
        List<TransactionDTO> txList = objectMapper.readValue(
                txResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class));

        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.HOLD).count()).isEqualTo(4);
        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.SETTLED).count()).isEqualTo(4);
        assertThat(txList).hasSize(8);
    }

    @Test
    @DisplayName("PAY-003: Hold → Cancel")
    void testHoldAndCancel() throws Exception {
        // Arrange: Create BUYER_1 with 100,000
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        // Step 1: Create transaction group
        MvcResult groupResult = mockMvc.perform(post("/api/v1/ledger/groups"))
                .andExpect(status().isCreated())
                .andReturn();

        String groupJson = groupResult.getResponse().getContentAsString();
        TransactionGroupResponse groupResponse = objectMapper.readValue(groupJson, TransactionGroupResponse.class);
        UUID referenceId = groupResponse.referenceId();

        // Step 2: Hold 15,000 from BUYER_1
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "15000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", merchantWalletId)
                        .param("amount", "15000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        // Assert: After Hold - check balances
        Long buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);

        assertThat(buyerAvailable).isEqualTo(85000L); // Available decreased (hold blocks 15000)

        // Step 3: Cancel the transaction group
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/cancel", referenceId)
                        .param("reason", "Test cancellation"))
                .andExpect(status().isOk());

        // Assert: After Cancel - balance fully restored
        buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);

        assertThat(buyerAvailable).isEqualTo(100000L); // Available fully restored

        // Assert: Verify transaction group status
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String statusJson = statusResult.getResponse().getContentAsString();
        GroupStatusResponse statusResponse = objectMapper.readValue(statusJson, GroupStatusResponse.class);

        assertThat(statusResponse.status()).isEqualTo("CANCELLED");

        // Assert: Verify zero-sum
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Verify transactions (2 HOLD + 2 CANCELLED)
        MvcResult txResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();
        List<TransactionDTO> txList = objectMapper.readValue(
                txResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class));

        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.HOLD).count()).isEqualTo(4);
        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.CANCELLED).count()).isEqualTo(4);
        assertThat(txList).hasSize(8);
    }

    @Test
    @DisplayName("PAY-004: Hold → Release")
    void testHoldAndRelease() throws Exception {
        // Arrange: Create BUYER_1 with 100,000
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        // Step 1: Create transaction group
        MvcResult groupResult = mockMvc.perform(post("/api/v1/ledger/groups"))
                .andExpect(status().isCreated())
                .andReturn();

        String groupJson = groupResult.getResponse().getContentAsString();
        TransactionGroupResponse groupResponse = objectMapper.readValue(groupJson, TransactionGroupResponse.class);
        UUID referenceId = groupResponse.referenceId();

        // Step 2: Hold 15,000 from BUYER_1
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "15000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", merchantWalletId)
                        .param("amount", "15000")
                        .param("referenceId", referenceId.toString()))
                .andExpect(status().isCreated());

        // Assert: After Hold - check balances
        Long buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);

        assertThat(buyerAvailable).isEqualTo(85000L); // Available decreased (hold blocks 15000)

        // Step 3: Release the transaction group (dispute resolved)
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/release", referenceId)
                        .param("reason", "Dispute resolved in buyer's favor"))
                .andExpect(status().isOk());

        // Assert: After Release - balance restored
        buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);

        assertThat(buyerAvailable).isEqualTo(100000L); // Available fully restored

        // Assert: Verify transaction group status
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String statusJson = statusResult.getResponse().getContentAsString();
        GroupStatusResponse statusResponse = objectMapper.readValue(statusJson, GroupStatusResponse.class);

        assertThat(statusResponse.status()).isEqualTo("RELEASED");

        // Assert: Verify zero-sum
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Verify transactions (2 HOLD + 2 RELEASED)
        MvcResult txResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();
        List<TransactionDTO> txList = objectMapper.readValue(
                txResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class));

        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.HOLD).count()).isEqualTo(4);
        assertThat(txList.stream().filter(t -> t.getStatus() == TransactionStatus.RELEASED).count()).isEqualTo(4);
        assertThat(txList).hasSize(8);
    }

    @Test
    @DisplayName("PAY-005: Deposit — пополнение кошелька")
    void testDeposit() throws Exception {
        // Arrange: Create NEW_USER with 0 balance
        Integer userWalletId = createUserWallet("NEW_USER");

        // Act: Deposit 50,000 to NEW_USER
        DepositRequest depositRequest = new DepositRequest(userWalletId, 50000L, "BANK_TX_12345");
        String requestJson = objectMapper.writeValueAsString(depositRequest);

        MvcResult depositResult = mockMvc.perform(post("/api/v1/payment/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String depositResponseJson = depositResult.getResponse().getContentAsString();
        DepositResponse depositResponse = objectMapper.readValue(depositResponseJson, DepositResponse.class);

        // Assert: Verify response
        assertThat(depositResponse.referenceId()).isNotNull();
        assertThat(depositResponse.walletId()).isEqualTo(userWalletId);
        assertThat(depositResponse.amount()).isEqualTo(50000L);
        assertThat(depositResponse.status()).isEqualTo("COMPLETED");

        // Assert: Verify zero-sum (sufficient to validate system integrity)
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationJson = reconciliationResult.getResponse().getContentAsString();
        ReconciliationResponse reconciliation = objectMapper.readValue(reconciliationJson, ReconciliationResponse.class);

        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Verify balance after deposit
        Long userBalance = walletBalanceService.getAvailableBalance(userWalletId);
        assertThat(userBalance).isEqualTo(50000L);

        // Assert: Verify transactions
        UUID referenceId = depositResponse.referenceId();
        MvcResult transactionsResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String transactionsJson = transactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> transactions = objectMapper.readValue(
                transactionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        // Find SETTLED transactions
        List<TransactionDTO> settledTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SETTLED)
                .toList();

        // Should have 2 SETTLED transactions: DEPOSIT wallet DEBIT, User wallet CREDIT
        // (Direct transfer without ESCROW - no HOLD phase needed for deposit)
        assertThat(settledTransactions).hasSize(2);

        // Find debit from DEPOSIT wallet (the wallet that is NOT userWalletId)
        TransactionDTO depositDebit = settledTransactions.stream()
                .filter(t -> !t.getWalletId().equals(userWalletId))
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBIT transaction not found for DEPOSIT wallet"));

        // Find credit to user wallet
        TransactionDTO userCredit = settledTransactions.stream()
                .filter(t -> t.getWalletId().equals(userWalletId))
                .filter(t -> t.getType() == TransactionType.CREDIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREDIT transaction not found for USER wallet"));

        assertThat(depositDebit.getAmount()).isEqualTo(-50000L);
        assertThat(userCredit.getAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("PAY-006: Withdrawal — вывод средств")
    void testWithdrawal() throws Exception {
        // Arrange: Create MERCHANT_1 with 30,000 balance
        Integer merchantWalletId = createMerchantWalletWithBalance("MERCHANT_1", 30000L);

        // Act: Withdraw 20,000 from MERCHANT_1
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(
                merchantWalletId,
                20000L,
                "BANK_ACCOUNT_12345"
        );
        String requestJson = objectMapper.writeValueAsString(withdrawalRequest);

        MvcResult withdrawalResult = mockMvc.perform(post("/api/v1/payment/withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String withdrawalResponseJson = withdrawalResult.getResponse().getContentAsString();
        WithdrawalResponse withdrawalResponse = objectMapper.readValue(withdrawalResponseJson, WithdrawalResponse.class);

        // Assert: Verify response
        assertThat(withdrawalResponse.referenceId()).isNotNull();
        assertThat(withdrawalResponse.walletId()).isEqualTo(merchantWalletId);
        assertThat(withdrawalResponse.amount()).isEqualTo(20000L);
        assertThat(withdrawalResponse.status()).isEqualTo("COMPLETED");

        // Assert: Verify zero-sum (sufficient to validate system integrity)
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationJson = reconciliationResult.getResponse().getContentAsString();
        ReconciliationResponse reconciliation = objectMapper.readValue(reconciliationJson, ReconciliationResponse.class);

        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Verify balance after withdrawal
        Long merchantBalance = walletBalanceService.getAvailableBalance(merchantWalletId);
        assertThat(merchantBalance).isEqualTo(10000L);

        // Assert: Verify transactions
        UUID referenceId = withdrawalResponse.referenceId();
        MvcResult transactionsResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String transactionsJson = transactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> transactions = objectMapper.readValue(
                transactionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        // Find SETTLED transactions
        List<TransactionDTO> settledTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SETTLED)
                .toList();

        // Should have 2 SETTLED transactions: Merchant wallet DEBIT, WITHDRAWAL wallet CREDIT
        // (Direct transfer without ESCROW - no HOLD phase needed for withdrawal)
        assertThat(settledTransactions).hasSize(2);

        // Find debit from merchant wallet
        TransactionDTO merchantDebit = settledTransactions.stream()
                .filter(t -> t.getWalletId().equals(merchantWalletId))
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBIT transaction not found for MERCHANT wallet"));

        // Find credit to withdrawal wallet (the wallet that is NOT merchantWalletId)
        TransactionDTO withdrawalCredit = settledTransactions.stream()
                .filter(t -> !t.getWalletId().equals(merchantWalletId))
                .filter(t -> t.getType() == TransactionType.CREDIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREDIT transaction not found for WITHDRAWAL wallet"));

        assertThat(merchantDebit.getAmount()).isEqualTo(-20000L);
        assertThat(withdrawalCredit.getAmount()).isEqualTo(20000L);
    }
}
