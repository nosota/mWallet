package com.nosota.mwallet.service;

import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.mapper.TransactionMapper;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionGroupZeroingOutException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
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

        // Release all transactions in the group (opposite direction)
        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        for (Transaction transaction : transactions) {
            walletService.release(transaction.getWalletId(), referenceId);
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

        // Cancel all transactions in the group (opposite direction)
        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        for (Transaction transaction : transactions) {
            walletService.cancel(transaction.getWalletId(), referenceId);
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
}
