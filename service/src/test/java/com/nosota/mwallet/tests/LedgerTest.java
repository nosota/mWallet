package com.nosota.mwallet.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import com.nosota.mwallet.api.response.ReconciliationResponse;
import com.nosota.mwallet.api.response.TransferResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Ledger operations.
 *
 * <p>Tests verify double-entry accounting principles:
 * <ul>
 *   <li>LED-001: Double-entry bookkeeping (debit + credit = 0)</li>
 *   <li>LED-002: Immutability (no UPDATE/DELETE operations)</li>
 *   <li>LED-003: Reversal pattern (cancel creates new transactions)</li>
 *   <li>LED-004: Zero-sum reconciliation (system total = 0)</li>
 * </ul>
 */
@DisplayName("3. Ledger Tests")
public class LedgerTest extends TestBase {

    @Test
    @DisplayName("LED-001: Создание транзакции с double-entry")
    void testDoubleEntry() throws Exception {
        // Arrange: Create two wallets
        // BUYER_1 with 100,000, MERCHANT_1 with 0
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        // Act: Transfer 10,000 from BUYER_1 to MERCHANT_1
        MvcResult transferResult = mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyerWalletId.toString())
                        .param("recipientId", merchantWalletId.toString())
                        .param("amount", "10000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String transferResponseJson = transferResult.getResponse().getContentAsString();
        TransferResponse transferResponse = objectMapper.readValue(transferResponseJson, TransferResponse.class);
        UUID referenceId = transferResponse.referenceId();

        // Assert: Get transactions for this group
        MvcResult transactionsResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String transactionsJson = transactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> transactions = objectMapper.readValue(
                transactionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        // Filter SETTLED transactions (final state)
        List<TransactionDTO> settledTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SETTLED)
                .toList();

        // Verify: At least 2 transactions (debit + credit)
        assertThat(settledTransactions).hasSizeGreaterThanOrEqualTo(2);

        // Find debit and credit transactions
        TransactionDTO debitTx = settledTransactions.stream()
                .filter(t -> t.getWalletId().equals(buyerWalletId))
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBIT transaction not found for BUYER"));

        TransactionDTO creditTx = settledTransactions.stream()
                .filter(t -> t.getWalletId().equals(merchantWalletId))
                .filter(t -> t.getType() == TransactionType.CREDIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREDIT transaction not found for MERCHANT"));

        // Verify double-entry bookkeeping
        assertThat(debitTx.getAmount()).isEqualTo(-10000L);
        assertThat(creditTx.getAmount()).isEqualTo(10000L);
        assertThat(debitTx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(creditTx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(debitTx.getReferenceId()).isEqualTo(creditTx.getReferenceId());

        // Verify zero-sum for this group
        long sum = settledTransactions.stream()
                .mapToLong(TransactionDTO::getAmount)
                .sum();
        assertThat(sum).isEqualTo(0L);

        // Verify final balances
        mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", buyerWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(90000L));

        mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", merchantWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(10000L));
    }

    @Test
    @DisplayName("LED-002: Immutability — запрет изменения транзакций через API")
    void testImmutability() throws Exception {
        // Arrange: Create transaction
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");

        MvcResult transferResult = mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyerWalletId.toString())
                        .param("recipientId", merchantWalletId.toString())
                        .param("amount", "10000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String transferResponseJson = transferResult.getResponse().getContentAsString();
        TransferResponse transferResponse = objectMapper.readValue(transferResponseJson, TransferResponse.class);
        UUID referenceId = transferResponse.referenceId();

        // Get transactions
        MvcResult transactionsResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String transactionsJson = transactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> transactions = objectMapper.readValue(
                transactionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        Integer firstTransactionId = transactions.get(0).getId();

        // Assert 1: Verify PUT endpoint does not exist (should NOT succeed with 2xx status)
        MvcResult putResult = mockMvc.perform(put("/api/v1/ledger/groups/{referenceId}/transactions/{transactionId}", referenceId, firstTransactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 99999}"))
                .andReturn();
        assertThat(putResult.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(putResult.getResponse().getStatus()).isNotEqualTo(201);

        // Assert 2: Verify PATCH endpoint does not exist (should NOT succeed with 2xx status)
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/ledger/groups/{referenceId}/transactions/{transactionId}", referenceId, firstTransactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andReturn();
        assertThat(patchResult.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(patchResult.getResponse().getStatus()).isNotEqualTo(201);

        // Assert 3: Verify DELETE endpoint does not exist (should NOT succeed with 2xx status)
        MvcResult deleteResult = mockMvc.perform(delete("/api/v1/ledger/groups/{referenceId}/transactions/{transactionId}", referenceId, firstTransactionId))
                .andReturn();
        assertThat(deleteResult.getResponse().getStatus()).isNotEqualTo(200);
        assertThat(deleteResult.getResponse().getStatus()).isNotEqualTo(204);

        // Assert 4: Verify transaction still exists with original data (immutability)
        MvcResult verifyResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId))
                .andExpect(status().isOk())
                .andReturn();

        String verifyJson = verifyResult.getResponse().getContentAsString();
        List<TransactionDTO> verifyTransactions = objectMapper.readValue(
                verifyJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        // Transaction data remains unchanged
        TransactionDTO originalTx = transactions.stream()
                .filter(t -> t.getId().equals(firstTransactionId))
                .findFirst()
                .orElseThrow();

        TransactionDTO verifyTx = verifyTransactions.stream()
                .filter(t -> t.getId().equals(firstTransactionId))
                .findFirst()
                .orElseThrow();

        assertThat(verifyTx).isEqualTo(originalTx);
    }

    @Test
    @DisplayName("LED-003: Reversal вместо удаления")
    void testReversal() throws Exception {
        // Arrange: Create wallet and hold funds
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);

        // Step 1: Create transaction group and hold
        MvcResult groupResult = mockMvc.perform(post("/api/v1/ledger/groups"))
                .andExpect(status().isCreated())
                .andReturn();

        String groupJson = groupResult.getResponse().getContentAsString();
        String referenceIdString = objectMapper.readTree(groupJson).get("referenceId").asText();
        UUID groupUuid = UUID.fromString(referenceIdString);

        // Step 2: Hold 10,000
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "10000")
                        .param("referenceId", groupUuid.toString()))
                .andExpect(status().isCreated());

        // Get transactions after HOLD
        MvcResult holdTransactionsResult = mockMvc.perform(
                        get("/api/v1/ledger/groups/{referenceId}/transactions", groupUuid))
                .andExpect(status().isOk())
                .andReturn();

        String holdJson = holdTransactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> holdTransactions = objectMapper.readValue(
                holdJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        int transactionsAfterHold = holdTransactions.size();
        assertThat(transactionsAfterHold).isGreaterThanOrEqualTo(2); // At least 2: DEBIT from buyer + CREDIT to escrow

        // Assert: Verify ESCROW wallet participation (double-entry bookkeeping)
        // holdDebit should create 2 transactions:
        // 1. DEBIT from buyer wallet (-10,000)
        // 2. CREDIT to ESCROW wallet (+10,000)

        // Find DEBIT transaction from buyer
        TransactionDTO buyerDebitTx = holdTransactions.stream()
                .filter(t -> t.getWalletId().equals(buyerWalletId))
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBIT HOLD transaction not found for BUYER wallet"));

        assertThat(buyerDebitTx.getAmount()).isEqualTo(-10000L);

        // Find CREDIT transaction to ESCROW (walletId will be the ESCROW wallet ID)
        TransactionDTO escrowCreditTx = holdTransactions.stream()
                .filter(t -> !t.getWalletId().equals(buyerWalletId)) // Different wallet (ESCROW)
                .filter(t -> t.getType() == TransactionType.CREDIT)
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CREDIT HOLD transaction not found for ESCROW wallet"));

        assertThat(escrowCreditTx.getAmount()).isEqualTo(10000L);

        // Verify both transactions have the same referenceId
        assertThat(buyerDebitTx.getReferenceId()).isEqualTo(escrowCreditTx.getReferenceId());

        // Verify zero-sum for HOLD transactions
        long holdSum = holdTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .mapToLong(TransactionDTO::getAmount)
                .sum();
        assertThat(holdSum).isEqualTo(0L);

        // Step 3: Cancel the hold
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/cancel", groupUuid)
                        .param("reason", "Test cancellation"))
                .andExpect(status().isOk());

        // Get transactions after CANCEL
        MvcResult cancelTransactionsResult = mockMvc.perform(
                        get("/api/v1/ledger/groups/{referenceId}/transactions", groupUuid))
                .andExpect(status().isOk())
                .andReturn();

        String cancelJson = cancelTransactionsResult.getResponse().getContentAsString();
        List<TransactionDTO> cancelTransactions = objectMapper.readValue(
                cancelJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class)
        );

        // Assert: Original HOLD transactions still exist
        long holdCount = cancelTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .count();
        assertThat(holdCount).isGreaterThanOrEqualTo(1);

        // Assert: New CANCELLED transactions created
        long cancelledCount = cancelTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.CANCELLED)
                .count();
        assertThat(cancelledCount).isGreaterThanOrEqualTo(1);

        // Assert: Total transactions increased (reversal pattern)
        assertThat(cancelTransactions.size()).isGreaterThan(transactionsAfterHold);

        // Assert: Zero-sum maintained
        long totalSum = cancelTransactions.stream()
                .mapToLong(TransactionDTO::getAmount)
                .sum();
        assertThat(totalSum).isEqualTo(0L);

        // Verify balance restored
        mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", buyerWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(100000L));
    }

    @Test
    @DisplayName("LED-004: Zero-Sum Reconciliation всей системы")
    void testZeroSumReconciliation() throws Exception {
        // Arrange: Perform multiple operations

        // Operation 1: Deposit (BUYER_1 gets 100,000)
        Integer buyer1WalletId = createUserWalletWithBalance("BUYER_1", 100000L);

        // Assert: Verify DEPOSIT wallet participation (initial balance creation)
        // createUserWalletWithBalance creates initial balance using DEPOSIT wallet.
        // This should create 2 transactions with proper double-entry bookkeeping:
        // 1. DEBIT from DEPOSIT system wallet (-100,000) - money from external world
        // 2. CREDIT to buyer wallet (+100,000) - money received
        // This ensures zero-sum: DEPOSIT=-100,000, BUYER=+100,000, Total=0
        //
        // Note: We verify this indirectly through reconciliation and zero-sum checks,
        // as there's no API endpoint to query transactions by wallet ID.
        // The DEPOSIT wallet behavior is tested through the overall system reconciliation.

        // Operation 2: Transfer (BUYER_1 → MERCHANT_1: 25,000)
        Integer merchant1WalletId = createMerchantWallet("MERCHANT_1");
        mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyer1WalletId.toString())
                        .param("recipientId", merchant1WalletId.toString())
                        .param("amount", "25000"))
                .andExpect(status().isCreated());

