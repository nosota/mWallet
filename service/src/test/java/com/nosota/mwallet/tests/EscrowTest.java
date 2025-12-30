package com.nosota.mwallet.tests;

import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.response.GroupStatusResponse;
import com.nosota.mwallet.api.response.ReconciliationResponse;
import com.nosota.mwallet.api.response.TransactionGroupResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Escrow workflows.
 *
 * <p>Tests cover the full lifecycle of escrow operations:
 * <ul>
 *   <li>ESC-001: Full cycle BUYER → ESCROW → MERCHANT</li>
 *   <li>ESC-002: Cancel before settle (BUYER → ESCROW → Cancel)</li>
 *   <li>ESC-003: Release before settle (dispute resolved for buyer)</li>
 *   <li>ESC-004: Return after settle on ESCROW (ESCROW → BUYER)</li>
 *   <li>ESC-005: Full cycle with REFUND (BUYER → ESCROW → MERCHANT → BUYER)</li>
 * </ul>
 */
@DisplayName("Escrow Tests")
public class EscrowTest extends TestBase {
    // Note: Helper methods (createTransactionGroup, holdAndSettle, getEscrowWalletId)
    // are inherited from TestBase and reusable across all test classes

    @Test
    @DisplayName("ESC-001: Полный цикл Escrow (BUYER → ESCROW → MERCHANT)")
    void testEscrowFullCycle() throws Exception {
        // Arrange: Create BUYER with 100,000 and MERCHANT with 0
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");
        Integer escrowWalletId = getEscrowWalletId();

        // Initial balances
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(100000L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(0L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(0L);

        // Step 1: Group 1 - BUYER → ESCROW (hold + settle)
        UUID group1Id = createTransactionGroup();
        holdAndSettle(buyerWalletId, escrowWalletId, 10000L, group1Id);

        // Assert after step 1: BUYER lost 10,000, ESCROW gained 10,000
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(90000L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(10000L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(0L);

        // Verify group 1 status
        MvcResult statusResult1 = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", group1Id))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status1 = objectMapper.readValue(
                statusResult1.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status1.status()).isEqualTo("SETTLED");

        // Step 2: Group 2 - ESCROW → MERCHANT (hold + settle)
        UUID group2Id = createTransactionGroup();
        holdAndSettle(escrowWalletId, merchantWalletId, 10000L, group2Id);

        // Assert final balances: BUYER 90k, ESCROW 0, MERCHANT 10k
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(90000L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(0L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(10000L);

        // Verify group 2 status
        MvcResult statusResult2 = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", group2Id))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status2 = objectMapper.readValue(
                statusResult2.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status2.status()).isEqualTo("SETTLED");

        // Assert: Zero-sum maintained
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ESC-002: Escrow с отменой (Cancel до settle)")
    void testEscrowCancelBeforeSettle() throws Exception {
        // Arrange: Create BUYER with 100,000
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer escrowWalletId = getEscrowWalletId();

        // Step 1: Create group and hold BUYER → ESCROW
        UUID groupId = createTransactionGroup();

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "10000")
                        .param("referenceId", groupId.toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", escrowWalletId)
                        .param("amount", "10000")
                        .param("referenceId", groupId.toString()))
                .andExpect(status().isCreated());

        // Assert: buyer available balance decreased (10k held)
        Long buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerAvailable).isEqualTo(90000L);

        // Step 2: Cancel (before settle!)
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/cancel", groupId)
                        .param("reason", "Buyer changed mind"))
                .andExpect(status().isOk());

        // Assert: buyer balance fully restored
        buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerAvailable).isEqualTo(100000L);

        // Assert: ESCROW balance still 0
        Long escrowBalance = walletBalanceService.getAvailableBalance(escrowWalletId);
        assertThat(escrowBalance).isEqualTo(0L);

        // Assert: group status = CANCELLED
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", groupId))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status = objectMapper.readValue(
                statusResult.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status.status()).isEqualTo("CANCELLED");

        // Assert: transactions include HOLD + CANCELLED
        MvcResult txResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", groupId))
                .andExpect(status().isOk())
                .andReturn();
        List<TransactionDTO> transactions = objectMapper.readValue(
                txResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class));

