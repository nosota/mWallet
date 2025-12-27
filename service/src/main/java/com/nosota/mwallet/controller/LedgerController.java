package com.nosota.mwallet.controller;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionGroupZeroingOutException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.service.TransactionService;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for ledger operations following banking standards.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Wallet operations (hold debit/credit, settle, release, cancel)</li>
 *   <li>Transaction group management (create, settle, release, cancel)</li>
 *   <li>High-level operations (transfers)</li>
 *   <li>Query operations (balance, status, history)</li>
 * </ul>
 *
 * <p>All endpoints follow REST best practices with proper HTTP status codes.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@Validated
@AllArgsConstructor
@Slf4j
public class LedgerController {

    private final WalletService walletService;
    private final WalletBalanceService walletBalanceService;
    private final TransactionService transactionService;

    // ==================== Wallet Hold Operations ====================

    /**
     * Holds (blocks) funds from a wallet for debit operation.
     *
     * <p>POST /api/v1/ledger/wallets/{walletId}/hold-debit
     *
     * @param walletId    Wallet ID to debit from
     * @param amount      Amount to hold (positive)
     * @param referenceId Transaction group UUID
     * @return Created transaction ID
     */
    @PostMapping("/wallets/{walletId}/hold-debit")
    public ResponseEntity<Map<String, Object>> holdDebit(
            @PathVariable Integer walletId,
            @RequestParam @Positive Long amount,
            @RequestParam @NotNull UUID referenceId) {

        try {
            Integer transactionId = walletService.holdDebit(walletId, amount, referenceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "transactionId", transactionId,
                    "walletId", walletId,
                    "amount", -amount,
                    "type", "DEBIT",
                    "status", "HOLD",
                    "referenceId", referenceId
            ));
        } catch (WalletNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Holds (prepares) funds for a wallet for credit operation.
     *
     * <p>POST /api/v1/ledger/wallets/{walletId}/hold-credit
     *
     * @param walletId    Wallet ID to credit to
     * @param amount      Amount to hold (positive)
     * @param referenceId Transaction group UUID
     * @return Created transaction ID
     */
    @PostMapping("/wallets/{walletId}/hold-credit")
    public ResponseEntity<Map<String, Object>> holdCredit(
            @PathVariable Integer walletId,
            @RequestParam @Positive Long amount,
            @RequestParam @NotNull UUID referenceId) {

        try {
            Integer transactionId = walletService.holdCredit(walletId, amount, referenceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "transactionId", transactionId,
                    "walletId", walletId,
                    "amount", amount,
                    "type", "CREDIT",
                    "status", "HOLD",
                    "referenceId", referenceId
            ));
        } catch (WalletNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Settles (finalizes) a held transaction for a wallet.
     *
     * <p>POST /api/v1/ledger/wallets/{walletId}/settle
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created SETTLED transaction ID
     */
    @PostMapping("/wallets/{walletId}/settle")
    public ResponseEntity<Map<String, Object>> settle(
            @PathVariable Integer walletId,
            @RequestParam @NotNull UUID referenceId) {

        try {
            Integer transactionId = walletService.settle(walletId, referenceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "transactionId", transactionId,
                    "walletId", walletId,
                    "status", "SETTLED",
                    "referenceId", referenceId
            ));
        } catch (TransactionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Releases (returns) held funds for a wallet after dispute.
     *
     * <p>POST /api/v1/ledger/wallets/{walletId}/release
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created RELEASED transaction ID
     */
    @PostMapping("/wallets/{walletId}/release")
    public ResponseEntity<Map<String, Object>> release(
            @PathVariable Integer walletId,
            @RequestParam @NotNull UUID referenceId) {

        try {
            Integer transactionId = walletService.release(walletId, referenceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "transactionId", transactionId,
                    "walletId", walletId,
                    "status", "RELEASED",
                    "referenceId", referenceId
            ));
        } catch (TransactionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancels held funds for a wallet before conditions met.
     *
     * <p>POST /api/v1/ledger/wallets/{walletId}/cancel
     *
     * @param walletId    Wallet ID
     * @param referenceId Transaction group UUID
     * @return Created CANCELLED transaction ID
     */
    @PostMapping("/wallets/{walletId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable Integer walletId,
            @RequestParam @NotNull UUID referenceId) {

        try {
            Integer transactionId = walletService.cancel(walletId, referenceId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "transactionId", transactionId,
                    "walletId", walletId,
                    "status", "CANCELLED",
                    "referenceId", referenceId
            ));
        } catch (TransactionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== Transaction Group Operations ====================

    /**
     * Creates a new transaction group.
     *
     * <p>POST /api/v1/ledger/groups
     *
     * @return Created transaction group UUID
     */
    @PostMapping("/groups")
    public ResponseEntity<Map<String, Object>> createTransactionGroup() {
        UUID referenceId = transactionService.createTransactionGroup();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "referenceId", referenceId,
                "status", "IN_PROGRESS"
        ));
    }

    /**
     * Settles (finalizes) a transaction group successfully.
     *
     * <p>POST /api/v1/ledger/groups/{referenceId}/settle
     *
     * @param referenceId Transaction group UUID
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/settle")
    public ResponseEntity<Map<String, Object>> settleTransactionGroup(
            @PathVariable UUID referenceId) {

        try {
            transactionService.settleTransactionGroup(referenceId);

            return ResponseEntity.ok(Map.of(
                    "referenceId", referenceId,
                    "status", "SETTLED",
                    "message", "Transaction group settled successfully"
            ));
        } catch (TransactionNotFoundException | TransactionGroupZeroingOutException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Releases (returns funds) for a transaction group after dispute.
     *
     * <p>POST /api/v1/ledger/groups/{referenceId}/release
     *
     * @param referenceId Transaction group UUID
     * @param reason      Reason for release
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/release")
    public ResponseEntity<Map<String, Object>> releaseTransactionGroup(
            @PathVariable UUID referenceId,
            @RequestParam @NotEmpty String reason) {

        try {
            transactionService.releaseTransactionGroup(referenceId, reason);

            return ResponseEntity.ok(Map.of(
                    "referenceId", referenceId,
                    "status", "RELEASED",
                    "reason", reason,
                    "message", "Transaction group released successfully"
            ));
        } catch (TransactionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancels a transaction group before conditions met.
     *
     * <p>POST /api/v1/ledger/groups/{referenceId}/cancel
     *
     * @param referenceId Transaction group UUID
     * @param reason      Reason for cancellation
     * @return Success response
     */
    @PostMapping("/groups/{referenceId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTransactionGroup(
            @PathVariable UUID referenceId,
            @RequestParam @NotEmpty String reason) {

        try {
            transactionService.cancelTransactionGroup(referenceId, reason);

            return ResponseEntity.ok(Map.of(
                    "referenceId", referenceId,
                    "status", "CANCELLED",
                    "reason", reason,
                    "message", "Transaction group cancelled successfully"
            ));
        } catch (TransactionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== High-Level Operations ====================

    /**
     * Transfers funds between two wallets.
     *
     * <p>POST /api/v1/ledger/transfer
     *
     * @param senderId    Sender wallet ID
     * @param recipientId Recipient wallet ID
     * @param amount      Amount to transfer
     * @return Transaction group UUID
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(
            @RequestParam @NotNull Integer senderId,
            @RequestParam @NotNull Integer recipientId,
            @RequestParam @Positive Long amount) {

        try {
            UUID referenceId = transactionService.transferBetweenTwoWallets(senderId, recipientId, amount);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "referenceId", referenceId,
                    "senderId", senderId,
                    "recipientId", recipientId,
                    "amount", amount,
                    "status", "SETTLED"
            ));
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Insufficient funds: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Transfer failed: " + e.getMessage()));
        }
    }

    // ==================== Query Operations ====================

    /**
     * Gets available balance for a wallet.
     *
     * <p>GET /api/v1/ledger/wallets/{walletId}/balance
     *
     * @param walletId Wallet ID
     * @return Available balance
     */
    @GetMapping("/wallets/{walletId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Integer walletId) {
        Long balance = walletBalanceService.getAvailableBalance(walletId);

        return ResponseEntity.ok(Map.of(
                "walletId", walletId,
                "availableBalance", balance
        ));
    }

    /**
     * Gets status of a transaction group.
     *
     * <p>GET /api/v1/ledger/groups/{referenceId}/status
     *
     * @param referenceId Transaction group UUID
     * @return Group status
     */
    @GetMapping("/groups/{referenceId}/status")
    public ResponseEntity<Map<String, Object>> getGroupStatus(@PathVariable UUID referenceId) {
        try {
            TransactionGroupStatus status = transactionService.getStatusForReferenceId(referenceId);

            return ResponseEntity.ok(Map.of(
                    "referenceId", referenceId,
                    "status", status.name()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gets all transactions for a transaction group.
     *
     * <p>GET /api/v1/ledger/groups/{referenceId}/transactions
     *
     * @param referenceId Transaction group UUID
     * @return List of transactions
     */
    @GetMapping("/groups/{referenceId}/transactions")
    public ResponseEntity<List<TransactionDTO>> getGroupTransactions(@PathVariable UUID referenceId) {
        List<TransactionDTO> transactions = transactionService.getTransactionsByReferenceId(referenceId);

        return ResponseEntity.ok(transactions);
    }
}
