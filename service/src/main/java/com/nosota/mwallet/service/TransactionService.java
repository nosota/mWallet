package com.nosota.mwallet.service;

import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.mapper.TransactionMapper;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionGroupZeroingOutException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing transaction groups following banking ledger standards.
 *
 * <p>This service provides operations for:
 * <ul>
 *   <li>Creating transaction groups (IN_PROGRESS state)</li>
 *   <li>Finalizing groups via settle/release/cancel</li>
 *   <li>High-level operations like wallet-to-wallet transfers</li>
 * </ul>
 *
 * <p>All finalization methods enforce zero-sum reconciliation before proceeding.
 */
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class TransactionService {

    private final WalletService walletService;
    private final TransactionGroupRepository transactionGroupRepository;
    private final TransactionRepository transactionRepository;
    private final com.nosota.mwallet.repository.WalletRepository walletRepository;

    /**
     * Creates a new transaction group in IN_PROGRESS state.
     *
     * <p>A transaction group is used to batch multiple related transactions together
     * (typically one debit and one credit) and ensure they are finalized atomically.
     *
     * <p>Example: For a $100 transfer from wallet A to wallet B:
     * <ol>
     *   <li>Create transaction group (returns UUID)</li>
     *   <li>Hold debit from wallet A with this UUID</li>
     *   <li>Hold credit to wallet B with this UUID</li>
     *   <li>Settle the group (both transactions finalized together)</li>
     * </ol>
     *
     * <p><b>Idempotency:</b> If an idempotencyKey is provided and a transaction group
     * with the same key already exists, this method returns the existing group's UUID
     * instead of creating a new one. This prevents duplicate groups from being created
     * in case of request retries (network timeouts, client retries, etc.).
     *
     * @param idempotencyKey Optional idempotency key for duplicate detection.
     *                       If null, a new group is always created.
     * @return UUID of the created (or existing) transaction group
     */
    @Transactional
    public UUID createTransactionGroup(String idempotencyKey) {
        // Check for existing transaction group with this idempotency key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<TransactionGroup> existing = transactionGroupRepository
                    .findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
                UUID existingId = existing.get().getId();
                log.info("Returning existing transaction group for idempotency key: referenceId={}, key={}",
                        existingId, idempotencyKey);
                return existingId;
            }
        }

        // Create new transaction group
        TransactionGroup transactionGroup = new TransactionGroup();
        transactionGroup.setStatus(TransactionGroupStatus.IN_PROGRESS);
        transactionGroup.setIdempotencyKey(idempotencyKey);
        transactionGroup = transactionGroupRepository.save(transactionGroup);

        UUID referenceId = transactionGroup.getId();
        log.info("Created transaction group: referenceId={}, idempotencyKey={}",
                referenceId, idempotencyKey);
        return referenceId;
    }

    /**
     * Creates a new transaction group without idempotency key (backward compatibility).
     * Delegates to {@link #createTransactionGroup(String)} with null idempotency key.
     *
     * @return UUID of the created transaction group
     */
    @Transactional
    public UUID createTransactionGroup() {
        return createTransactionGroup(null);
    }

    /**
     * Settles (finalizes) a transaction group successfully.
     *
     * <p>This method:
     * <ol>
     *   <li>Verifies the group exists and is IN_PROGRESS</li>
     *   <li>Checks zero-sum reconciliation (all HOLD transactions must sum to 0)</li>
     *   <li>Settles all transactions in the group</li>
     *   <li>Updates group status to SETTLED</li>
     * </ol>
     *
     * <p>After settlement, all transactions have SETTLED status and funds are transferred.
     *
     * @param referenceId The UUID of the transaction group to settle
     * @throws EntityNotFoundException If no group exists with this reference ID
     * @throws TransactionGroupZeroingOutException If transactions don't sum to zero
     * @throws TransactionNotFoundException If any HOLD transaction is missing
     */
    @Transactional
    public void settleTransactionGroup(@NotNull UUID referenceId)
            throws TransactionNotFoundException, TransactionGroupZeroingOutException {

        // Fetch transaction group
        TransactionGroup transactionGroup = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No transaction group found with referenceId: " + referenceId));

        // Verify zero-sum reconciliation (double-entry accounting)
        Long reconciliationAmount = transactionRepository.getReconciliationAmountByGroupId(referenceId);
        if (reconciliationAmount != 0) {
            throw new TransactionGroupZeroingOutException(
                    String.format("Transaction group %s is not reconciled (sum=%d, expected=0)",
                            referenceId, reconciliationAmount));
        }

        // Settle all transactions in the group - process each unique wallet once
        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        Set<Integer> processedWallets = new HashSet<>();
        for (Transaction transaction : transactions) {
            if (!processedWallets.contains(transaction.getWalletId())) {
                walletService.settle(transaction.getWalletId(), referenceId);
                processedWallets.add(transaction.getWalletId());
            }
        }

        // Update group status to SETTLED
        transactionGroup.setStatus(TransactionGroupStatus.SETTLED);
        transactionGroupRepository.save(transactionGroup);

        log.info("Settled transaction group: referenceId={}, transactionCount={}", referenceId, transactions.size());
    }

    /**
     * Releases (returns) all funds in a transaction group after dispute resolution.
     *
     * <p>This method:
     * <ol>
     *   <li>Verifies the group exists and is IN_PROGRESS</li>
     *   <li>Releases all transactions in the group (creates opposite direction transactions)</li>
     *   <li>Updates group status to RELEASED</li>
     * </ol>
     *
     * <p>Use this when conditions were met but after investigation/dispute the funds
     * should be returned to original accounts.
     *
     * <p>Example: Payment was held, shipping conditions met, but product was defective
     * so after investigation funds are released back to buyer.
     *
     * @param referenceId The UUID of the transaction group to release
     * @param reason      The reason for releasing (for audit trail)
     * @throws EntityNotFoundException If no group exists with this reference ID
     * @throws TransactionNotFoundException If any HOLD transaction is missing
     */
    @Transactional
    public void releaseTransactionGroup(@NotNull UUID referenceId, @NotEmpty String reason)
            throws TransactionNotFoundException {

        // Fetch transaction group
        TransactionGroup transactionGroup = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No transaction group found with referenceId: " + referenceId));

        // Release all HOLD transactions in the group (create offsetting RELEASED transactions)
        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        List<Transaction> holdTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        for (Transaction holdTransaction : holdTransactions) {
            // Create RELEASED transaction with OPPOSITE amount and type
            TransactionType oppositeType = holdTransaction.getType() == TransactionType.DEBIT
                    ? TransactionType.CREDIT
                    : TransactionType.DEBIT;

            Transaction releaseTransaction = new Transaction();
            releaseTransaction.setWalletId(holdTransaction.getWalletId());
            releaseTransaction.setAmount(-holdTransaction.getAmount()); // Flip sign
            releaseTransaction.setType(oppositeType); // Flip type
            releaseTransaction.setStatus(TransactionStatus.RELEASED);
            releaseTransaction.setReferenceId(referenceId);
            releaseTransaction.setConfirmRejectTimestamp(now);
            releaseTransaction.setCurrency(holdTransaction.getCurrency());

            transactionRepository.save(releaseTransaction);

            log.debug("Released transaction: walletId={}, originalAmount={}, releaseAmount={}, originalType={}, releaseType={}",
                    holdTransaction.getWalletId(), holdTransaction.getAmount(), releaseTransaction.getAmount(),
                    holdTransaction.getType(), releaseTransaction.getType());
        }

        // Update group status to RELEASED
        transactionGroup.setStatus(TransactionGroupStatus.RELEASED);
        transactionGroup.setReason(reason);
        transactionGroupRepository.save(transactionGroup);

        log.info("Released transaction group: referenceId={}, reason={}, transactionCount={}",
                referenceId, reason, transactions.size());
    }

    /**
     * Cancels a transaction group before execution conditions were met.
     *
     * <p>This method:
     * <ol>
     *   <li>Verifies the group exists and is IN_PROGRESS</li>
     *   <li>Cancels all transactions in the group (creates opposite direction transactions)</li>
     *   <li>Updates group status to CANCELLED</li>
     * </ol>
     *
     * <p>Use this when conditions were never met (timeout, user cancelled, validation failed).
     *
     * <p>Example: Payment was held waiting for shipping, but shipping failed validation
     * so the transaction is cancelled before settlement.
     *
     * <p>Difference from release: Cancel = conditions never met, Release = conditions met but reversed after investigation.
     *
     * @param referenceId The UUID of the transaction group to cancel
     * @param reason      The reason for cancellation (for audit trail)
     * @throws EntityNotFoundException If no group exists with this reference ID
     * @throws TransactionNotFoundException If any HOLD transaction is missing
     */
    @Transactional
    public void cancelTransactionGroup(@NotNull UUID referenceId, @NotEmpty String reason)
            throws TransactionNotFoundException {

        // Fetch transaction group
        TransactionGroup transactionGroup = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No transaction group found with referenceId: " + referenceId));

        // Cancel all HOLD transactions in the group (create offsetting CANCELLED transactions)
        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        List<Transaction> holdTransactions = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.HOLD)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        for (Transaction holdTransaction : holdTransactions) {
            // Create CANCELLED transaction with OPPOSITE amount and type
            TransactionType oppositeType = holdTransaction.getType() == TransactionType.DEBIT
                    ? TransactionType.CREDIT
                    : TransactionType.DEBIT;

            Transaction cancelTransaction = new Transaction();
            cancelTransaction.setWalletId(holdTransaction.getWalletId());
            cancelTransaction.setAmount(-holdTransaction.getAmount()); // Flip sign
            cancelTransaction.setType(oppositeType); // Flip type
            cancelTransaction.setStatus(TransactionStatus.CANCELLED);
            cancelTransaction.setReferenceId(referenceId);
            cancelTransaction.setConfirmRejectTimestamp(now);
            cancelTransaction.setCurrency(holdTransaction.getCurrency());

            transactionRepository.save(cancelTransaction);

            log.debug("Cancelled transaction: walletId={}, originalAmount={}, cancelAmount={}, originalType={}, cancelType={}",
                    holdTransaction.getWalletId(), holdTransaction.getAmount(), cancelTransaction.getAmount(),
                    holdTransaction.getType(), cancelTransaction.getType());
        }

        // Update group status to CANCELLED
        transactionGroup.setStatus(TransactionGroupStatus.CANCELLED);
        transactionGroup.setReason(reason);
        transactionGroupRepository.save(transactionGroup);

        log.info("Cancelled transaction group: referenceId={}, reason={}, transactionCount={}",
                referenceId, reason, transactions.size());
    }

    /**
     * Facilitates a transfer of funds between two wallets (high-level operation).
     *
     * <p>This method demonstrates a complete ledger-compliant transfer workflow:
     * <ol>
     *   <li>Create transaction group (IN_PROGRESS)</li>
     *   <li>Hold debit from sender (blocks funds)</li>
     *   <li>Hold credit to recipient (prepares funds)</li>
     *   <li>Settle group (finalizes transfer) OR Cancel on error</li>
     * </ol>
     *
     * <p>The method follows banking standards:
     * <ul>
     *   <li>Two-phase commit (hold → settle)</li>
     *   <li>Zero-sum reconciliation (debit + credit = 0)</li>
     *   <li>Atomic finalization (both succeed or both fail)</li>
     *   <li>Automatic rollback on error</li>
     * </ul>
     *
     * @param senderId    The wallet ID to debit from
     * @param recipientId The wallet ID to credit to
     * @param amount      The amount to transfer (must be positive)
     * @return The UUID of the transaction group (for tracking)
     * @throws InsufficientFundsException If sender has insufficient funds
     * @throws Exception                  If any error occurs (group will be cancelled automatically)
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public UUID transferBetweenTwoWallets(@NotNull Integer senderId, @NotNull Integer recipientId,
                                          @Positive Long amount) throws Exception {
        // 1. Create transaction group (IN_PROGRESS)
        UUID referenceId = createTransactionGroup();

        try {
            // 2. Hold debit from sender (blocks amount from available balance)
            walletService.holdDebit(senderId, amount, referenceId);

            // 3. Hold credit to recipient (prepares amount for addition)
            walletService.holdCredit(recipientId, amount, referenceId);

            // 4. Settle the transaction group (finalizes the transfer)
            settleTransactionGroup(referenceId);

            log.info("Transfer completed: from={}, to={}, amount={}, referenceId={}",
                    senderId, recipientId, amount, referenceId);

        } catch (Exception e) {
            // 5. Cancel the transaction group on any error (returns funds)
            log.error("Transfer failed: from={}, to={}, amount={}, referenceId={}, error={}",
                    senderId, recipientId, amount, referenceId, e.getMessage());

            cancelTransactionGroup(referenceId, e.getMessage());
            throw e;  // Propagate exception to caller
        }

        return referenceId;
    }

    /**
     * Direct transfer: creates SETTLED transactions immediately without HOLD phase.
     *
     * <p>This method bypasses the two-phase commit (HOLD → SETTLE) and creates
     * SETTLED transactions directly. Use for:
     * <ul>
     *   <li>Deposit (external funds entering system)</li>
     *   <li>Withdrawal (funds leaving system)</li>
     *   <li>Simple transfers without dispute risk</li>
     * </ul>
     *
     * <p>Creates 2 transactions:
     * <ul>
     *   <li>DEBIT from sender: -amount, DEBIT, SETTLED</li>
     *   <li>CREDIT to recipient: +amount, CREDIT, SETTLED</li>
     * </ul>
     *
     * <p>Zero-sum is guaranteed: sum of all transactions = 0
     *
     * @param fromWalletId Sender wallet ID
     * @param toWalletId   Recipient wallet ID
     * @param amount       Amount to transfer (in cents/minor units, must be positive)
     * @return Transaction group UUID (referenceId)
     * @throws EntityNotFoundException  If either wallet not found
     * @throws IllegalArgumentException If wallets have different currencies
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UUID directTransfer(@NotNull Integer fromWalletId, @NotNull Integer toWalletId,
                                @Positive Long amount) {
        // 1. Verify wallets exist and get currency
        Wallet fromWallet = walletRepository.findById(fromWalletId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "From wallet not found: " + fromWalletId));

        Wallet toWallet = walletRepository.findById(toWalletId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "To wallet not found: " + toWalletId));

        // 2. Validate currency match
        if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: %s → %s",
                            fromWallet.getCurrency(), toWallet.getCurrency()));
        }

        String currency = fromWallet.getCurrency();

        // 3. Create transaction group (SETTLED)
        UUID referenceId = createTransactionGroup();

        // 4. Create SETTLED transactions
        createTransaction(fromWalletId, -amount, TransactionType.DEBIT,
                TransactionStatus.SETTLED, referenceId, currency);

        createTransaction(toWalletId, amount, TransactionType.CREDIT,
                TransactionStatus.SETTLED, referenceId, currency);

        // 5. Update group status to SETTLED
        TransactionGroup group = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction group not found: " + referenceId));
        group.setStatus(TransactionGroupStatus.SETTLED);
        transactionGroupRepository.save(group);

        log.info("Direct transfer completed: from={}, to={}, amount={}, currency={}, referenceId={}",
                fromWalletId, toWalletId, amount, currency, referenceId);

        return referenceId;
    }

    /**
     * Retrieves the current status of a transaction group.
     *
     * <p>Possible statuses:
     * <ul>
     *   <li>IN_PROGRESS: Group is active, transactions are held</li>
     *   <li>SETTLED: Group finalized successfully</li>
     *   <li>RELEASED: Group reversed after dispute</li>
     *   <li>CANCELLED: Group cancelled before completion</li>
     * </ul>
     *
     * @param referenceId The UUID of the transaction group
     * @return The current status of the group
     * @throws EntityNotFoundException If no group exists with this reference ID
     */
    public TransactionGroupStatus getStatusForReferenceId(@NotNull UUID referenceId) {
        return transactionGroupRepository.findById(referenceId)
                .map(TransactionGroup::getStatus)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No transaction group found with referenceId: " + referenceId));
    }

    /**
     * Retrieves all transactions associated with a transaction group.
     *
     * <p>Returns all transaction records (both HOLD and final status) that belong
     * to the specified group. Useful for audit trails and transaction history.
     *
     * @param referenceId The UUID of the transaction group
     * @return List of transaction DTOs (empty list if no transactions found)
     * @throws DataAccessException If database error occurs
     */
    public List<TransactionDTO> getTransactionsByReferenceId(@NotNull UUID referenceId) {
        List<Transaction> transactions = transactionRepository.findByReferenceId(referenceId);
        return TransactionMapper.INSTANCE.toDTOList(transactions);
    }

    /**
     * Gets reconciliation statistics for zero-sum validation.
     *
     * <p>According to double-entry accounting, the sum of all transactions
     * in the system must always equal 0. This method provides statistics
     * to verify system integrity.
     *
     * @return Map with reconciliation statistics by status
     */
    public java.util.Map<String, Long> getReconciliation() {
        Long totalSum = transactionRepository.getTotalSum();
        Long settledSum = transactionRepository.getSumByStatus(TransactionStatus.SETTLED);
        Long holdSum = transactionRepository.getSumByStatus(TransactionStatus.HOLD);
        Long releasedSum = transactionRepository.getSumByStatus(TransactionStatus.RELEASED);
        Long cancelledSum = transactionRepository.getSumByStatus(TransactionStatus.CANCELLED);
        Long refundedSum = transactionRepository.getSumByStatus(TransactionStatus.REFUNDED);

        java.util.Map<String, Long> reconciliation = new java.util.HashMap<>();
        reconciliation.put("totalSum", totalSum);
        reconciliation.put("settledSum", settledSum);
        reconciliation.put("holdSum", holdSum);
        reconciliation.put("releasedSum", releasedSum);
        reconciliation.put("cancelledSum", cancelledSum);
        reconciliation.put("refundedSum", refundedSum);

        log.info("Reconciliation check: total={}, settled={}, hold={}, released={}, cancelled={}, refunded={}",
                totalSum, settledSum, holdSum, releasedSum, cancelledSum, refundedSum);

        return reconciliation;
    }

    /**
     * Helper method to create a transaction.
     *
     * @param walletId    Wallet ID
     * @param amount      Amount (positive for CREDIT, negative for DEBIT)
     * @param type        Transaction type (DEBIT/CREDIT)
     * @param status      Transaction status (HOLD/SETTLED/CANCELLED/RELEASED)
     * @param referenceId Reference ID (transaction group UUID)
     * @param currency    Currency code
     * @return Saved transaction entity
     */
    private Transaction createTransaction(
            Integer walletId,
            Long amount,
            TransactionType type,
            TransactionStatus status,
            UUID referenceId,
            String currency
    ) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setStatus(status);
        tx.setReferenceId(referenceId);
        tx.setCurrency(currency);

        LocalDateTime now = LocalDateTime.now();
        if (status == TransactionStatus.HOLD) {
            tx.setHoldReserveTimestamp(now);
        } else {
            // SETTLED, CANCELLED, RELEASED
            tx.setConfirmRejectTimestamp(now);
        }

        return transactionRepository.save(tx);
    }
}
