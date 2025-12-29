package com.nosota.mwallet.api;

import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.response.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Ledger API interface for tier2 internal business service.
 *
 * <p>Defines REST endpoints for low-level ledger operations:
 * <ul>
 *   <li>Wallet operations (hold debit/credit, settle, release, cancel)</li>
 *   <li>Transaction group management (create, settle, release, cancel)</li>
 *   <li>Transfer operations (wallet-to-wallet transfers)</li>
 *   <li>Query operations (balance, status, transaction history)</li>
 * </ul>
 *
 * <p><b>Note:</b> High-level payment operations (settlement, refund) are in {@link PaymentApi}
 *
 * <p>This interface is implemented by:
 * <ul>
 *   <li>LedgerController - in service module (server-side implementation)</li>
 *   <li>LedgerClient - in api module (WebClient-based client for consumers)</li>
 * </ul>
 */
@RequestMapping("/api/v1/ledger")
public interface LedgerApi {

    // ==================== Wallet Hold Operations ====================

    /**
     * Holds (blocks) funds from a wallet for debit operation.
     *
     * @param walletId    Wallet ID to debit from
     * @param amount      Amount to hold (positive)
     * @param referenceId Transaction group UUID
     * @return Created transaction response
     */
    @PostMapping("/wallets/{walletId}/hold-debit")
    ResponseEntity<TransactionResponse> holdDebit(
            @PathVariable("walletId") Integer walletId,
            @RequestParam("amount") @Positive Long amount,
            @RequestParam("referenceId") @NotNull UUID referenceId) throws Exception;

    /**
     * Holds (prepares) funds for a wallet for credit operation.
     *
     * @param walletId    Wallet ID to credit to
     * @param amount      Amount to hold (positive)
     * @param referenceId Transaction group UUID
     * @return Created transaction response
     */
    @PostMapping("/wallets/{walletId}/hold-credit")
    ResponseEntity<TransactionResponse> holdCredit(
            @PathVariable("walletId") Integer walletId,
            @RequestParam("amount") @Positive Long amount,
            @RequestParam("referenceId") @NotNull UUID referenceId) throws Exception;

    /**
     * Settles (finalizes) a held transaction for a wallet.
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created SETTLED transaction response
     */
    @PostMapping("/wallets/{walletId}/settle")
    ResponseEntity<TransactionResponse> settle(
            @PathVariable("walletId") Integer walletId,
            @RequestParam("referenceId") @NotNull UUID referenceId) throws Exception;

    /**
     * Releases (returns) held funds for a wallet after dispute.
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created RELEASED transaction response
     */
    @PostMapping("/wallets/{walletId}/release")
    ResponseEntity<TransactionResponse> release(
            @PathVariable("walletId") Integer walletId,
            @RequestParam("referenceId") @NotNull UUID referenceId) throws Exception;

    /**
     * Cancels held funds for a wallet before conditions met.
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created CANCELLED transaction response
     */
    @PostMapping("/wallets/{walletId}/cancel")
    ResponseEntity<TransactionResponse> cancel(
            @PathVariable("walletId") Integer walletId,
            @RequestParam("referenceId") @NotNull UUID referenceId) throws Exception;

    // ==================== Transaction Group Operations ====================

    /**
     * Creates a new transaction group.
     *
     * @param idempotencyKey Optional idempotency key for duplicate prevention.
     *                       If provided, duplicate requests with the same key will return
     *                       the existing transaction group instead of creating a new one.
     * @return Created transaction group response
     */
    @PostMapping("/groups")
    ResponseEntity<TransactionGroupResponse> createTransactionGroup(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey);

    /**
     * Settles (finalizes) a transaction group successfully.
     *
     * @param referenceId Transaction group UUID
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/settle")
    ResponseEntity<TransactionGroupResponse> settleTransactionGroup(
            @PathVariable("referenceId") UUID referenceId) throws Exception;

    /**
     * Releases (returns funds) for a transaction group after dispute.
     *
     * @param referenceId Transaction group UUID
     * @param reason      Reason for release
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/release")
    ResponseEntity<TransactionGroupResponse> releaseTransactionGroup(
            @PathVariable("referenceId") UUID referenceId,
            @RequestParam("reason") @NotEmpty String reason) throws Exception;

    /**
     * Cancels a transaction group before conditions met.
     *
     * @param referenceId Transaction group UUID
     * @param reason      Reason for cancellation
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/cancel")
    ResponseEntity<TransactionGroupResponse> cancelTransactionGroup(
            @PathVariable("referenceId") UUID referenceId,
            @RequestParam("reason") @NotEmpty String reason) throws Exception;

    // ==================== High-Level Operations ====================

    /**
     * Transfers funds between two wallets.
     *
     * @param senderId    Sender wallet ID
     * @param recipientId Recipient wallet ID
     * @param amount      Amount to transfer
     * @return Transfer response
     */
    @PostMapping("/transfer")
    ResponseEntity<TransferResponse> transfer(
            @RequestParam("senderId") @NotNull Integer senderId,
            @RequestParam("recipientId") @NotNull Integer recipientId,
            @RequestParam("amount") @Positive Long amount) throws Exception;

    // ==================== Query Operations ====================

    /**
     * Gets available balance for a wallet.
     *
     * @param walletId Wallet ID
     * @return Balance response
     */
    @GetMapping("/wallets/{walletId}/balance")
    ResponseEntity<BalanceResponse> getBalance(
            @PathVariable("walletId") Integer walletId);

    /**
     * Gets status of a transaction group.
     *
     * @param referenceId Transaction group UUID
     * @return Group status response
     */
    @GetMapping("/groups/{referenceId}/status")
    ResponseEntity<GroupStatusResponse> getGroupStatus(
            @PathVariable("referenceId") UUID referenceId);

    /**
     * Gets all transactions for a transaction group.
     *
     * @param referenceId Transaction group UUID
     * @return List of transactions
     */
    @GetMapping("/groups/{referenceId}/transactions")
    ResponseEntity<List<TransactionDTO>> getGroupTransactions(
            @PathVariable("referenceId") UUID referenceId);
}