        long holdCount = transactions.stream().filter(t -> t.getStatus() == TransactionStatus.HOLD).count();
        long cancelledCount = transactions.stream().filter(t -> t.getStatus() == TransactionStatus.CANCELLED).count();
        assertThat(holdCount).isEqualTo(4); // 2 hold-debit + 2 hold-credit (via ESCROW)
        assertThat(cancelledCount).isEqualTo(4); // 4 reversal transactions
        assertThat(transactions).hasSize(8); // 4 HOLD + 4 CANCELLED

        // Assert: Zero-sum maintained
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ESC-003: Escrow с Release (диспут до settle)")
    void testEscrowReleaseBeforeSettle() throws Exception {
        // Arrange: Create BUYER with 100,000
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer escrowWalletId = getEscrowWalletId();

        // Step 1: Create group and hold BUYER → ESCROW
        UUID groupId = createTransactionGroup();

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", buyerWalletId)
                        .param("amount", "10000")
                        .param("referenceId", groupId.toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", escrowWalletId)
                        .param("amount", "10000")
                        .param("referenceId", groupId.toString()))
                .andExpect(status().isCreated());

        // Assert: buyer available balance decreased (10k held)
        Long buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerAvailable).isEqualTo(90000L);

        // Step 2: Dispute opened (external process, not in ledger)
        // Step 3: Release (dispute resolved in buyer's favor)
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/release", groupId)
                        .param("reason", "Dispute resolved for buyer"))
                .andExpect(status().isOk());

        // Assert: buyer balance fully restored
        buyerAvailable = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerAvailable).isEqualTo(100000L);

        // Assert: ESCROW balance still 0
        Long escrowBalance = walletBalanceService.getAvailableBalance(escrowWalletId);
        assertThat(escrowBalance).isEqualTo(0L);

        // Assert: group status = RELEASED
        MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", groupId))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status = objectMapper.readValue(
                statusResult.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status.status()).isEqualTo("RELEASED");

        // Assert: transactions include HOLD + RELEASED
        MvcResult txResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", groupId))
                .andExpect(status().isOk())
                .andReturn();
        List<TransactionDTO> transactions = objectMapper.readValue(
                txResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionDTO.class));

        long holdCount = transactions.stream().filter(t -> t.getStatus() == TransactionStatus.HOLD).count();
        long releasedCount = transactions.stream().filter(t -> t.getStatus() == TransactionStatus.RELEASED).count();
        assertThat(holdCount).isEqualTo(4); // 2 hold-debit + 2 hold-credit (via ESCROW)
        assertThat(releasedCount).isEqualTo(4); // 4 reversal transactions
        assertThat(transactions).hasSize(8); // 4 HOLD + 4 RELEASED

        // Assert: Zero-sum maintained
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ESC-004: Возврат после settle на ESCROW")
    void testEscrowReturnAfterSettle() throws Exception {
        // Arrange: Create BUYER with 100,000
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer escrowWalletId = getEscrowWalletId();

        // Step 1: Group 1 - Hold BUYER → ESCROW + Settle
        UUID group1Id = createTransactionGroup();
        holdAndSettle(buyerWalletId, escrowWalletId, 10000L, group1Id);

        // Assert: ESCROW has 10,000
        Long escrowBalance = walletBalanceService.getAvailableBalance(escrowWalletId);
        assertThat(escrowBalance).isEqualTo(10000L);

        // Assert: BUYER has 90,000
        Long buyerBalance = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerBalance).isEqualTo(90000L);

        // Verify group 1 is SETTLED
        MvcResult statusResult1 = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", group1Id))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status1 = objectMapper.readValue(
                statusResult1.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status1.status()).isEqualTo("SETTLED");

        // Step 2: Decision to return - NEW group for ESCROW → BUYER
        UUID group2Id = createTransactionGroup();
        holdAndSettle(escrowWalletId, buyerWalletId, 10000L, group2Id);

        // Assert: buyer balance restored to 100,000
        buyerBalance = walletBalanceService.getAvailableBalance(buyerWalletId);
        assertThat(buyerBalance).isEqualTo(100000L);

        // Assert: ESCROW balance back to 0
        escrowBalance = walletBalanceService.getAvailableBalance(escrowWalletId);
        assertThat(escrowBalance).isEqualTo(0L);

        // Verify group 2 is SETTLED
        MvcResult statusResult2 = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", group2Id))
                .andExpect(status().isOk())
                .andReturn();
        GroupStatusResponse status2 = objectMapper.readValue(
                statusResult2.getResponse().getContentAsString(),
                GroupStatusResponse.class);
        assertThat(status2.status()).isEqualTo("SETTLED");

        // Assert: 2 separate groups, both SETTLED
        assertThat(group1Id).isNotEqualTo(group2Id);

        // Assert: Zero-sum maintained
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ESC-005: Полный цикл до MERCHANT с REFUND")
    void testEscrowFullCycleWithRefund() throws Exception {
        // Arrange: Create BUYER with 100,000 and MERCHANT with 0
        Integer buyerWalletId = createUserWalletWithBalance("BUYER_1", 100000L);
        Integer merchantWalletId = createMerchantWallet("MERCHANT_1");
        Integer escrowWalletId = getEscrowWalletId();

        // Initial balances
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(100000L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(0L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(0L);

        // Step 1: Group 1 - BUYER → ESCROW
        UUID group1Id = createTransactionGroup();
        holdAndSettle(buyerWalletId, escrowWalletId, 10000L, group1Id);

        // Assert: BUYER 90k, ESCROW 10k
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(90000L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(10000L);

        // Step 2: Group 2 - ESCROW → MERCHANT
        UUID group2Id = createTransactionGroup();
        holdAndSettle(escrowWalletId, merchantWalletId, 10000L, group2Id);

        // Assert: BUYER 90k, ESCROW 0, MERCHANT 10k
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(90000L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(0L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(10000L);

        // Step 3: REFUND - MERCHANT → BUYER (new group)
        // NOTE: In real system, this would go through RefundService with fee deduction
        // For this test, we simulate direct refund without fee
        UUID group3Id = createTransactionGroup();
        holdAndSettle(merchantWalletId, buyerWalletId, 10000L, group3Id);

        // Assert final balances: BUYER restored to 100k, MERCHANT back to 0
        assertThat(walletBalanceService.getAvailableBalance(buyerWalletId)).isEqualTo(100000L);
        assertThat(walletBalanceService.getAvailableBalance(merchantWalletId)).isEqualTo(0L);
        assertThat(walletBalanceService.getAvailableBalance(escrowWalletId)).isEqualTo(0L);

        // Assert: 3 separate groups, all SETTLED
        assertThat(group1Id).isNotEqualTo(group2Id);
        assertThat(group2Id).isNotEqualTo(group3Id);
        assertThat(group1Id).isNotEqualTo(group3Id);

        // Verify all groups are SETTLED
        for (UUID groupId : List.of(group1Id, group2Id, group3Id)) {
            MvcResult statusResult = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", groupId))
                    .andExpect(status().isOk())
                    .andReturn();
            GroupStatusResponse status = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(),
                    GroupStatusResponse.class);
            assertThat(status.status()).isEqualTo("SETTLED");
        }

        // Assert: Zero-sum maintained
        MvcResult reconciliationResult = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                .andExpect(status().isOk())
                .andReturn();
        ReconciliationResponse reconciliation = objectMapper.readValue(
                reconciliationResult.getResponse().getContentAsString(),
                ReconciliationResponse.class);
        assertThat(reconciliation.totalSum()).isEqualTo(0L);
    }
}
