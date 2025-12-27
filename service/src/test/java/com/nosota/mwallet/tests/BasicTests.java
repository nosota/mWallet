package com.nosota.mwallet.tests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.model.WalletType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ledger operations via LedgerController.
 * <p>
 * Tests follow banking ledger standards with:
 * <ul>
 *   <li>Double-entry accounting (debit + credit = 0)</li>
 *   <li>Two-phase commit (hold → settle)</li>
 *   <li>Immutable transactions</li>
 *   <li>Proper status transitions (HOLD → SETTLED/RELEASED/CANCELLED)</li>
 * </ul>
 */
class BasicTests extends TestBase {

    @Test
    void createWallets() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "createWallets", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "createWallets");

        // Get balance via REST API
        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
    }

    @Test
    void transferMoney2Positive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney2Positive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney2Positive");

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);

        // Transfer via REST API
        UUID refId = transfer(wallet1Id, wallet2Id, 10L);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        // Check status via REST API
        TransactionGroupStatus trxStatus = getGroupStatus(refId);
        assertThat(trxStatus).isEqualTo(TransactionGroupStatus.SETTLED);

        // Get transactions via REST API
        List<TransactionDTO> transactionList = getGroupTransactions(refId);
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

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);
        Long balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        // Create transaction group via REST API
        UUID referenceId = createTransactionGroup();

        // Hold debit via REST API
        holdDebit(wallet1Id, 9L, referenceId);
        balance1 = getBalance(wallet1Id);
        assertThat(balance1).isEqualTo(1L);

        // Hold credit via REST API
        holdCredit(wallet2Id, 4L, referenceId);
        balance2 = getBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        holdCredit(wallet3Id, 5L, referenceId);
        balance3 = getBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        // This should fail with insufficient funds - expect 400 Bad Request
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", wallet1Id)
                        .param("amount", "2")
                        .param("referenceId", referenceId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Cancel transaction group via REST API
        cancelTransactionGroup(referenceId, "Insufficient funds");

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);
    }

    @Test
    void transferMoney3Positive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Positive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3Positive");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3Positive", 1L);

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);
        Long balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = createTransactionGroup();

        holdDebit(wallet1Id, 10L, referenceId);
        balance1 = getBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        holdCredit(wallet2Id, 5L, referenceId);
        balance2 = getBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        holdCredit(wallet3Id, 5L, referenceId);
        balance3 = getBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        // Settle transaction group via REST API
        settleTransactionGroup(referenceId);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }

    @Test
    void transferMoney3ReconciliationError() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3ReconciliationError", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3ReconciliationError");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3ReconciliationError", 1L);

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);
        Long balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = createTransactionGroup();

        holdDebit(wallet1Id, 10L, referenceId);
        balance1 = getBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        holdCredit(wallet2Id, 5L, referenceId);
        balance2 = getBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        holdCredit(wallet3Id, 2L, referenceId);
        balance3 = getBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        // This should fail with reconciliation error (10 != 5 + 2) - expect 400 Bad Request
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/settle", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Cancel transaction group via REST API
        cancelTransactionGroup(referenceId, "Reconciliation error");

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);
    }

    @Test
    void transferMoneyAndSnapshot() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoneyAndSnapshot", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoneyAndSnapshot");

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);

        UUID refId = transfer(wallet1Id, wallet2Id, 10L);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        TransactionGroupStatus trxStatus = getGroupStatus(refId);
        assertThat(trxStatus).isEqualTo(TransactionGroupStatus.SETTLED);

        List<TransactionDTO> transactionList = getGroupTransactions(refId);
        transactionList.forEach(item -> {
            String formatted = MessageFormat.format("Wallet: {0}, Transaction: {2}, ReferenceId: {3}, Status: {4}, Amount: {1} ",
                    item.getWalletId(), item.getAmount(),
                    item.getType(), item.getReferenceId(),
                    item.getStatus());
            System.out.println(formatted);
        });

        // Snapshot operations remain via service (no REST endpoint)
        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(10L);

        transactionList = getGroupTransactions(refId);
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

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);
        Long balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = createTransactionGroup();

        holdDebit(wallet1Id, 10L, referenceId);
        balance1 = getBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        holdCredit(wallet2Id, 5L, referenceId);
        balance2 = getBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        holdCredit(wallet3Id, 5L, referenceId);
        balance3 = getBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        settleTransactionGroup(referenceId);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet3Id);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }

    @Test
    void transferMoney3PositiveAndSnapshotAndArchive() throws Exception {
        Integer wallet1Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive", 10L);
        Integer wallet2Id = walletManagementService.createNewWallet(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive");
        Integer wallet3Id = walletManagementService.createNewWalletWithBalance(WalletType.USER, "transferMoney3PositiveAndSnapshotAndArchive", 1L);

        Long balance1 = getBalance(wallet1Id);
        Long balance2 = getBalance(wallet2Id);
        Long balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(10L);
        assertThat(balance2).isEqualTo(0L);
        assertThat(balance3).isEqualTo(1L);

        UUID referenceId = createTransactionGroup();

        holdDebit(wallet1Id, 10L, referenceId);
        balance1 = getBalance(wallet1Id);
        assertThat(balance1).isEqualTo(0L);

        holdCredit(wallet2Id, 5L, referenceId);
        balance2 = getBalance(wallet2Id);
        assertThat(balance2).isEqualTo(0L);

        holdCredit(wallet3Id, 5L, referenceId);
        balance3 = getBalance(wallet3Id);
        assertThat(balance3).isEqualTo(1L);

        settleTransactionGroup(referenceId);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.captureDailySnapshotForWallet(wallet1Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet2Id);
        transactionSnapshotService.captureDailySnapshotForWallet(wallet3Id);

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);

        transactionSnapshotService.archiveOldSnapshots(wallet1Id, LocalDateTime.now());
        transactionSnapshotService.archiveOldSnapshots(wallet2Id, LocalDateTime.now());
        transactionSnapshotService.archiveOldSnapshots(wallet3Id, LocalDateTime.now());

        balance1 = getBalance(wallet1Id);
        balance2 = getBalance(wallet2Id);
        balance3 = getBalance(wallet3Id);

        assertThat(balance1).isEqualTo(0L);
        assertThat(balance2).isEqualTo(5L);
        assertThat(balance3).isEqualTo(6L);
    }

    // ==================== Helper Methods for REST API Calls ====================

    private Long getBalance(Integer walletId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", walletId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        return ((Number) response.get("availableBalance")).longValue();
    }

    private UUID transfer(Integer senderId, Integer recipientId, Long amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ledger/transfer")
                        .param("senderId", senderId.toString())
                        .param("recipientId", recipientId.toString())
                        .param("amount", amount.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        return UUID.fromString((String) response.get("referenceId"));
    }

    private TransactionGroupStatus getGroupStatus(UUID referenceId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/status", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        return TransactionGroupStatus.valueOf((String) response.get("status"));
    }

    private List<TransactionDTO> getGroupTransactions(UUID referenceId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ledger/groups/{referenceId}/transactions", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});
    }

    private UUID createTransactionGroup() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ledger/groups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        return UUID.fromString((String) response.get("referenceId"));
    }

    private void holdDebit(Integer walletId, Long amount, UUID referenceId) throws Exception {
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-debit", walletId)
                        .param("amount", amount.toString())
                        .param("referenceId", referenceId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    private void holdCredit(Integer walletId, Long amount, UUID referenceId) throws Exception {
        mockMvc.perform(post("/api/v1/ledger/wallets/{walletId}/hold-credit", walletId)
                        .param("amount", amount.toString())
                        .param("referenceId", referenceId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    private void settleTransactionGroup(UUID referenceId) throws Exception {
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/settle", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void cancelTransactionGroup(UUID referenceId, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/ledger/groups/{referenceId}/cancel", referenceId)
                        .param("reason", reason)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
