package com.nosota.mwallet.service;

import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for wallet operations following banking ledger standards.
 *
 * <p>This service implements a two-phase transaction lifecycle:
 * <ul>
 *   <li>Phase 1 (HOLD): Block funds without transfer</li>
 *   <li>Phase 2 (Final): SETTLED, RELEASED, or CANCELLED</li>
 * </ul>
 *
 * <p>All transactions are immutable - new transactions are created for each state change.
 * <p>All finalization methods (settle/release/cancel) create offsetting transactions.
 */
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletBalanceService walletBalanceService;
    private final TransactionStatusStateMachine stateMachine;

    /**
     * Holds (blocks) a specified amount from the wallet for debit operation.
     *
     * <p>This is Phase 1 of a two-phase transaction. The amount is blocked from the sender's
     * available balance and moved to ESCROW. The wallet must have sufficient funds.
     *
     * <p><b>IMPORTANT:</b> Creates TWO transactions atomically (double-entry bookkeeping):
     * <ul>
     *   <li>Transaction 1: DEBIT from buyer wallet: -amount, DEBIT, HOLD</li>
     *   <li>Transaction 2: CREDIT to ESCROW wallet: +amount, CREDIT, HOLD</li>
     *   <li>Total: 0 (zero-sum principle maintained)</li>
     * </ul>
     *
     * <p>Example: Hold $100 from buyer for payment
     * → Creates: BUYER -100 (DEBIT, HOLD) + ESCROW +100 (CREDIT, HOLD) = 0 ✓
     *
     * @param walletId    The unique identifier of the wallet to debit from
     * @param amount      The amount to hold (must be positive, will be negated internally)
     * @param referenceId UUID grouping related transactions (typically matches transaction group ID)
     * @return The ID of the created DEBIT HOLD transaction from buyer wallet
     * @throws WalletNotFoundException    if the wallet does not exist
     * @throws InsufficientFundsException if the wallet has insufficient available balance
     */
    @Transactional
    public Integer holdDebit(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        // Verify wallet exists
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Check sufficient funds (pessimistic locking handled by balance service)
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in wallet %d: available=%d, required=%d",
                            walletId, availableBalance, amount));
        }

        // Get ESCROW wallet for holding funds
        Integer escrowWalletId = getOrCreateEscrowWallet();

        LocalDateTime now = LocalDateTime.now();

        // Transaction 1: DEBIT from buyer wallet (negative amount, money leaving)
        Transaction debitTransaction = new Transaction();
        debitTransaction.setWalletId(wallet.getId());
        debitTransaction.setAmount(-amount);
        debitTransaction.setType(TransactionType.DEBIT);
        debitTransaction.setStatus(TransactionStatus.HOLD);
        debitTransaction.setReferenceId(referenceId);
        debitTransaction.setHoldReserveTimestamp(now);
        debitTransaction.setCurrency(wallet.getCurrency());

        Transaction savedDebitTransaction = transactionRepository.save(debitTransaction);

        // Transaction 2: CREDIT to ESCROW wallet (positive amount, money arriving)
        Transaction creditTransaction = new Transaction();
        creditTransaction.setWalletId(escrowWalletId);
        creditTransaction.setAmount(amount);
        creditTransaction.setType(TransactionType.CREDIT);
        creditTransaction.setStatus(TransactionStatus.HOLD);
        creditTransaction.setReferenceId(referenceId);
        creditTransaction.setHoldReserveTimestamp(now);
        creditTransaction.setCurrency(wallet.getCurrency());

        transactionRepository.save(creditTransaction);

        log.info("Held debit (double-entry): buyerWalletId={}, escrowWalletId={}, amount={}, currency={}, referenceId={}, debitTxId={}",
                walletId, escrowWalletId, amount, wallet.getCurrency(), referenceId, savedDebitTransaction.getId());

        return savedDebitTransaction.getId();
    }

    /**
     * Holds (prepares) a specified amount for the wallet for credit operation.
     *
     * <p>This is Phase 1 of a two-phase transaction. The amount is moved from ESCROW
     * to the recipient wallet. No balance check is needed (funds already in ESCROW).
     *
     * <p><b>IMPORTANT:</b> Creates TWO transactions atomically (double-entry bookkeeping):
     * <ul>
     *   <li>Transaction 1: DEBIT from ESCROW wallet: -amount, DEBIT, HOLD</li>
     *   <li>Transaction 2: CREDIT to recipient wallet: +amount, CREDIT, HOLD</li>
     *   <li>Total: 0 (zero-sum principle maintained)</li>
     * </ul>
     *
     * <p>Example: Hold $100 for recipient (funds from ESCROW)
     * → Creates: ESCROW -100 (DEBIT, HOLD) + RECIPIENT +100 (CREDIT, HOLD) = 0 ✓
     *
     * @param walletId    The unique identifier of the wallet to credit to
     * @param amount      The amount to hold (must be positive)
     * @param referenceId UUID grouping related transactions (typically matches transaction group ID)
     * @return The ID of the created CREDIT HOLD transaction to recipient wallet
     * @throws WalletNotFoundException if the wallet does not exist
     */
    @Transactional
    public Integer holdCredit(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId)
            throws WalletNotFoundException {

        // Verify wallet exists
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Get ESCROW wallet (funds source)
        Integer escrowWalletId = getOrCreateEscrowWallet();

        LocalDateTime now = LocalDateTime.now();

        // Transaction 1: DEBIT from ESCROW wallet (negative amount, money leaving)
        Transaction debitTransaction = new Transaction();
        debitTransaction.setWalletId(escrowWalletId);
        debitTransaction.setAmount(-amount);
        debitTransaction.setType(TransactionType.DEBIT);
        debitTransaction.setStatus(TransactionStatus.HOLD);
        debitTransaction.setReferenceId(referenceId);
        debitTransaction.setHoldReserveTimestamp(now);
        debitTransaction.setCurrency(wallet.getCurrency());

        transactionRepository.save(debitTransaction);

        // Transaction 2: CREDIT to recipient wallet (positive amount, money arriving)
        Transaction creditTransaction = new Transaction();
        creditTransaction.setWalletId(walletId);
        creditTransaction.setAmount(amount);
        creditTransaction.setType(TransactionType.CREDIT);
        creditTransaction.setStatus(TransactionStatus.HOLD);
        creditTransaction.setReferenceId(referenceId);
        creditTransaction.setHoldReserveTimestamp(now);
        creditTransaction.setCurrency(wallet.getCurrency());

        Transaction savedCreditTransaction = transactionRepository.save(creditTransaction);

        log.info("Held credit (double-entry): escrowWalletId={}, recipientWalletId={}, amount={}, currency={}, referenceId={}, creditTxId={}",
                escrowWalletId, walletId, amount, wallet.getCurrency(), referenceId, savedCreditTransaction.getId());

        return savedCreditTransaction.getId();
    }

    /**
     * Settles (finalizes) a previously held transaction in favor of the recipient.
     *
     * <p>This is Phase 2 (success path) of a two-phase transaction. The blocked funds are
     * transferred to the recipient. For debit transactions, funds are removed from sender's
     * available balance. For credit transactions, funds are added to recipient's available balance.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: same as HOLD transaction</li>
     *   <li>Type: same as HOLD transaction</li>
     *   <li>Status: SETTLED (final state)</li>
     * </ul>
     *
     * <p>Example: Settle held $100 debit
     * → Finds: -100, DEBIT, HOLD
     * → Creates: -100, DEBIT, SETTLED
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to settle
     * @return The ID of the created SETTLED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer settle(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find ALL HOLD transactions for this wallet and reference ID
        List<Transaction> holdTransactions = transactionRepository
                .findAllByWalletIdAndReferenceIdAndStatus(walletId, referenceId, TransactionStatus.HOLD);

        if (holdTransactions.isEmpty()) {
            throw new TransactionNotFoundException(
                    String.format("HOLD transaction not found for wallet %d and reference %s",
                            walletId, referenceId));
        }

        // Validate state transition: HOLD → SETTLED
        stateMachine.validateTransition(TransactionStatus.HOLD, TransactionStatus.SETTLED);

        // Create SETTLED transaction for each HOLD transaction
        Integer lastTransactionId = null;
        for (Transaction holdTransaction : holdTransactions) {
            Transaction settleTransaction = new Transaction();
            settleTransaction.setWalletId(holdTransaction.getWalletId());
            settleTransaction.setAmount(holdTransaction.getAmount());
            settleTransaction.setType(holdTransaction.getType());
            settleTransaction.setStatus(TransactionStatus.SETTLED);
            settleTransaction.setReferenceId(referenceId);
            settleTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
            settleTransaction.setCurrency(holdTransaction.getCurrency());

            Transaction savedTransaction = transactionRepository.save(settleTransaction);
            lastTransactionId = savedTransaction.getId();

            log.info("Settled transaction: walletId={}, amount={}, type={}, currency={}, referenceId={}, transactionId={}",
                    walletId, holdTransaction.getAmount(), holdTransaction.getType(),
                    holdTransaction.getCurrency(), referenceId, savedTransaction.getId());
        }

        return lastTransactionId;
    }

    /**
     * Releases (returns) previously held funds back to sender after dispute resolution.
     *
     * <p>This is Phase 2 (dispute/return path) of a two-phase transaction. The blocked funds
     * are returned to their original account by creating an offsetting transaction in the
     * OPPOSITE direction.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: OPPOSITE sign of HOLD transaction</li>
     *   <li>Type: OPPOSITE type of HOLD transaction (DEBIT ↔ CREDIT)</li>
     *   <li>Status: RELEASED (final state)</li>
     * </ul>
     *
     * <p>Example: Release held $100 debit (return funds to sender)
     * → Finds: -100, DEBIT, HOLD
     * → Creates: +100, CREDIT, RELEASED (returns money)
     *
     * <p>Use case: Payment was held, conditions were met, but after investigation/dispute
     * it was decided to return the funds to the sender.
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to release
     * @return The ID of the created RELEASED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer release(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find the HOLD transaction
        Transaction holdTransaction = transactionRepository
                .findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD)
                .orElseThrow(() -> new TransactionNotFoundException(
                        String.format("HOLD transaction not found for wallet %d and reference %s",
                                walletId, referenceId)));

        // Validate state transition: HOLD → RELEASED
        stateMachine.validateTransition(TransactionStatus.HOLD, TransactionStatus.RELEASED);

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
        releaseTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
        releaseTransaction.setCurrency(holdTransaction.getCurrency());

        Transaction savedTransaction = transactionRepository.save(releaseTransaction);

        log.info("Released transaction: walletId={}, originalAmount={}, releaseAmount={}, originalType={}, releaseType={}, currency={}, referenceId={}, transactionId={}",
                walletId, holdTransaction.getAmount(), releaseTransaction.getAmount(),
                holdTransaction.getType(), releaseTransaction.getType(),
                holdTransaction.getCurrency(), referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Cancels a previously held transaction before execution conditions were met.
     *
     * <p>This is Phase 2 (cancellation path) of a two-phase transaction. The blocked funds
     * are returned to their original account by creating an offsetting transaction in the
     * OPPOSITE direction.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: OPPOSITE sign of HOLD transaction</li>
     *   <li>Type: OPPOSITE type of HOLD transaction (DEBIT ↔ CREDIT)</li>
     *   <li>Status: CANCELLED (final state)</li>
     * </ul>
     *
     * <p>Example: Cancel held $100 debit (return funds to sender)
     * → Finds: -100, DEBIT, HOLD
     * → Creates: +100, CREDIT, CANCELLED (returns money)
     *
     * <p>Use case: Payment was held, but conditions were never met (e.g., timeout, user cancelled,
     * validation failed), so the operation is cancelled before settlement.
     *
     * <p>Difference from release(): Cancel is used when conditions were never met,
     * release is used when conditions were met but funds are returned after dispute/investigation.
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to cancel
     * @return The ID of the created CANCELLED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer cancel(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find the HOLD transaction
        Transaction holdTransaction = transactionRepository
                .findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD)
                .orElseThrow(() -> new TransactionNotFoundException(
                        String.format("HOLD transaction not found for wallet %d and reference %s",
                                walletId, referenceId)));

        // Validate state transition: HOLD → CANCELLED
        stateMachine.validateTransition(TransactionStatus.HOLD, TransactionStatus.CANCELLED);

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
        cancelTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
        cancelTransaction.setCurrency(holdTransaction.getCurrency());

        Transaction savedTransaction = transactionRepository.save(cancelTransaction);

        log.info("Cancelled transaction: walletId={}, originalAmount={}, cancelAmount={}, originalType={}, cancelType={}, currency={}, referenceId={}, transactionId={}",
                walletId, holdTransaction.getAmount(), cancelTransaction.getAmount(),
                holdTransaction.getType(), cancelTransaction.getType(),
                holdTransaction.getCurrency(), referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Processes a refund by transferring funds from merchant wallet to buyer wallet.
     *
     * <p>This operation is different from RELEASE/CANCEL which return funds from ESCROW.
     * Refund happens AFTER settlement when merchant already received the funds.
     *
     * <p>Creates two transactions atomically:
     * <ul>
     *   <li>DEBIT from merchant wallet: -amount, DEBIT, REFUNDED</li>
     *   <li>CREDIT to buyer wallet: +amount, CREDIT, REFUNDED</li>
     * </ul>
     *
     * <p>Example: Refund $100 from merchant to buyer
     * → Creates: -100, DEBIT, REFUNDED (on merchant wallet)
     * → Creates: +100, CREDIT, REFUNDED (on buyer wallet)
     *
     * <p>Both transactions share the same referenceId (refund transaction group ID).
     * The merchant wallet must have sufficient available balance.
     *
     * @param merchantWalletId The wallet ID of the merchant (funds source)
     * @param buyerWalletId    The wallet ID of the buyer (funds destination)
     * @param amount           The amount to refund (must be positive)
     * @param referenceId      UUID for refund transaction group (links debit and credit)
     * @return The referenceId of the created refund transactions
     * @throws WalletNotFoundException    if either wallet does not exist
     * @throws InsufficientFundsException if merchant wallet has insufficient available balance
     */
    @Transactional
    public UUID refund(@NotNull Integer merchantWalletId,
                       @NotNull Integer buyerWalletId,
                       @Positive Long amount,
                       @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        // Verify both wallets exist
        Wallet merchantWallet = walletRepository.findById(merchantWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Merchant wallet with ID " + merchantWalletId + " not found"));

        Wallet buyerWallet = walletRepository.findById(buyerWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Buyer wallet with ID " + buyerWalletId + " not found"));

        // Validate both wallets have the same currency (cross-currency refunds forbidden)
        if (!merchantWallet.getCurrency().equals(buyerWallet.getCurrency())) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: merchant wallet has %s, buyer wallet has %s. Cross-currency refunds are forbidden.",
                            merchantWallet.getCurrency(), buyerWallet.getCurrency()));
        }

        // Check merchant has sufficient funds
        Long availableBalance = walletBalanceService.getAvailableBalance(merchantWalletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in merchant wallet %d for refund: available=%d, required=%d",
                            merchantWalletId, availableBalance, amount));
        }

        LocalDateTime now = LocalDateTime.now();

        // Create DEBIT transaction on merchant wallet (negative amount)
        Transaction merchantDebit = new Transaction();
        merchantDebit.setWalletId(merchantWalletId);
        merchantDebit.setAmount(-amount);
        merchantDebit.setType(TransactionType.DEBIT);
        merchantDebit.setStatus(TransactionStatus.REFUNDED);
        merchantDebit.setReferenceId(referenceId);
        merchantDebit.setConfirmRejectTimestamp(now);
        merchantDebit.setCurrency(merchantWallet.getCurrency());

        Transaction savedMerchantDebit = transactionRepository.save(merchantDebit);

        // Create CREDIT transaction on buyer wallet (positive amount)
        Transaction buyerCredit = new Transaction();
        buyerCredit.setWalletId(buyerWalletId);
        buyerCredit.setAmount(amount);
        buyerCredit.setType(TransactionType.CREDIT);
        buyerCredit.setStatus(TransactionStatus.REFUNDED);
        buyerCredit.setReferenceId(referenceId);
        buyerCredit.setConfirmRejectTimestamp(now);
        buyerCredit.setCurrency(buyerWallet.getCurrency());

        Transaction savedBuyerCredit = transactionRepository.save(buyerCredit);

        log.info("Refund completed: merchantWalletId={}, buyerWalletId={}, amount={}, currency={}, referenceId={}, merchantTxId={}, buyerTxId={}",
                merchantWalletId, buyerWalletId, amount, merchantWallet.getCurrency(), referenceId,
                savedMerchantDebit.getId(), savedBuyerCredit.getId());

        return referenceId;
    }

    /**
     * Gets or creates the ESCROW wallet (on-demand creation).
     *
     * <p>The ESCROW wallet is a temporary holding account for funds during two-phase transactions.
     * When funds are held (Phase 1), they move from sender to ESCROW.
     * When settled (Phase 2), they move from ESCROW to recipient.
     *
     * <p>This helper method avoids circular dependency with WalletManagementService.
     *
     * @return The ID of the ESCROW wallet
     */
    private Integer getOrCreateEscrowWallet() {
        return walletRepository.findByTypeAndDescription(com.nosota.mwallet.model.WalletType.ESCROW, "ESCROW")
                .map(Wallet::getId)
                .orElseGet(() -> {
                    log.info("Creating ESCROW wallet");
                    Wallet escrowWallet = new Wallet();
                    escrowWallet.setType(com.nosota.mwallet.model.WalletType.ESCROW);
                    escrowWallet.setDescription("ESCROW");
                    escrowWallet.setOwnerId(null);
                    escrowWallet.setOwnerType(com.nosota.mwallet.model.OwnerType.SYSTEM_OWNER);
                    return walletRepository.save(escrowWallet).getId();
                });
    }
}