        // Operation 3: Another transfer (BUYER_1 → MERCHANT_1: 15,000)
        mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyer1WalletId.toString())
                        .param("recipientId", merchant1WalletId.toString())
                        .param("amount", "15000"))
                .andExpect(status().isCreated());

        // Operation 4: Create another buyer with deposit
        Integer buyer2WalletId = createUserWalletWithBalance("BUYER_2", 50000L);

        // Operation 5: Transfer from BUYER_2 to MERCHANT_1
        mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", buyer2WalletId.toString())
                        .param("recipientId", merchant1WalletId.toString())
                        .param("amount", "10000"))
                .andExpect(status().isCreated());

        // Act: Get reconciliation statistics
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationJson = reconciliationResult.getResponse().getContentAsString();
        ReconciliationResponse reconciliation = objectMapper.readValue(reconciliationJson, ReconciliationResponse.class);

        // Assert: Total sum must be 0 (zero-sum principle)
        assertThat(reconciliation.totalSum()).isEqualTo(0L);

        // Assert: Sum by each status should also be 0 (double-entry for each status)
        assertThat(reconciliation.settledSum()).isEqualTo(0L);

        // Assert: If there are any HOLD transactions, they should sum to 0
        if (reconciliation.holdSum() != null) {
            assertThat(reconciliation.holdSum()).isEqualTo(0L);
        }

        // Verify that sumByStatus map contains expected keys
        assertThat(reconciliation.sumByStatus()).containsKey("totalSum");
        assertThat(reconciliation.sumByStatus()).containsKey("settledSum");
        assertThat(reconciliation.sumByStatus().get("totalSum")).isEqualTo(0L);
    }
}
